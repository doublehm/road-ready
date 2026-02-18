from fastapi import APIRouter, Depends, HTTPException, status, BackgroundTasks
from sqlalchemy.orm import Session
from typing import List
import json
from datetime import datetime
from app import models, schemas
from app.api import deps
from app.services.diagnostic_evaluator import DiagnosticEvaluator
from app.services.speed_limit_service import get_speed_limit

router = APIRouter()

@router.get("/speed-limit")
async def get_speed_limit_for_location(
    lat: float,
    lon: float,
    current_user: models.User = Depends(deps.get_current_user)
):
    """
    Get the speed limit for the given GPS coordinates.
    Uses Overpass API with BC provincial defaults as fallback.
    """
    result = get_speed_limit(lat, lon)
    return result


@router.post("/", response_model=schemas.DiagnosticRide)
async def create_diagnostic_ride(
    ride: schemas.DiagnosticRideCreate,
    current_user: models.User = Depends(deps.get_current_user),
    db: Session = Depends(deps.get_db)
):
    """
    Create a new diagnostic ride.

    Args:
        ride: Diagnostic ride data including ride_type, instructor_id, sensor data
    """
    if current_user.role != "student":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Only students can create diagnostic rides"
        )

    # Validate ride type
    if ride.ride_type not in ["parent_supervised", "instructor_supervised"]:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="ride_type must be 'parent_supervised' or 'instructor_supervised'"
        )

    # If instructor supervised, verify instructor exists
    if ride.ride_type == "instructor_supervised" and ride.instructor_id:
        instructor = db.query(models.InstructorProfile).filter(
            models.InstructorProfile.id == ride.instructor_id
        ).first()
        if not instructor:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Instructor not found"
            )

    # Create diagnostic ride
    db_ride = models.DiagnosticRide(
        student_id=current_user.id,
        ride_type=ride.ride_type,
        instructor_id=ride.instructor_id,
        booking_id=ride.booking_id,
        start_time=ride.start_time,
        end_time=ride.end_time,
        duration_minutes=ride.duration_minutes,
        distance_km=ride.distance_km,
        route_coords=ride.route_coords,
        acceleration_data=ride.acceleration_data,
        rotation_data=ride.rotation_data,
        speed_data=ride.speed_data,
        speed_limit_data=ride.speed_limit_data,
        heading_data=ride.heading_data,
        status="pending",
        created_at=datetime.now().isoformat()
    )

    db.add(db_ride)
    db.commit()
    db.refresh(db_ride)

    return db_ride


@router.post("/live-evaluate")
async def live_evaluate(
    request: schemas.LiveEvaluationRequest,
    current_user: models.User = Depends(deps.get_current_user)
):
    """
    Evaluate a window of sensor data for real-time feedback.
    Returns any detected events (mistakes) in the current window.
    """
    if current_user.role != "student":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Only students can access live evaluation"
        )

    evaluator = DiagnosticEvaluator()
    
    # Convert SensorDataPoints to dicts for evaluator
    acceleration_data = [p.dict() for p in request.acceleration_window]
    speed_data = [p.dict() for p in request.speed_window]
    rotation_data = [p.dict() for p in request.rotation_window]

    # Run sub-evaluations
    _, braking_feedback = evaluator._evaluate_braking(acceleration_data, speed_data)
    _, speed_feedback = evaluator._evaluate_speed(speed_data, 1) # dummy duration
    _, cornering_feedback = evaluator._evaluate_cornering(acceleration_data, rotation_data)

    # Collect all events detected in this window
    events = []
    events.extend(braking_feedback.get('events', []))
    events.extend(speed_feedback.get('events', []))
    events.extend(cornering_feedback.get('events', []))

    # Remove duplicates if any (though unlikely in small windows)
    # Sort by timestamp
    events.sort(key=lambda x: x.get('timestamp') or 0)

    return {
        "events": events,
        "timestamp": datetime.now().isoformat()
    }


@router.get("/", response_model=List[schemas.DiagnosticRide])
async def list_diagnostic_rides(
    current_user: models.User = Depends(deps.get_current_user),
    db: Session = Depends(deps.get_db)
):
    """
    List all diagnostic rides for the current student.
    Instructors can see rides they supervised.
    """
    if current_user.role == "student":
        rides = db.query(models.DiagnosticRide).filter(
            models.DiagnosticRide.student_id == current_user.id
        ).order_by(models.DiagnosticRide.created_at.desc()).all()
    elif current_user.role == "instructor":
        instructor_profile = db.query(models.InstructorProfile).filter(
            models.InstructorProfile.user_id == current_user.id
        ).first()
        if not instructor_profile:
            return []
        rides = db.query(models.DiagnosticRide).filter(
            models.DiagnosticRide.instructor_id == instructor_profile.id
        ).order_by(models.DiagnosticRide.created_at.desc()).all()
    else:
        rides = []

    return rides


