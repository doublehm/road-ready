from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from typing import List
import json
from datetime import datetime
from app import models, schemas
from app.api import deps

router = APIRouter()

@router.get("/", response_model=List[schemas.LearningModule])
async def list_modules(
    current_user: models.User = Depends(deps.get_current_user),
    db: Session = Depends(deps.get_db)
):
    """
    List all learning modules with student's progress if user is a student.
    """
    modules = db.query(models.LearningModule).order_by(models.LearningModule.order).all()
    return modules


@router.get("/student-progress", response_model=List[schemas.StudentModuleProgress])
async def get_student_progress(
    current_user: models.User = Depends(deps.get_current_user),
    db: Session = Depends(deps.get_db)
):
    """
    Get current student's module progress.
    """
    if current_user.role != "student":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Only students can access module progress"
        )

    progress = db.query(models.StudentModuleProgress).filter(
        models.StudentModuleProgress.student_id == current_user.id
    ).all()

    # If no progress exists, create initial locked progress for all modules
    if not progress:
        modules = db.query(models.LearningModule).all()
        for module in modules:
            module_progress = models.StudentModuleProgress(
                student_id=current_user.id,
                module_id=module.id,
                status="locked",
                enrollment_type="none",
                package_purchased=False,
                hours_completed=0.0
            )
            db.add(module_progress)
        db.commit()
        db.refresh(current_user)

        # Re-fetch progress
        progress = db.query(models.StudentModuleProgress).filter(
            models.StudentModuleProgress.student_id == current_user.id
        ).all()

    return progress


@router.get("/{module_id}", response_model=schemas.LearningModule)
async def get_module(
    module_id: int,
    current_user: models.User = Depends(deps.get_current_user),
    db: Session = Depends(deps.get_db)
):
    """
    Get details of a specific module.
    """
    module = db.query(models.LearningModule).filter(
        models.LearningModule.id == module_id
    ).first()

    if not module:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Module not found"
        )

    return module


@router.post("/{module_id}/enroll")
async def enroll_in_module(
    module_id: int,
    enrollment_type: str,  # "package" or "hourly"
    current_user: models.User = Depends(deps.get_current_user),
    db: Session = Depends(deps.get_db)
):
    """
    Enroll a student in a module.

    Args:
        module_id: The module to enroll in
        enrollment_type: "package" (discounted) or "hourly" (pay per lesson)
    """
    if current_user.role != "student":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Only students can enroll in modules"
        )

    if enrollment_type not in ["package", "hourly"]:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="enrollment_type must be 'package' or 'hourly'"
        )

    # Check if module exists
    module = db.query(models.LearningModule).filter(
        models.LearningModule.id == module_id
    ).first()

    if not module:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Module not found"
        )

    # Get student's progress for this module
    progress = db.query(models.StudentModuleProgress).filter(
        models.StudentModuleProgress.student_id == current_user.id,
        models.StudentModuleProgress.module_id == module_id
    ).first()

    if not progress:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Module not unlocked. Complete prerequisites first."
        )

    # Check if module is unlocked
    if progress.status == "locked":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Module is locked. Complete prerequisites first."
        )

    # Check if already enrolled
    if progress.enrollment_type != "none":
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Already enrolled in this module as {progress.enrollment_type}"
        )

    # Update progress
    progress.enrollment_type = enrollment_type
    progress.package_purchased = (enrollment_type == "package")
    progress.status = "in_progress"

    if not progress.unlocked_at:
        progress.unlocked_at = datetime.now().isoformat()

    db.commit()
    db.refresh(progress)

    # Update student's current module
    student_profile = db.query(models.StudentProfile).filter(
        models.StudentProfile.user_id == current_user.id
    ).first()

    if student_profile and not student_profile.current_module_id:
        student_profile.current_module_id = module_id
        db.commit()

    return {
        "message": f"Successfully enrolled in {module.name} as {enrollment_type}",
        "module": module,
        "progress": progress
    }
