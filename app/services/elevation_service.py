"""
Elevation data service using Open-Elevation API.

Provides road grade computation and driving tips for uphill/downhill terrain.
Results are cached in-process by coordinate (4 decimal places ~ 11 m precision).
"""

import logging
import math
import httpx
from typing import Optional, List, Tuple

logger = logging.getLogger(__name__)

OPEN_ELEVATION_URL = "https://api.open-elevation.com/api/v1/lookup"

_cache: dict = {}

GRADE_TIPS: dict = {
    "steep_uphill": [
        "Steep uphill: maintain steady throttle to avoid stalling",
        "Keep extra following distance — vehicles slow significantly on steep grades",
    ],
    "moderate_uphill": [
        "Moderate uphill: ease into throttle to maintain smooth speed",
        "Watch for slow-moving vehicles merging or losing speed on the grade",
    ],
    "gentle_uphill": [
        "Slight uphill: a small throttle increase may be needed",
    ],
    "flat": [],
    "gentle_downhill": [
        "Gentle downhill: use light engine braking to maintain speed",
    ],
    "moderate_downhill": [
        "Moderate downhill: use engine braking, avoid riding brakes continuously",
        "Increase following distance — stopping distances grow on downhill grades",
    ],
    "steep_downhill": [
        "Steep downhill: use engine braking aggressively, apply brakes intermittently",
        "Stay in a lower gear to maximize engine braking on steep descents",
        "Avoid sustained brake pressure — risk of brake fade on long descents",
    ],
}


def _grade_category(grade_pct: float) -> str:
    if grade_pct > 10:
        return "steep_uphill"
    elif grade_pct > 5:
        return "moderate_uphill"
    elif grade_pct > 2:
        return "gentle_uphill"
    elif grade_pct > -2:
        return "flat"
    elif grade_pct > -5:
        return "gentle_downhill"
    elif grade_pct > -10:
        return "moderate_downhill"
    else:
        return "steep_downhill"


def _haversine_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    R = 6_371_000.0
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlam = math.radians(lon2 - lon1)
    a = math.sin(dphi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlam / 2) ** 2
    return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


async def get_elevation(lat: float, lon: float) -> Optional[float]:
    """Return elevation in metres for a coordinate, or None on failure."""
    key = (round(lat, 4), round(lon, 4))
    if key in _cache:
        return _cache[key]
    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            r = await client.post(
                OPEN_ELEVATION_URL,
                json={"locations": [{"latitude": lat, "longitude": lon}]},
            )
            if r.status_code == 200:
                data = r.json()
                elev: float = data["results"][0]["elevation"]
                _cache[key] = elev
                return elev
    except Exception as exc:
        logger.warning("Elevation lookup failed for (%.4f, %.4f): %s", lat, lon, exc)
    return None


async def get_elevation_profile(coords: List[Tuple[float, float]]) -> List[Optional[float]]:
    """Batch elevation lookup for a route.  Returns a list aligned with coords."""
    if not coords:
        return []
    locations = [{"latitude": lat, "longitude": lon} for lat, lon in coords]
    try:
        async with httpx.AsyncClient(timeout=30.0) as client:
            r = await client.post(OPEN_ELEVATION_URL, json={"locations": locations})
            if r.status_code == 200:
                data = r.json()
                return [item["elevation"] for item in data["results"]]
    except Exception as exc:
        logger.warning("Batch elevation profile request failed: %s", exc)
    return [None] * len(coords)


def grade_summary(
    elev1: float,
    elev2: float,
    lat1: float,
    lon1: float,
    lat2: float,
    lon2: float,
) -> dict:
    """Compute grade between two points and return a response dict."""
    dist_m = _haversine_m(lat1, lon1, lat2, lon2)
    # elev1 is the current elevation, elev2 is the previous elevation.
    # Going uphill (elev1 > elev2) should yield a positive grade.
    grade_pct = (elev1 - elev2) / dist_m * 100 if dist_m >= 1 else 0.0
    cat = _grade_category(grade_pct)
    return {
        "elevation_m": round(elev1, 1),
        "grade_pct": round(grade_pct, 1),
        "category": cat,
        "tips": GRADE_TIPS.get(cat, []),
    }
