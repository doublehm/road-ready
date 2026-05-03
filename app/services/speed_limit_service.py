import logging
import requests
import time
import math
from typing import Dict, Optional, Tuple
from datetime import datetime

logger = logging.getLogger(__name__)

# British Columbia default speed limits by road type (km/h)
BC_DEFAULTS = {
    "motorway": 120,
    "motorway_link": 60,
    "trunk": 80,
    "trunk_link": 60,
    "primary": 80,
    "primary_link": 60,
    "secondary": 60,
    "secondary_link": 50,
    "tertiary": 50,
    "tertiary_link": 50,
    "residential": 50,
    "living_street": 30,
    "unclassified": 50,
    "service": 20,
}

BC_SCHOOL_ZONE_LIMIT = 30  # km/h Mon-Fri 8am-5pm

# Road type priority: higher index = higher-class road.
# When multiple roads are near the GPS point, we prefer the one the driver
# is most likely on (e.g. motorway over an adjacent service road or link).
ROAD_TYPE_PRIORITY = {
    "service": 0,
    "living_street": 1,
    "residential": 2,
    "unclassified": 3,
    "tertiary_link": 4,
    "tertiary": 5,
    "secondary_link": 6,
    "secondary": 7,
    "primary_link": 8,
    "primary": 9,
    "trunk_link": 10,
    "trunk": 11,
    "motorway_link": 12,
    "motorway": 13,
}

def _best_road(elements: list) -> Optional[Dict]:
    """
    From a list of Overpass way elements, return the one most likely being
    driven on. Strategy:
      1. Among elements with a maxspeed tag, pick the highest-class road type.
      2. Fall back to the highest-class road overall.
    This prevents a nearby 50 km/h service road or on-ramp from overriding the
    100 km/h motorway the driver is actually on.
    """
    if not elements:
        return None

    def priority(elem):
        road_type = elem.get("tags", {}).get("highway", "")
        return ROAD_TYPE_PRIORITY.get(road_type, -1)

    with_maxspeed = [e for e in elements if e.get("tags", {}).get("maxspeed")]
    candidates = with_maxspeed if with_maxspeed else elements
    return max(candidates, key=priority)

OVERPASS_URL = "https://overpass-api.de/api/interpreter"
OVERPASS_TIMEOUT = 10  # seconds

# Cache: key = rounded (lat, lon) -> value = (result_dict, timestamp)
_speed_limit_cache: Dict[Tuple[float, float], Tuple[Dict, float]] = {}
CACHE_TTL = 86400  # 24 hours


def _round_coords(lat: float, lon: float) -> Tuple[float, float]:
    """Round coordinates to ~100m precision (3 decimal places)."""
    return (round(lat, 3), round(lon, 3))


def _is_school_hours() -> bool:
    """Check if current time is within BC school zone hours (Mon-Fri 8am-5pm)."""
    now = datetime.now()
    # Monday=0, Friday=4
    if now.weekday() > 4:
        return False
    return 8 <= now.hour < 17


def _parse_maxspeed(maxspeed_str: str) -> Optional[int]:
    """Parse OSM maxspeed tag value to km/h integer."""
    if not maxspeed_str:
        return None
    # Remove whitespace
    maxspeed_str = maxspeed_str.strip()
    # Handle "XX mph" format
    if "mph" in maxspeed_str.lower():
        try:
            mph = int(maxspeed_str.lower().replace("mph", "").strip())
            return int(mph * 1.60934)
        except ValueError:
            return None
    # Handle plain number (assumed km/h)
    try:
        return int(maxspeed_str)
    except ValueError:
        return None


