import pytest
from unittest.mock import patch, MagicMock
from app import models

def test_create_stripe_account_initiation(client, db, auth_headers):
    # 1. Create an instructor
    user = models.User(email="inst_stripe@example.com", full_name="Inst Stripe", role="instructor", hashed_password="hashed")
    db.add(user)
    db.commit()
    profile = models.InstructorProfile(user_id=user.id, bio="Bio", hourly_rate=50.0, city="V", car_model="X", insurance_policy="Y", certification_id="Z")
    db.add(profile)
    db.commit()
    
    headers = auth_headers("inst_stripe@example.com")
    
    # 2. Mock Stripe API
    with patch("app.api.instructors.stripe.Account.create") as mock_create, \
         patch("app.api.instructors.stripe.AccountLink.create") as mock_link:
        
        mock_create.return_value = MagicMock(id="acct_test_123")
        mock_link.return_value = MagicMock(url="https://connect.stripe.com/setup/s/mock_link")
        
        # 3. Call the endpoint (should FAIL with 404 until implemented)
        response = client.post("/api/v1/instructors/me/stripe-onboarding", headers=headers)
        
        assert response.status_code == 200
        data = response.json()
        assert data["url"] == "https://connect.stripe.com/setup/s/mock_link"
        
        # Verify DB was updated
        db.refresh(profile)
        assert profile.stripe_account_id == "acct_test_123"
