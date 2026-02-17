from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)

def test_terms_page_content():
    response = client.get("/terms")
    assert response.status_code == 200
    assert "British Columbia" in response.text
    assert "ICBC" in response.text
    assert "Liability Disclaimer" in response.text

def test_privacy_page_content():
    response = client.get("/privacy")
    assert response.status_code == 200
    assert "British Columbia" in response.text
    assert "PIPA" in response.text
    assert "Personal Information Protection Act" in response.text
    assert "Privacy Officer" in response.text

def test_home_page_footer():
    response = client.get("/")
    assert response.status_code == 200
    assert "independent driving instructors" in response.text
    assert "ICBC compliance" in response.text
    assert "Road Ready is not liable" in response.text
