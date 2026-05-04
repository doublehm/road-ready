"""
Road Condition Intelligence Service

Ingests classified road condition events from device trips, aggregates them
per H3 cell (resolution 10, ~15m segments), and confirms hazard segments once
≥ 3 independent devices report them with speed-weighted confidence.

Confirmed segments are cached in memory (TTL 1 hour) for fast nearby queries.
"""

import asyncio
import logging
import time
from typing import Any, Dict, List, Optional

import h3
from app.database import nosql_db

logger = logging.getLogger(__name__)

# ── Constants ────────────────────────────────────────────────────────────────────

H3_RESOLUTION = 10          # ~15 m hexagonal segments
MIN_DEVICE_CONFIRMATIONS = 3
CACHE_TTL_SECONDS = 3600    # 1-hour in-memory cache for confirmed segments
# Low-speed observations are more reliable (vehicle not bouncing from suspension)
LOW_SPEED_THRESHOLD_KMH = 30.0
HIGH_SPEED_PENALTY = 0.6    # Weight multiplier for high-speed observations

LABEL_PRIORITY = {          # Higher = worse condition, used for segment label voting
    "smooth": 0,
    "rough": 1,
    "bump": 2,
    "speed_bump": 3,
    "pothole": 4,
}


class _SegmentCache:
    """Thread-safe TTL cache for confirmed road segments."""

    def __init__(self, ttl: int = CACHE_TTL_SECONDS):
        self._ttl = ttl
        self._store: Dict[str, tuple[float, Any]] = {}  # key → (expires_at, value)
        self._lock = asyncio.Lock()

    async def get(self, key: str) -> Optional[Any]:
        async with self._lock:
            entry = self._store.get(key)
            if entry and entry[0] > time.monotonic():
                return entry[1]
            if entry:
                del self._store[key]
            return None

    async def set(self, key: str, value: Any) -> None:
        async with self._lock:
            self._store[key] = (time.monotonic() + self._ttl, value)

    async def invalidate(self, key: str) -> None:
        async with self._lock:
            self._store.pop(key, None)


_segment_cache = _SegmentCache()


# ── Index bootstrap ──────────────────────────────────────────────────────────────

async def ensure_indexes() -> None:
    """Create required MongoDB indexes once at startup."""
    try:
        events_col = nosql_db.road_condition_events
        await events_col.create_index([("location", "2dsphere")])
        await events_col.create_index("h3_index")
        await events_col.create_index("device_id")

        segments_col = nosql_db.road_condition_segments
        await segments_col.create_index([("location", "2dsphere")])
        await segments_col.create_index("h3_index", unique=True)
        await segments_col.create_index("confirmed")

        logger.info("[road_conditions] MongoDB indexes ensured")
    except Exception as exc:
        logger.warning(f"[road_conditions] Could not create indexes: {exc}")


# ── Ingest ───────────────────────────────────────────────────────────────────────

async def ingest_trip_report(
    device_id: str,
    ride_id: str,
    events: List[Dict[str, Any]],
) -> int:
    """
    Store a batch of classified events from a completed trip.

    Returns the number of events persisted.
    """
    if not events:
        return 0

    h3_cells_touched: set[str] = set()
    docs = []
    for ev in events:
        lat = float(ev["lat"])
        lon = float(ev["lon"])
        cell = h3.latlng_to_cell(lat, lon, H3_RESOLUTION)
        h3_cells_touched.add(cell)
        docs.append({
            "device_id": device_id,
            "ride_id": ride_id,
            "timestamp": int(ev.get("timestamp", time.time() * 1000)),
            "location": {"type": "Point", "coordinates": [lon, lat]},
            "speed_kmh": float(ev.get("speed_kmh", 0.0)),
            "label": str(ev.get("label", "smooth")),
            "confidence": float(ev.get("confidence", 0.5)),
            "h3_index": cell,
        })

    try:
        await nosql_db.road_condition_events.insert_many(docs, ordered=False)
    except Exception as exc:
        logger.error(f"[road_conditions] insert_many failed: {exc}")
        return 0

    # Aggregate affected cells in the background — don't block the HTTP response
    asyncio.create_task(_aggregate_cells(list(h3_cells_touched)))
    return len(docs)


# ── Aggregation ──────────────────────────────────────────────────────────────────

