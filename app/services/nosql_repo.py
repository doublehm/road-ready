from typing import List, Dict, Any
from app.database import nosql_db
import logging

logger = logging.getLogger(__name__)

class NoSQLRepository:
    """
    Repository for high-throughput sensor data and events stored in MongoDB.
    Falls back gracefully when MongoDB is unavailable.
    """
    def __init__(self):
        self.telemetry = nosql_db.telemetry
        self.events = nosql_db.events
        self._available = True  # circuit breaker

    def _mark_unavailable(self, e: Exception):
        if self._available:
            logger.warning(f"MongoDB unavailable, telemetry will not be persisted: {type(e).__name__}")
            self._available = False

    async def save_telemetry_chunk(self, ride_id: str, points: List[Dict[str, Any]]) -> bool:
        """
        Persist a chunk of telemetry points for a specific ride.
        """
        if not points or not self._available:
            return True
            
        try:
            # Add ride_id to each point for querying
            for p in points:
                p["ride_id"] = str(ride_id)
            
            await self.telemetry.insert_many(points)
            return True
        except Exception as e:
            self._mark_unavailable(e)
            return False

    async def get_ride_telemetry(self, ride_id: str) -> List[Dict[str, Any]]:
        """
        Retrieve all telemetry points for a specific ride, sorted by timestamp.
        """
        cursor = self.telemetry.find({"ride_id": str(ride_id)}).sort("timestamp", 1)
        return await cursor.to_list(length=100000) # Large limit for full ride data

    async def save_event(self, ride_id: str, event: Dict[str, Any]) -> bool:
        """
        Persist a single event (mistake or system event) for a ride.
        """
        if not self._available:
            return True
        try:
            event["ride_id"] = str(ride_id)
            await self.events.insert_one(event)
            return True
        except Exception as e:
            self._mark_unavailable(e)
            return False

    async def get_ride_events(self, ride_id: str) -> List[Dict[str, Any]]:
        """
        Retrieve all events recorded for a specific ride.
        """
        cursor = self.events.find({"ride_id": str(ride_id)}).sort("timestamp", 1)
        return await cursor.to_list(length=1000)
