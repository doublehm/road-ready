import pytest
from app.services.elevation_service import grade_summary, _grade_category

def test_elevation_uphill():
    # elev1 (current) = 150.0m, elev2 (previous) = 100.0m (Uphill!)
    # Distance = 500m
    # grade_pct should be (150 - 100) / 500 * 100 = 10.0%
    # With dist_m = 500, we can mock the lat/lon to be anything that produces haversine distance of 500m.
    # To keep it simple and test the math directly:
    # Let's verify _grade_category logic first
    assert _grade_category(12.0) == "steep_uphill"
    assert _grade_category(6.0) == "moderate_uphill"
    assert _grade_category(3.0) == "gentle_uphill"
    assert _grade_category(0.0) == "flat"
    assert _grade_category(-3.0) == "gentle_downhill"
    assert _grade_category(-6.0) == "moderate_downhill"
    assert _grade_category(-12.0) == "steep_downhill"

def test_grade_summary_uphill():
    # Vancouver coordinates roughly 500m apart
    lat1, lon1 = 49.2827, -123.1207 # Current
    lat2, lon2 = 49.2782, -123.1207 # Previous
    
    # Going uphill: elev1 (current) > elev2 (previous)
    res = grade_summary(
        elev1=150.0,
        elev2=100.0,
        lat1=lat1,
        lon1=lon1,
        lat2=lat2,
        lon2=lon2
    )
    
    assert res["elevation_m"] == 150.0  # Should be current elevation (elev1)
    assert res["grade_pct"] > 0.0       # Should be positive
    assert "uphill" in res["category"]
    assert len(res["tips"]) > 0

def test_grade_summary_downhill():
    # Vancouver coordinates roughly 500m apart
    lat1, lon1 = 49.2827, -123.1207 # Current
    lat2, lon2 = 49.2782, -123.1207 # Previous
    
    # Going downhill: elev1 (current) < elev2 (previous)
    res = grade_summary(
        elev1=100.0,
        elev2=150.0,
        lat1=lat1,
        lon1=lon1,
        lat2=lat2,
        lon2=lon2
    )
    
    assert res["elevation_m"] == 100.0  # Should be current elevation (elev1)
    assert res["grade_pct"] < 0.0       # Should be negative
    assert "downhill" in res["category"]
    assert len(res["tips"]) > 0