async def _aggregate_cells(cells: List[str]) -> None:
    """Recompute segment confidence for a list of H3 cells."""
    for cell in cells:
        try:
            await _aggregate_cell(cell)
        except Exception as exc:
            logger.warning(f"[road_conditions] aggregation error for {cell}: {exc}")


async def _aggregate_cell(h3_index: str) -> None:
    """
    Group all observations in an H3 cell, compute speed-weighted confidence,
    and upsert a confirmed segment once ≥ MIN_DEVICE_CONFIRMATIONS unique
    devices have reported it.
    """
    cursor = nosql_db.road_condition_events.find(
        {"h3_index": h3_index},
        {"device_id": 1, "speed_kmh": 1, "label": 1, "confidence": 1, "_id": 0},
    )
    observations = await cursor.to_list(length=10_000)
    if not observations:
        return

    device_ids = {obs["device_id"] for obs in observations}
    device_count = len(device_ids)

    # Speed-weight: low-speed = 1.0, high-speed = HIGH_SPEED_PENALTY
    total_weight = 0.0
    weighted_confidence = 0.0
    label_scores: Dict[str, float] = {}

    for obs in observations:
        w = 1.0 if obs["speed_kmh"] <= LOW_SPEED_THRESHOLD_KMH else HIGH_SPEED_PENALTY
        c = float(obs.get("confidence", 0.5)) * w
        total_weight += w
        weighted_confidence += c
        label = obs.get("label", "smooth")
        label_scores[label] = label_scores.get(label, 0.0) + c

    avg_confidence = weighted_confidence / total_weight if total_weight > 0 else 0.0

    # Dominant label: highest total weighted confidence, tie-broken by priority
    dominant_label = max(
        label_scores,
        key=lambda lbl: (label_scores[lbl], LABEL_PRIORITY.get(lbl, 0)),
    )

    confirmed = device_count >= MIN_DEVICE_CONFIRMATIONS

    # H3 cell centre for geospatial queries
    cell_lat, cell_lon = h3.cell_to_latlng(h3_index)

    await nosql_db.road_condition_segments.update_one(
        {"h3_index": h3_index},
        {
            "$set": {
                "h3_index": h3_index,
                "location": {"type": "Point", "coordinates": [cell_lon, cell_lat]},
                "label": dominant_label,
                "device_count": device_count,
                "observation_count": len(observations),
                "avg_confidence": round(avg_confidence, 4),
                "confirmed": confirmed,
                "last_updated": int(time.time() * 1000),
            }
        },
        upsert=True,
    )

    await _segment_cache.invalidate(h3_index)


# ── Nearby query ─────────────────────────────────────────────────────────────────

async def get_nearby_hazards(
    lat: float,
    lon: float,
    radius_m: float = 5000.0,
) -> List[Dict[str, Any]]:
    """
    Return confirmed road hazard segments within `radius_m` metres.

    Results are served from the in-memory cache when possible.
    """
    cache_key = f"nearby:{round(lat, 3)}:{round(lon, 3)}:{int(radius_m)}"
    cached = await _segment_cache.get(cache_key)
    if cached is not None:
        return cached

    try:
        cursor = nosql_db.road_condition_segments.find(
            {
                "confirmed": True,
                "location": {
                    "$nearSphere": {
                        "$geometry": {"type": "Point", "coordinates": [lon, lat]},
                        "$maxDistance": radius_m,
                    }
                },
            },
            {
                "_id": 0,
                "h3_index": 1,
                "label": 1,
                "avg_confidence": 1,
                "device_count": 1,
                "location": 1,
            },
        ).limit(500)

        results = await cursor.to_list(length=500)

        # Convert GeoJSON coordinates to flat lat/lon for the mobile client
        output = []
        for seg in results:
            coords = seg.get("location", {}).get("coordinates", [0, 0])
            output.append({
                "h3_index": seg["h3_index"],
                "lat": coords[1],
                "lon": coords[0],
                "label": seg["label"],
                "confidence": seg.get("avg_confidence", 0.0),
                "device_count": seg.get("device_count", 0),
            })

        await _segment_cache.set(cache_key, output)
        return output

    except Exception as exc:
        logger.error(f"[road_conditions] nearby query failed: {exc}")
        return []
