import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from app.database import Base
from app.main import app

# Use an in-memory SQLite database for testing
SQLALCHEMY_DATABASE_URL = "sqlite://"

engine = create_engine(
    SQLALCHEMY_DATABASE_URL,
    connect_args={"check_same_thread": False},
    poolclass=StaticPool,
)
TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

@pytest.fixture(scope="function")
def db():
    Base.metadata.create_all(bind=engine)
    session = TestingSessionLocal()
    try:
        yield session
    finally:
        session.close()
        Base.metadata.drop_all(bind=engine)

@pytest.fixture(scope="function")
def client(db):
    from fastapi.testclient import TestClient
    from app.api.deps import get_db as deps_get_db
    from app.main import get_db as main_get_db
    
    def override_get_db():
        try:
            yield db
        finally:
            pass
            
    app.dependency_overrides[deps_get_db] = override_get_db
    app.dependency_overrides[main_get_db] = override_get_db
    
    with TestClient(app) as c:
        yield c
    app.dependency_overrides.clear()


@pytest.fixture(scope="function")
def auth_headers():
    from app.security import create_access_token
    from datetime import timedelta
    
    def _auth_headers(email: str):
        access_token = create_access_token(
            data={"sub": email}, expires_delta=timedelta(minutes=15)
        )
        return {"Authorization": f"Bearer {access_token}"}
    
    return _auth_headers
