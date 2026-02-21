import pytest
from app.services.diagnostic_evaluator import DiagnosticEvaluator
from unittest.mock import AsyncMock, patch

@pytest.mark.asyncio
async def test_evaluator_fetches_from_nosql():
    evaluator = DiagnosticEvaluator()
    ride_id = "test_ride_123"
    ride_data = {
        "duration_minutes": 25,
        "distance_km": 10,
        "speed_limit_data": "[]",
        "human_feedback": "[]"
    }
    
    # Mock NoSQL response
    mock_telemetry = [
        {"timestamp": 1000, "speed": 50, "location": {"latitude": 49.2, "longitude": -123.1}},
        {"timestamp": 1001, "speed": 55, "location": {"latitude": 49.21, "longitude": -123.11}}
    ]
    
    with patch.object(evaluator.nosql_repo, "get_ride_telemetry", new_callable=AsyncMock) as mock_get_tel:
        mock_get_tel.return_value = mock_telemetry
        with patch.object(evaluator.nosql_repo, "get_ride_events", new_callable=AsyncMock) as mock_get_ev:
            mock_get_ev.return_value = []
            
            result = await evaluator.evaluate(ride_id, ride_data)
            
            assert mock_get_tel.called
            assert "overall_score" in result
            assert "evaluation_result" in result
