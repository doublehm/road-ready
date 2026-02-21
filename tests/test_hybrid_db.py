import pytest
from app import database
from motor.motor_asyncio import AsyncIOMotorClient

def test_sqlite_connection():
    # Test that SQL SessionLocal is available
    db = database.SessionLocal()
    assert db is not None
    db.close()

@pytest.mark.asyncio
async def test_mongodb_connection():
    # Test that MongoDB client and database are configured
    assert database.mongodb_client is not None
    assert database.nosql_db is not None
    
    # Try a simple ping (may fail if MongoDB not running, which is fine for unit test config)
    try:
        # Check if the client is initialized correctly
        assert isinstance(database.mongodb_client, AsyncIOMotorClient)
        # Verify the database name matches
        assert database.nosql_db.name == "roadready"
    except Exception as e:
        print(f"MongoDB Config Check: {e}")
        raise e