@router.get("/{ride_id}", response_model=schemas.DiagnosticRide)
async def get_diagnostic_ride(
    ride_id: int,
    current_user: models.User = Depends(deps.get_current_user),
    db: Session = Depends(deps.get_db)
):
    """
    Get details of a specific diagnostic ride.
    """
    ride = db.query(models.DiagnosticRide).filter(
        models.DiagnosticRide.id == ride_id
    ).first()

    if not ride:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Diagnostic ride not found"
        )

    # Check permissions
    if current_user.role == "student":
        if ride.student_id != current_user.id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="You can only view your own diagnostic rides"
            )
    elif current_user.role == "instructor":
        instructor_profile = db.query(models.InstructorProfile).filter(
            models.InstructorProfile.user_id == current_user.id
        ).first()
        if not instructor_profile or ride.instructor_id != instructor_profile.id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="You can only view diagnostic rides you supervised"
            )

    return ride


@router.post("/{ride_id}/evaluate")
async def evaluate_diagnostic_ride(
    ride_id: int,
    background_tasks: BackgroundTasks,
    current_user: models.User = Depends(deps.get_current_user),
    db: Session = Depends(deps.get_db)
):
    """
    Trigger evaluation of a diagnostic ride.
    This processes sensor data and generates scores.
    """
    ride = db.query(models.DiagnosticRide).filter(
        models.DiagnosticRide.id == ride_id
    ).first()

    if not ride:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Diagnostic ride not found"
        )

    # Check permissions (student who created it or supervising instructor)
    if current_user.role == "student":
        if ride.student_id != current_user.id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="You can only evaluate your own diagnostic rides"
            )
    elif current_user.role == "instructor":
        instructor_profile = db.query(models.InstructorProfile).filter(
            models.InstructorProfile.user_id == current_user.id
        ).first()
        if not instructor_profile or ride.instructor_id != instructor_profile.id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="You can only evaluate diagnostic rides you supervised"
            )

    # Check if already evaluated
    if ride.status == "completed":
        return {
            "message": "Ride already evaluated",
            "ride": ride
        }

    # Update status to evaluating
    ride.status = "evaluating"
    db.commit()

    # Run evaluation
    try:
        evaluator = DiagnosticEvaluator()
        ride_data = {
            'acceleration_data': ride.acceleration_data or '[]',
            'rotation_data': ride.rotation_data or '[]',
            'speed_data': ride.speed_data or '[]',
            'speed_limit_data': ride.speed_limit_data or '[]',
            'heading_data': ride.heading_data or '[]',
            'duration_minutes': ride.duration_minutes or 0,
            'distance_km': ride.distance_km or 0
        }

        evaluation_result = evaluator.evaluate(ride_data)

        # Update ride with evaluation results
        ride.braking_score = evaluation_result['braking_score']
        ride.speed_score = evaluation_result['speed_score']
        ride.cornering_score = evaluation_result['cornering_score']
        ride.overall_score = evaluation_result['overall_score']
        ride.passed = evaluation_result['passed']
        ride.evaluation_result = evaluation_result['evaluation_result']
        
        # Extract criteria_results from evaluation_result for easier access
        eval_dict = json.loads(ride.evaluation_result)
        ride.criteria_results = json.dumps(eval_dict.get('overall', {}))
        
        ride.status = "completed"
        ride.evaluated_at = datetime.now().isoformat()

        db.commit()
        db.refresh(ride)

        # If passed, unlock Advanced module and mark Basics as skipped
        if ride.passed:
            student_profile = db.query(models.StudentProfile).filter(
                models.StudentProfile.user_id == ride.student_id
            ).first()

            if student_profile:
                student_profile.diagnostic_completed = True
                student_profile.diagnostic_ride_id = ride.id
                student_profile.basics_skipped = True

                # Get Advanced module (assuming order 2) and Basics module (order 1)
                basics_module = db.query(models.LearningModule).filter(
                    models.LearningModule.order == 1
                ).first()

                advanced_module = db.query(models.LearningModule).filter(
                    models.LearningModule.order == 2
                ).first()

                if advanced_module:
                    # Unlock Advanced module
                    advanced_progress = db.query(models.StudentModuleProgress).filter(
                        models.StudentModuleProgress.student_id == ride.student_id,
                        models.StudentModuleProgress.module_id == advanced_module.id
                    ).first()

                    if advanced_progress:
                        advanced_progress.status = "unlocked"
                        advanced_progress.unlocked_at = datetime.now().isoformat()

                # Keep Basics locked (skipped)
                if basics_module:
                    basics_progress = db.query(models.StudentModuleProgress).filter(
                        models.StudentModuleProgress.student_id == ride.student_id,
                        models.StudentModuleProgress.module_id == basics_module.id
                    ).first()

                    if basics_progress:
                        basics_progress.status = "locked"

                db.commit()
        else:
            # If failed, unlock Basics module
            basics_module = db.query(models.LearningModule).filter(
                models.LearningModule.order == 1
            ).first()

            if basics_module:
                basics_progress = db.query(models.StudentModuleProgress).filter(
                    models.StudentModuleProgress.student_id == ride.student_id,
                    models.StudentModuleProgress.module_id == basics_module.id
                ).first()

                if basics_progress:
                    basics_progress.status = "unlocked"
                    basics_progress.unlocked_at = datetime.now().isoformat()
                    db.commit()

        return {
            "message": "Evaluation completed successfully",
            "ride": ride,
            "passed": ride.passed
        }

    except Exception as e:
        ride.status = "pending"
        db.commit()
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Evaluation failed: {str(e)}"
        )


