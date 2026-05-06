from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel
from sqlalchemy.orm import Session
from typing import Optional, List

from app.api import deps
from app.services.osm_correction_service import submit_flag, get_nearby_flags

router = APIRouter()


class FlagRequest(BaseModel):
    lat: float
    lon: float
    osm_speed_kmh: float
    observed_speed_kmh: float
    reported_speed_kmh: Optional[float] = None


class FlagResponse(BaseModel):
    id: int
    lat: float
    lon: float
    h3_cell: str
    osm_speed_kmh: float
    observed_speed_kmh: float
    reported_speed_kmh: Optional[float]
    status: str

    model_config = {"from_attributes": True}


@router.post("/flag", response_model=FlagResponse)
async def flag_speed_limit(
    request: FlagRequest,
    db: Session = Depends(deps.get_db),
    current_user=Depends(deps.get_current_user),
):
    """Submit a crowdsourced speed limit discrepancy report."""
    flag = await submit_flag(
        db=db,
        lat=request.lat,
        lon=request.lon,
        osm_speed_kmh=request.osm_speed_kmh,
        observed_speed_kmh=request.observed_speed_kmh,
        reported_speed_kmh=request.reported_speed_kmh,
        user_id=current_user.id if current_user else None,
    )
    return flag


@router.get("/flags/nearby", response_model=List[FlagResponse])
def get_flags_nearby(
    lat: float = Query(...),
    lon: float = Query(...),
    radius_km: float = Query(0.5, ge=0.1, le=5.0),
    db: Session = Depends(deps.get_db),
):
    """Return speed limit flags within radius_km of a coordinate."""
    return get_nearby_flags(db, lat, lon, radius_km)
