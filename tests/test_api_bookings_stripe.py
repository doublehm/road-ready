import pytest
from unittest.mock import patch, MagicMock
from app import models

def test_checkout_page_creates_payment_intent(client, db):
    # 1. Create student
    student = models.User(email="stud_pay@example.com", full_name="Stud Pay", role="student", hashed_password="hashed")
    db.add(student)
    db.commit()
    
    # 2. Create instructor with Stripe account
    inst_user = models.User(email="inst_pay@example.com", full_name="Inst Pay", role="instructor", hashed_password="hashed")
    db.add(inst_user)
    db.commit()
    inst_profile = models.InstructorProfile(
        user_id=inst_user.id, 
        bio="Bio", 
        hourly_rate=100.0, 
        city="V", 
        car_model="X", 
        insurance_policy="Y", 
        certification_id="Z",
        stripe_account_id="acct_test_destination",
        stripe_onboarding_completed=True
    )
    db.add(inst_profile)
    db.commit()
    
    # 3. Create a booking
    booking = models.BookingRequest(
        student_id=student.id,
        instructor_id=inst_profile.id,
        date="2024-01-01",
        time="10:00",
        duration=1,
        total_amount=100.0,
        platform_fee=15.0,
        instructor_payout=85.0,
        status="pending_payment"
    )
    db.add(booking)
    db.commit()
    
    # 4. Mock login
    client.cookies.set("user_id", str(student.id))
    
    # 5. Mock Stripe PaymentIntent create
    with patch("app.main.stripe.PaymentIntent.create") as mock_pi_create:
        mock_pi_create.return_value = MagicMock(
            id="pi_test_123",
            client_secret="pi_test_123_secret_mock"
        )
        
        # 6. Access checkout page
        response = client.get(f"/checkout/{booking.id}")
        
        assert response.status_code == 200
        # Verify Stripe was called with correct parameters
        # 15.0 fee on 100.0 total
        # Stripe expects amounts in cents
        mock_pi_create.assert_called_once()
        args, kwargs = mock_pi_create.call_args
        assert kwargs["amount"] == 10000 # 100.00 * 100
        assert kwargs["application_fee_amount"] == 1500 # 15.00 * 100
        assert kwargs["transfer_data"]["destination"] == "acct_test_destination"
        
        # Verify DB was updated with PI ID
        db.refresh(booking)
        assert booking.stripe_payment_intent_id == "pi_test_123"
        # We expect the client secret to be in the template context (checked via response text)
        assert "pi_test_123_secret_mock" in response.text
