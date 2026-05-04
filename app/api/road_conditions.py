"""
Road Conditions API

POST /road-conditions/report  — receive a batch of classified events from a completed trip
GET  /road-conditions/nearby  — return confirmed hazard segments within a radius
"""

from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, Field

from app.api.deps import get_current_user
from app.models import User
from app.services.road_condition_service import get_nearby_hazards, ingest_trip_report

router = APIRouter()


# ── Request / Response models ────────────────────────────────────────────────────

class RoadConditionEvent(BaseModel):
    lat: float
    lon: float
    speed_kmh: float = Field(ge=0)
    label: str = Field(pattern=r"^(smooth|rough|bump|pothole|speed_bump)$")
    confidence: float = Field(ge=0.0, le=1.0)
    timestamp: Optional[int] = None  # epoch ms; server fills if absent


class TripReportRequest(BaseModel):
    ride_id: str
    events: List[RoadConditionEvent] = Field(max_length=5000)


class TripReportResponse(BaseModel):
    accepted: int


class NearbyHazard(BaseModel):
    h3_index: str
    lat: float
    lon: float
    label: str
    confidence: float
    device_count: int


class NearbyResponse(BaseModel):
    hazards: List[NearbyHazard]
    count: int


# ── Endpoints ────────────────────────────────────────────────────────────────────

@router.post("/report", response_model=TripReportResponse)
async def report_road_conditions(
    body: TripReportRequest,
    current_user: User = Depends(get_current_user),
) -> TripReportResponse:
    """
    Accept a batch of on-device classified road events uploaded at ride completion.
    Aggregation runs asynchronously — this endpoint returns immediately.
    """
    device_id = f"user_{current_user.id}"
    accepted = await ingest_trip_report(
        device_id=device_id,
        ride_id=body.ride_id,
        events=[ev.model_dump() for ev in body.events],
    )
    return TripReportResponse(accepted=accepted)


@router.get("/nearby", response_model=NearbyResponse)
async def nearby_hazards(
    lat: float = Query(..., ge=-90, le=90),
    lng: float = Query(..., ge=-180, le=180),
    radius_m: float = Query(default=5000.0, ge=100, le=50_000),
    current_user: User = Depends(get_current_user),
) -> NearbyResponse:
    """
    Return confirmed road hazard segments within `radius_m` metres of the given
    coordinates. Used by the mobile app to prefetch the local hazard cache at
    trip start.
    """
    hazards = await get_nearby_hazards(lat=lat, lon=lng, radius_m=radius_m)
    return NearbyResponse(hazards=[NearbyHazard(**h) for h in hazards], count=len(hazards))
