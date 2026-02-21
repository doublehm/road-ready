import pytest
from unittest.mock import MagicMock, AsyncMock
from app.services.nosql_repo import NoSQLRepository
import time

@pytest.mark.asyncio
async def test_nosql_repo_telemetry_logic():
    # Mock the database collections
    mock_telemetry = AsyncMock()
    mock_events = AsyncMock()
    
    repo = NoSQLRepository()
    repo.telemetry = mock_telemetry
    repo.events = mock_events
    
    ride_id = "test_ride_123"
    points = [
        {"timestamp": 1000, "speed": 50},
        {"timestamp": 1001, "speed": 51}
    ]
    
    # Test insertion
    await repo.save_telemetry_chunk(ride_id, points)
    
    # Verify ride_id was added to points
    mock_telemetry.insert_many.assert_called_once()
    args, _ = mock_telemetry.insert_many.call_args
    inserted_points = args[0]
    assert all(p["ride_id"] == ride_id for p in inserted_points)
    assert inserted_points[0]["timestamp"] == 1000

@pytest.mark.asyncio
async def test_nosql_repo_retrieval_logic():
    # Motor's find returns a cursor (synchronous call), then sort returns a cursor, 
    # and then to_list is awaited.
    mock_telemetry = MagicMock()
    mock_cursor = MagicMock()
    mock_cursor.sort.return_value = mock_cursor
    mock_cursor.to_list = AsyncMock(return_value=[{"ride_id": "123", "speed": 50}])
    mock_telemetry.find.return_value = mock_cursor
    
    repo = NoSQLRepository()
    repo.telemetry = mock_telemetry
    
    results = await repo.get_ride_telemetry("123")
    
    mock_telemetry.find.assert_called_once_with({"ride_id": "123"})
    assert len(results) == 1
    assert results[0]["speed"] == 50
