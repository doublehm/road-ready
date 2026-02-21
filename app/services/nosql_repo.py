from typing import List, Dict, Any
from app.database import nosql_db
import logging

logger = logging.getLogger(__name__)

class NoSQLRepository:
    """
    Repository for high-throughput sensor data and events stored in MongoDB.
    """
    def __init__(self):
        self.telemetry = nosql_db.telemetry
        self.events = nosql_db.events

    async def save_telemetry_chunk(self, ride_id: str, points: List[Dict[str, Any]]) -> bool:
        """
        Persist a chunk of telemetry points for a specific ride.
        """
        if not points:
            return True
            
        try:
            # Add ride_id to each point for querying
            for p in points:
                p["ride_id"] = str(ride_id)
            
            await self.telemetry.insert_many(points)
            return True
        except Exception as e:
            logger.error(f"Failed to save telemetry chunk for ride {ride_id}: {e}")
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
        try:
            event["ride_id"] = str(ride_id)
            await self.events.insert_one(event)
            return True
        except Exception as e:
            logger.error(f"Failed to save event for ride {ride_id}: {e}")
            return False

    async def get_ride_events(self, ride_id: str) -> List[Dict[str, Any]]:
        """
        Retrieve all events recorded for a specific ride.
        """
        cursor = self.events.find({"ride_id": str(ride_id)}).sort("timestamp", 1)
        return await cursor.to_list(length=1000)
