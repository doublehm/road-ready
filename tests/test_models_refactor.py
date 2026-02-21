import pytest
from app import models

def test_sensor_tables_removed():
    # Assert that high-frequency tables are no longer in models.py
    with pytest.raises(AttributeError):
        _ = models.DiagnosticRidePoint
    with pytest.raises(AttributeError):
        _ = models.DiagnosticRideAcceleration
    with pytest.raises(AttributeError):
        _ = models.DiagnosticRideRotation

def test_diagnostic_ride_exists():
    # Ensure DiagnosticRide itself remains
    assert models.DiagnosticRide is not None
    # Check for the NoSQL reference field
    assert hasattr(models.DiagnosticRide, 'nosql_ref')