def _query_overpass(lat: float, lon: float, radius: int = 50) -> Optional[Dict]:
    """
    Query Overpass API for speed limit data near the given coordinates.

    Returns dict with road info or None on failure.
    """
    query = f"""
    [out:json][timeout:{OVERPASS_TIMEOUT}];
    (
      way(around:{radius},{lat},{lon})["highway"]["maxspeed"];
      way(around:{radius},{lat},{lon})["highway"];
    );
    out tags;
    """
    try:
        response = requests.post(
            OVERPASS_URL,
            data={"data": query},
            timeout=OVERPASS_TIMEOUT + 2,
        )
        if response.status_code != 200:
            return None

        data = response.json()
        elements = data.get("elements", [])
        if not elements:
            return None

        # Pick the best road — highest-class highway, preferring ones with a
        # maxspeed tag. This avoids a nearby on-ramp or service road "winning".
        elem = _best_road(elements)
        if elem is None:
            return None
        tags = elem.get("tags", {})
        speed_limit = _parse_maxspeed(tags.get("maxspeed"))
        return {
            "speed_limit_kmh": speed_limit,
            "source": "osm" if speed_limit else "osm_no_maxspeed",
            "road_name": tags.get("name"),
            "road_type": tags.get("highway", "unknown"),
            "maxspeed_conditional": tags.get("maxspeed:conditional"),
        }

    except (requests.RequestException, ValueError, KeyError) as e:
        logger.warning("Overpass query failed for (%.4f, %.4f): %s", lat, lon, e)
        return None


def _check_nearby_school(lat: float, lon: float) -> bool:
    """Check if there's a school within 200m of the coordinates."""
    query = f"""
    [out:json][timeout:{OVERPASS_TIMEOUT}];
    (
      node(around:200,{lat},{lon})["amenity"="school"];
      way(around:200,{lat},{lon})["amenity"="school"];
    );
    out count;
    """
    try:
        response = requests.post(
            OVERPASS_URL,
            data={"data": query},
            timeout=OVERPASS_TIMEOUT + 2,
        )
        if response.status_code != 200:
            return False

        data = response.json()
        elements = data.get("elements", [])
        # The count query returns a single element with tags.total
        if elements:
            total = elements[0].get("tags", {}).get("total", "0")
            return int(total) > 0
        return False
    except (requests.RequestException, ValueError, KeyError):
        return False


def get_speed_limit(lat: float, lon: float) -> Dict:
    """
    Get the speed limit for the given GPS coordinates.

    Uses Overpass API with caching and BC provincial defaults as fallback.

    Returns:
        Dict with keys: speed_limit_kmh, source, road_name, road_type, zone_type
    """
    cache_key = _round_coords(lat, lon)

    # Check cache
    if cache_key in _speed_limit_cache:
        cached_result, cached_time = _speed_limit_cache[cache_key]
        if time.time() - cached_time < CACHE_TTL:
            return cached_result

    # Query Overpass
    osm_result = _query_overpass(lat, lon)

    speed_limit = None
    source = "default"
    road_name = None
    road_type = "unknown"
    zone_type = "regular"

    if osm_result:
        road_name = osm_result.get("road_name")
        road_type = osm_result.get("road_type", "unknown")

        # Check for conditional speed limit (school zone)
        conditional = osm_result.get("maxspeed_conditional")
        if conditional and "school" in conditional.lower() and _is_school_hours():
            # Parse conditional speed limit
            parsed = _parse_maxspeed(conditional.split("@")[0].strip())
            if parsed:
                speed_limit = parsed
                zone_type = "school"
                source = "osm_conditional"

        # Use OSM maxspeed if available and no conditional override
        if speed_limit is None and osm_result.get("speed_limit_kmh"):
            speed_limit = osm_result["speed_limit_kmh"]
            source = "osm"

        # Fall back to BC defaults based on road type
        if speed_limit is None:
            speed_limit = BC_DEFAULTS.get(road_type, 50)
            source = "bc_default"

    else:
        # Overpass failed entirely — use urban default but do NOT cache,
        # so the next query retries the real API instead of locking in 50.
        logger.warning("Overpass returned no data for (%.4f, %.4f); using 50 km/h default", lat, lon)
        return {
            "speed_limit_kmh": 50,
            "source": "default",
            "road_name": None,
            "road_type": "unknown",
            "zone_type": "regular",
        }

    # Check for nearby school (if not already in school zone and during school hours)
    if zone_type != "school" and _is_school_hours():
        if _check_nearby_school(lat, lon):
            speed_limit = BC_SCHOOL_ZONE_LIMIT
            zone_type = "school"
            source = "school_proximity"

    result = {
        "speed_limit_kmh": speed_limit,
        "source": source,
        "road_name": road_name,
        "road_type": road_type,
        "zone_type": zone_type,
    }

    # Only cache successful OSM lookups — never cache fallback defaults.
    if source != "default":
        _speed_limit_cache[cache_key] = (result, time.time())

    return result


def clear_cache():
    """Clear the speed limit cache."""
    _speed_limit_cache.clear()
