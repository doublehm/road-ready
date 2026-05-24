import pytest
from app import models

def test_prevent_direct_email_phone_number_update(client, db, auth_headers):
    # Create student
    student = models.User(email="student_priv@example.com", full_name="Student Priv", role="student", hashed_password="pwd", phone_number="111-222-3333")
    db.add(student)
    db.commit()

    headers = auth_headers("student_priv@example.com")

    # Try to modify email
    response = client.put(
        "/api/v1/users/me",
        json={"email": "hacked@example.com"},
        headers=headers
    )
    assert response.status_code == 400
    assert "not permitted" in response.json()["detail"]

    # Try to modify phone number
    response = client.put(
        "/api/v1/users/me",
        json={"phone_number": "999-999-9999"},
        headers=headers
    )
    assert response.status_code == 400
    assert "not permitted" in response.json()["detail"]

    # Changing full name should work
    response = client.put(
        "/api/v1/users/me",
        json={"full_name": "Updated Name"},
        headers=headers
    )
    assert response.status_code == 200
    assert response.json()["full_name"] == "Updated Name"


def test_user_data_export(client, db, auth_headers):
    # Create student, profile, bookings
    student = models.User(email="export_me@example.com", full_name="Export Me", role="student", hashed_password="pwd")
    db.add(student)
    db.commit()

    student_profile = models.StudentProfile(user_id=student.id, age=20, l_license_number="1234567")
    db.add(student_profile)
    db.commit()

    headers = auth_headers("export_me@example.com")
    response = client.get("/api/v1/users/me/export", headers=headers)
    assert response.status_code == 200
    
    data = response.json()
    assert "user_details" in data
    assert data["user_details"]["email"] == "export_me@example.com"
    assert data["student_profile"]["l_license_number"] == "1234567"
    assert "bookings" in data
    assert "diagnostic_rides" in data


def test_user_anonymization_sovereignty(client, db, auth_headers):
    # Create student, profile, and bookings
    student = models.User(email="delete_me@example.com", full_name="Delete Me", role="student", hashed_password="pwd", phone_number="555-555-5555")
    db.add(student)
    db.commit()

    student_profile = models.StudentProfile(user_id=student.id, age=22, l_license_number="7654321", license_image="file.jpg")
    db.add(student_profile)
    db.commit()

    instructor = models.InstructorProfile(user_id=student.id, bio="Bio", hourly_rate=50.0, city="V", car_model="X")
    db.add(instructor)
    db.commit()

    booking = models.BookingRequest(
        student_id=student.id,
        instructor_id=instructor.id,
        date="2026-05-24",
        time="10:00",
        duration=2,
        pickup_address="123 Main St",
        status="pending",
        focus_areas="parking"
    )
    db.add(booking)
    db.commit()

    # Call delete / anonymize endpoint
    headers = auth_headers("delete_me@example.com")
    response = client.delete("/api/v1/users/me", headers=headers)
    assert response.status_code == 200
    assert "anonymized" in response.json()["message"]

    # Verify database scrubbing
    db.refresh(student)
    db.refresh(student_profile)
    db.refresh(booking)

    assert student.email.startswith("deleted_")
    assert student.phone_number.startswith("deleted_")
    assert student.full_name == "Anonymized User"
    assert student_profile.l_license_number == "DELETED"
    assert student_profile.license_image is None
    assert booking.pickup_address == "Address Removed"
