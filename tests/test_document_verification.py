import pytest
from app import models, security
from io import BytesIO

def test_student_document_upload_and_verification_cycle(client, db, auth_headers):
    # 1. Create a Student & Admin
    student = models.User(email="student_doc@example.com", full_name="Student Doc", role="student", hashed_password="pwd")
    admin = models.User(email="admin_doc@example.com", full_name="Admin Doc", role="admin", hashed_password="pwd")
    db.add(student)
    db.add(admin)
    db.commit()

    student_profile = models.StudentProfile(user_id=student.id, age=18, l_license_number="1234567")
    db.add(student_profile)
    db.commit()

    # 2. Upload Document as Student
    headers = auth_headers("student_doc@example.com")
    file_data = {"file": ("license.jpg", BytesIO(b"file_contents"), "image/jpeg")}
    response = client.post(
        "/api/v1/users/me/upload-document?document_type=student_license",
        files=file_data,
        headers=headers
    )
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "success"
    assert data["document_type"] == "student_license"
    assert data["verification_status"] == "submitted (pending admin review)"

    # Check Student Profile Quarantine
    db.refresh(student_profile)
    assert student_profile.license_status == "submitted"
    assert student_profile.is_verified is False

    # Check Document Review Log entry
    log = db.query(models.DocumentReviewLog).filter(models.DocumentReviewLog.user_id == student.id).first()
    assert log is not None
    assert log.document_type == "student_license"
    assert log.status == "pending"

    # 3. Fetch Pending Documents as Admin
    admin_headers = auth_headers("admin_doc@example.com")
    response = client.get("/api/v1/users/admin/documents/pending", headers=admin_headers)
    assert response.status_code == 200
    pending_logs = response.json()
    assert len(pending_logs) == 1
    assert pending_logs[0]["id"] == log.id

    # 4. Admin Approves Document
    response = client.post(
        f"/api/v1/users/admin/documents/{log.id}/verify",
        json={"action": "approve"},
        headers=admin_headers
    )
    assert response.status_code == 200
    assert response.json()["document_status"] == "approved"

    # Check Student Profile verified status restored
    db.refresh(student_profile)
    assert student_profile.license_status == "verified"
    assert student_profile.is_verified is True


def test_instructor_document_upload_and_verification_cycle(client, db, auth_headers):
    # Create Instructor & Admin
    instructor = models.User(email="inst_doc@example.com", full_name="Inst Doc", role="instructor", hashed_password="pwd")
    admin = models.User(email="admin_doc2@example.com", full_name="Admin Doc", role="admin", hashed_password="pwd")
    db.add(instructor)
    db.add(admin)
    db.commit()

    inst_profile = models.InstructorProfile(
        user_id=instructor.id,
        bio="Bio",
        hourly_rate=50.0,
        city="V",
        car_model="X",
        insurance_policy="INS-12345",
        certification_id="CERT-001"
    )
    db.add(inst_profile)
    db.commit()

    headers = auth_headers("inst_doc@example.com")
    admin_headers = auth_headers("admin_doc2@example.com")

    # Upload Instructor License
    file_data = {"file": ("license.jpg", BytesIO(b"license_data"), "image/jpeg")}
    client.post("/api/v1/users/me/upload-document?document_type=instructor_license", files=file_data, headers=headers)
    
    # Upload Insurance
    file_data = {"file": ("insurance.jpg", BytesIO(b"ins_data"), "image/jpeg")}
    client.post("/api/v1/users/me/upload-document?document_type=insurance", files=file_data, headers=headers)

    # Upload Certification
    file_data = {"file": ("cert.jpg", BytesIO(b"cert_data"), "image/jpeg")}
    client.post("/api/v1/users/me/upload-document?document_type=certification", files=file_data, headers=headers)

    # Refresh and check quarantine
    db.refresh(inst_profile)
    assert inst_profile.license_image_status == "submitted"
    assert inst_profile.insurance_image_status == "submitted"
    assert inst_profile.certification_image_status == "submitted"
    assert inst_profile.is_verified is False

    # Approve two, reject one to assert is_verified remains False on rejection
    pending_response = client.get("/api/v1/users/admin/documents/pending", headers=admin_headers)
    pending_list = pending_response.json()
    assert len(pending_list) == 3

    # Approve license and insurance, reject certification
    for item in pending_list:
        if item["document_type"] == "instructor_license":
            client.post(f"/api/v1/users/admin/documents/{item['id']}/verify", json={"action": "approve"}, headers=admin_headers)
        elif item["document_type"] == "insurance":
            client.post(f"/api/v1/users/admin/documents/{item['id']}/verify", json={"action": "approve"}, headers=admin_headers)
        elif item["document_type"] == "certification":
            client.post(
                f"/api/v1/users/admin/documents/{item['id']}/verify",
                json={"action": "reject", "rejection_reason": "Expired document"},
                headers=admin_headers
            )

    db.refresh(inst_profile)
    assert inst_profile.license_image_status == "verified"
    assert inst_profile.insurance_image_status == "verified"
    assert inst_profile.certification_image_status == "rejected"
    assert inst_profile.is_verified is False # Rejected item keeps verified False

    # Upload new Certification to fix it
    file_data = {"file": ("cert_new.jpg", BytesIO(b"cert_data_new"), "image/jpeg")}
    client.post("/api/v1/users/me/upload-document?document_type=certification", files=file_data, headers=headers)

    db.refresh(inst_profile)
    assert inst_profile.certification_image_status == "submitted"

    # Admin approves new certification
    pending_response = client.get("/api/v1/users/admin/documents/pending", headers=admin_headers)
    new_pending = pending_response.json()
    assert len(new_pending) == 1
    client.post(f"/api/v1/users/admin/documents/{new_pending[0]['id']}/verify", json={"action": "approve"}, headers=admin_headers)

    # Finally all verified!
    db.refresh(inst_profile)
    assert inst_profile.certification_image_status == "verified"
    assert inst_profile.is_verified is True