@router.post("/{ride_id}/instructor-review")
async def instructor_review(
    ride_id: int,
    review: schemas.InstructorReview,
    current_user: models.User = Depends(deps.get_current_user),
    db: Session = Depends(deps.get_db)
):
    """
    Allow instructor to review and override diagnostic ride evaluation.
    """
    if current_user.role != "instructor":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Only instructors can review diagnostic rides"
        )

    instructor_profile = db.query(models.InstructorProfile).filter(
        models.InstructorProfile.user_id == current_user.id
    ).first()

    if not instructor_profile:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Instructor profile not found"
        )

    ride = db.query(models.DiagnosticRide).filter(
        models.DiagnosticRide.id == ride_id
    ).first()

    if not ride:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Diagnostic ride not found"
        )

    # Verify this instructor supervised the ride
    if ride.instructor_id != instructor_profile.id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="You can only review rides you supervised"
        )

    # Update ride with instructor notes and overrides
    ride.evaluator_notes = review.evaluator_notes

    # Apply overrides if provided
    if review.override_passed is not None:
        ride.passed = review.override_passed
        ride.instructor_override = True

    if review.override_braking_score is not None:
        ride.braking_score = review.override_braking_score
        ride.instructor_override = True

    if review.override_speed_score is not None:
        ride.speed_score = review.override_speed_score
        ride.instructor_override = True

    if review.override_cornering_score is not None:
        ride.cornering_score = review.override_cornering_score
        ride.instructor_override = True

    # Recalculate overall score if individual scores were overridden
    if any([
        review.override_braking_score,
        review.override_speed_score,
        review.override_cornering_score
    ]):
        evaluator = DiagnosticEvaluator()
        ride.overall_score = evaluator._calculate_overall(
            ride.braking_score or 0,
            ride.speed_score or 0,
            ride.cornering_score or 0
        )

    db.commit()
    db.refresh(ride)

    # Update module unlocks based on new pass/fail status
    if ride.passed:
        student_profile = db.query(models.StudentProfile).filter(
            models.StudentProfile.user_id == ride.student_id
        ).first()

        if student_profile:
            student_profile.diagnostic_completed = True
            student_profile.diagnostic_ride_id = ride.id
            student_profile.basics_skipped = True

            # Unlock Advanced module
            advanced_module = db.query(models.LearningModule).filter(
                models.LearningModule.order == 2
            ).first()

            if advanced_module:
                advanced_progress = db.query(models.StudentModuleProgress).filter(
                    models.StudentModuleProgress.student_id == ride.student_id,
                    models.StudentModuleProgress.module_id == advanced_module.id
                ).first()

                if advanced_progress:
                    advanced_progress.status = "unlocked"
                    advanced_progress.unlocked_at = datetime.now().isoformat()

            db.commit()

    return {
        "message": "Instructor review completed",
        "ride": ride
    }


