"""
Speed limit flag aggregation and automatic OSM correction service.

Workflow:
  1. Mobile app detects suspicious speed vs OSM limit discrepancy.
  2. User verifies or provides the correct speed limit.
  3. App submits a SpeedLimitFlag via POST /speed-limits/flag.
  4. This service aggregates flags per H3 cell (resolution 10, ~15 m).
  5. When ≥5 independent flags agree on a corrected limit (bucketed to
     nearest 10 km/h), it triggers an automatic OSM changeset.
  6. Requires OSM_OAUTH_TOKEN env var for live edits; otherwise logs only.

H3 resolution 10 matches road_condition_service.py for consistency.
"""

import h3
import httpx
import os
import re
import logging
from collections import Counter
from typing import Optional
from datetime import datetime

from sqlalchemy.orm import Session
from ..models import SpeedLimitFlag

logger = logging.getLogger(__name__)

H3_RESOLUTION = 10
FLAG_THRESHOLD = 5
OSM_API_BASE = "https://api.openstreetmap.org/api/0.6"
OVERPASS_URL = "https://overpass-api.de/api/interpreter"


async def submit_flag(
    db: Session,
    lat: float,
    lon: float,
    osm_speed_kmh: float,
    observed_speed_kmh: float,
    reported_speed_kmh: Optional[float],
    user_id: Optional[int] = None,
) -> SpeedLimitFlag:
    """Record a speed limit flag and check whether a correction should be triggered."""
    cell = h3.geo_to_h3(lat, lon, H3_RESOLUTION)
    flag = SpeedLimitFlag(
        user_id=user_id,
        lat=lat,
        lon=lon,
        h3_cell=cell,
        osm_speed_kmh=osm_speed_kmh,
        observed_speed_kmh=observed_speed_kmh,
        reported_speed_kmh=reported_speed_kmh,
        status="pending",
    )
    db.add(flag)
    db.commit()
    db.refresh(flag)
    await _check_and_correct(db, cell)
    return flag


async def _check_and_correct(db: Session, h3_cell: str) -> None:
    flags = (
        db.query(SpeedLimitFlag)
        .filter(SpeedLimitFlag.h3_cell == h3_cell, SpeedLimitFlag.status == "pending")
        .all()
    )
    if len(flags) < FLAG_THRESHOLD:
        return

    reported = [f.reported_speed_kmh for f in flags if f.reported_speed_kmh is not None]
    if not reported:
        return

    # Bucket to nearest 10 km/h to allow minor disagreement between respondents
    bucketed = [round(s / 10) * 10 for s in reported]
    winner_bucket, count = Counter(bucketed).most_common(1)[0]
    if count < FLAG_THRESHOLD:
        return

    osm_speed = flags[0].osm_speed_kmh
    lat_avg = sum(f.lat for f in flags) / len(flags)
    lon_avg = sum(f.lon for f in flags) / len(flags)

    logger.info(
        "Speed limit correction triggered: cell=%s, OSM=%.0f → consensus=%.0f km/h (%d flags)",
        h3_cell, osm_speed, winner_bucket, count,
    )

    changeset_id = await _submit_osm_correction(lat_avg, lon_avg, float(winner_bucket), osm_speed)

    now = datetime.utcnow()
    for flag in flags:
        flag.status = "corrected"
        flag.corrected_at = now
        if changeset_id:
            flag.osm_changeset_id = changeset_id
    db.commit()


async def _submit_osm_correction(lat: float, lon: float, new_speed: float, old_speed: float) -> Optional[int]:
    token = os.getenv("OSM_OAUTH_TOKEN")
    if not token:
        logger.info("OSM_OAUTH_TOKEN not configured — correction logged but not submitted to OSM")
        return None

    way_id = await _find_osm_way(lat, lon)
    if not way_id:
        logger.warning("No OSM way with maxspeed tag found near (%.4f, %.4f)", lat, lon)
        return None

    changeset_id = await _create_changeset(token, old_speed, new_speed)
    if not changeset_id:
        return None

    if await _update_way_maxspeed(token, way_id, new_speed, changeset_id):
        await _close_changeset(token, changeset_id)
        logger.info("OSM changeset %d submitted for way %d", changeset_id, way_id)
        return changeset_id

    await _close_changeset(token, changeset_id)
    return None


async def _find_osm_way(lat: float, lon: float) -> Optional[int]:
    query = (
        f"[out:json][timeout:10];\n"
        f"way(around:30,{lat},{lon})[highway][maxspeed];\n"
        f"out ids 1;"
    )
    try:
        async with httpx.AsyncClient(timeout=15.0) as client:
            r = await client.get(OVERPASS_URL, params={"data": query})
            if r.status_code == 200:
                elements = r.json().get("elements", [])
                if elements:
                    return elements[0]["id"]
    except Exception as exc:
        logger.warning("Overpass way lookup failed: %s", exc)
    return None


async def _create_changeset(token: str, old_speed: float, new_speed: float) -> Optional[int]:
    xml = (
        "<osm><changeset>"
        '<tag k="created_by" v="RoadReady/1.0"/>'
        f'<tag k="comment" v="Crowdsourced speed limit correction: {int(old_speed)} to {int(new_speed)} km/h ({FLAG_THRESHOLD}+ driver verifications)"/>'
        '<tag k="source" v="survey;crowd"/>'
        "</changeset></osm>"
    )
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            r = await client.put(
                f"{OSM_API_BASE}/changeset/create",
                content=xml,
                headers={"Authorization": f"Bearer {token}", "Content-Type": "text/xml"},
            )
            if r.status_code == 200:
                return int(r.text)
    except Exception as exc:
        logger.warning("OSM changeset creation failed: %s", exc)
    return None


async def _update_way_maxspeed(token: str, way_id: int, speed_kmh: float, changeset_id: int) -> bool:
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            r = await client.get(f"{OSM_API_BASE}/way/{way_id}")
            if r.status_code != 200:
                return False
            way_xml = r.text

        way_xml = re.sub(
            r'<tag k="maxspeed" v="[^"]*"/>',
            f'<tag k="maxspeed" v="{int(speed_kmh)}"/>',
            way_xml,
        )
        way_xml = way_xml.replace("<way ", f'<way changeset="{changeset_id}" ', 1)

        async with httpx.AsyncClient(timeout=10.0) as client:
            r = await client.put(
                f"{OSM_API_BASE}/way/{way_id}",
                content=way_xml,
                headers={"Authorization": f"Bearer {token}", "Content-Type": "text/xml"},
            )
            return r.status_code == 200
    except Exception as exc:
        logger.warning("OSM way update failed: %s", exc)
    return False


async def _close_changeset(token: str, changeset_id: int) -> None:
    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            await client.put(
                f"{OSM_API_BASE}/changeset/{changeset_id}/close",
                headers={"Authorization": f"Bearer {token}"},
            )
    except Exception as exc:
        logger.warning("OSM changeset close failed: %s", exc)


def get_nearby_flags(db: Session, lat: float, lon: float, radius_km: float = 0.5) -> list:
    """Return all flags within approximately radius_km of the coordinate."""
    center_cell = h3.geo_to_h3(lat, lon, H3_RESOLUTION)
    ring_size = max(1, int(radius_km / 0.015))
    cells = h3.k_ring(center_cell, ring_size)
    return (
        db.query(SpeedLimitFlag)
        .filter(SpeedLimitFlag.h3_cell.in_(list(cells)))
        .all()
    )
