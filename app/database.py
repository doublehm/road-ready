from sqlalchemy import create_engine
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker
from motor.motor_asyncio import AsyncIOMotorClient
import os

# --- SQL Configuration (Relational Business Logic) ---
# Use env var for Prod, default to SQLite for Dev
SQLALCHEMY_DATABASE_URL = os.getenv("DATABASE_URL", "sqlite:///./roadready.db")

connect_args = {}
if SQLALCHEMY_DATABASE_URL.startswith("sqlite"):
    connect_args = {"check_same_thread": False}

engine = create_engine(
    SQLALCHEMY_DATABASE_URL, connect_args=connect_args
)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

Base = declarative_base()

# --- NoSQL Configuration (High-Volume Telemetry) ---
# Default to localhost for local dev if not in Docker
MONGODB_URL = os.getenv("MONGODB_URL", "mongodb://localhost:27017/roadready")
mongodb_client = AsyncIOMotorClient(MONGODB_URL)
nosql_db = mongodb_client.get_default_database()

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

def get_nosql_db():
    return nosql_db