@router.get("/progress-trends")
async def get_progress_trends(
    student_id: int = None,
    current_user: models.User = Depends(deps.get_current_user),
    db: Session = Depends(deps.get_db)
):
    """
    Get progress trends across all diagnostic rides.
    Students see their own trends. Instructors can view a student's trends.
    """
    target_student_id = None

    if current_user.role == "student":
        target_student_id = current_user.id
    elif current_user.role == "instructor" and student_id:
        # Verify instructor supervised at least one ride for this student
        instructor_profile = db.query(models.InstructorProfile).filter(
            models.InstructorProfile.user_id == current_user.id
        ).first()
        if not instructor_profile:
            raise HTTPException(status_code=404, detail="Instructor profile not found")

        supervised = db.query(models.DiagnosticRide).filter(
            models.DiagnosticRide.student_id == student_id,
            models.DiagnosticRide.instructor_id == instructor_profile.id
        ).first()
        if not supervised:
            raise HTTPException(
                status_code=403,
                detail="You can only view trends for students you have supervised"
            )
        target_student_id = student_id
    else:
        raise HTTPException(status_code=400, detail="Student ID required for instructors")

    rides = db.query(models.DiagnosticRide).filter(
        models.DiagnosticRide.student_id == target_student_id,
        models.DiagnosticRide.status == "completed"
    ).order_by(models.DiagnosticRide.created_at.asc()).all()

    if not rides:
        return {
            "total_rides": 0,
            "pass_rate": 0,
            "average_overall": 0,
            "best_overall": 0,
            "score_trend": [],
            "improvement_areas": [],
            "total_distance_km": 0,
            "total_time_hours": 0,
        }

    total_rides = len(rides)
    passed_count = sum(1 for r in rides if r.passed)
    overall_scores = [r.overall_score for r in rides if r.overall_score is not None]
    braking_scores = [r.braking_score for r in rides if r.braking_score is not None]
    speed_scores = [r.speed_score for r in rides if r.speed_score is not None]
    cornering_scores = [r.cornering_score for r in rides if r.cornering_score is not None]

    avg_overall = sum(overall_scores) / len(overall_scores) if overall_scores else 0
    best_overall = max(overall_scores) if overall_scores else 0

    total_distance = sum(r.distance_km or 0 for r in rides)
    total_minutes = sum(r.duration_minutes or 0 for r in rides)

    # Identify weakest area from latest ride
    improvement_areas = []
    if rides:
        latest = rides[-1]
        scores = {
            'braking': latest.braking_score or 0,
            'speed_control': latest.speed_score or 0,
            'cornering': latest.cornering_score or 0,
        }
        weakest = min(scores, key=scores.get)
        if scores[weakest] < 75:
            improvement_areas.append(weakest)

    score_trend = []
    for r in rides:
        score_trend.append({
            "ride_id": r.id,
            "date": r.created_at,
            "overall": round(r.overall_score or 0, 1),
            "braking": round(r.braking_score or 0, 1),
            "speed": round(r.speed_score or 0, 1),
            "cornering": round(r.cornering_score or 0, 1),
            "passed": r.passed,
        })

    return {
        "total_rides": total_rides,
        "pass_rate": round(passed_count / total_rides * 100, 1) if total_rides > 0 else 0,
        "average_overall": round(avg_overall, 1),
        "best_overall": round(best_overall, 1),
        "score_trend": score_trend,
        "improvement_areas": improvement_areas,
        "total_distance_km": round(total_distance, 1),
        "total_time_hours": round(total_minutes / 60, 1),
    }


@router.get("/student/{student_id}/rides", response_model=List[schemas.DiagnosticRide])
async def get_student_rides(
    student_id: int,
    current_user: models.User = Depends(deps.get_current_user),
    db: Session = Depends(deps.get_db)
):
    """
    Get all diagnostic rides for a specific student (instructor access).
    """
    if current_user.role != "instructor":
        raise HTTPException(status_code=403, detail="Only instructors can view student rides")

    instructor_profile = db.query(models.InstructorProfile).filter(
        models.InstructorProfile.user_id == current_user.id
    ).first()
    if not instructor_profile:
        raise HTTPException(status_code=404, detail="Instructor profile not found")

    # Verify instructor supervised at least one ride for this student
    supervised = db.query(models.DiagnosticRide).filter(
        models.DiagnosticRide.student_id == student_id,
        models.DiagnosticRide.instructor_id == instructor_profile.id
    ).first()
    if not supervised:
        raise HTTPException(
            status_code=403,
            detail="You can only view rides for students you have supervised"
        )

    rides = db.query(models.DiagnosticRide).filter(
        models.DiagnosticRide.student_id == student_id
    ).order_by(models.DiagnosticRide.created_at.desc()).all()

    return rides
