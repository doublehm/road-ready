import requests
import time
import math
from typing import Dict, Optional, Tuple
from datetime import datetime

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

OVERPASS_URL = "https://overpass-api.de/api/interpreter"
OVERPASS_TIMEOUT = 5  # seconds

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

        # Prefer elements with maxspeed tag
        with_maxspeed = [e for e in elements if e.get("tags", {}).get("maxspeed")]
        if with_maxspeed:
            elem = with_maxspeed[0]
            tags = elem.get("tags", {})
            speed_limit = _parse_maxspeed(tags.get("maxspeed"))
            return {
                "speed_limit_kmh": speed_limit,
                "source": "osm",
                "road_name": tags.get("name"),
                "road_type": tags.get("highway", "unknown"),
                "maxspeed_conditional": tags.get("maxspeed:conditional"),
            }

        # No maxspeed tag - return road type for default lookup
        elem = elements[0]
        tags = elem.get("tags", {})
        return {
            "speed_limit_kmh": None,
            "source": "osm_no_maxspeed",
            "road_name": tags.get("name"),
            "road_type": tags.get("highway", "unknown"),
            "maxspeed_conditional": tags.get("maxspeed:conditional"),
        }

    except (requests.RequestException, ValueError, KeyError):
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
        # Overpass failed entirely - use urban default
        speed_limit = 50
        source = "default"

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

    # Cache the result
    _speed_limit_cache[cache_key] = (result, time.time())

    return result


def clear_cache():
    """Clear the speed limit cache."""
    _speed_limit_cache.clear()
