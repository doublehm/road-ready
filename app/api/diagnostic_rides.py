from fastapi import APIRouter, Depends, HTTPException, status, BackgroundTasks, Query, WebSocket, WebSocketDisconnect
from pydantic import BaseModel
from sqlalchemy.orm import Session, joinedload
from typing import List, Optional, Dict
import json
import logging
from datetime import datetime
from app import models, schemas, database
from app.api import deps

logger = logging.getLogger(__name__)
from app.services.nosql_repo import NoSQLRepository
from app.services.diagnostic_evaluator import DiagnosticEvaluator
from app.services.speed_limit_service import get_speed_limit

router = APIRouter()
nosql_repo = NoSQLRepository()

class ConnectionManager:
    def __init__(self):
        # Dictionary mapping ride_id to list of active websockets
        # {ride_id: {"mobile": [ws], "web": [ws, ws]}}
        self.active_connections: Dict[str, Dict[str, List[WebSocket]]] = {}

    async def connect(self, websocket: WebSocket, ride_id: str, client_type: str):
        await websocket.accept()
        if ride_id not in self.active_connections:
            self.active_connections[ride_id] = {"mobile": [], "web": []}
        
        if client_type not in self.active_connections[ride_id]:
            self.active_connections[ride_id][client_type] = []
            
        self.active_connections[ride_id][client_type].append(websocket)

    def disconnect(self, websocket: WebSocket, ride_id: str, client_type: str):
        if ride_id in self.active_connections:
            if client_type in self.active_connections[ride_id]:
                if websocket in self.active_connections[ride_id][client_type]:
                    self.active_connections[ride_id][client_type].remove(websocket)
            
            # Cleanup if no connections left for this ride
            if not self.active_connections[ride_id]["mobile"] and not self.active_connections[ride_id]["web"]:
                del self.active_connections[ride_id]

    async def broadcast(self, message: dict, ride_id: str, sender_type: str):
        if ride_id in self.active_connections:
            # Broadcast to all clients for this ride (web and mobile)
            # This ensures even the mobile client gets an ack if desired, 
            # and all web dashboards are in sync.
            for client_type in ["mobile", "web"]:
                dead_connections = []
                for connection in self.active_connections[ride_id][client_type]:
                    try:
                        await connection.send_json(message)
                    except Exception:
                        dead_connections.append(connection)
                
                for dead in dead_connections:
                    self.disconnect(dead, ride_id, client_type)

    async def broadcast_to_all(self, message: dict, ride_id: str):
        if ride_id in self.active_connections:
            for client_type in ["mobile", "web"]:
                dead_connections = []
                for connection in self.active_connections[ride_id][client_type]:
                    try:
                        await connection.send_json(message)
                    except Exception:
                        dead_connections.append(connection)
                
                for dead in dead_connections:
                    self.disconnect(dead, ride_id, client_type)

    async def persist_data(self, ride_id: str, message: dict):
        try:
            msg_type = message.get("type")
            data = message.get("data", {})

            if msg_type == "telemetry":
                # Handle single point or list of points
                points = data if isinstance(data, list) else [data]
                
                # Cleanup points for NoSQL storage (adding metadata)
                for p in points:
                    if "timestamp" not in p or p["timestamp"] is None:
                        p["timestamp"] = datetime.now().timestamp()
                    else:
                        try: p["timestamp"] = float(p["timestamp"])
                        except: p["timestamp"] = datetime.now().timestamp()

                await nosql_repo.save_telemetry_chunk(ride_id, points)

            elif msg_type == "event":
                await nosql_repo.save_event(ride_id, data)
                
        except Exception as e:
            logger.error("persist_data failed for ride %s: %s", ride_id, e)

manager = ConnectionManager()


@router.post("/{ride_id}/telemetry")
async def save_telemetry(
    ride_id: int,
    request: List[schemas.SensorDataPoint],
    current_user: models.User = Depends(deps.get_current_user),
    db: Session = Depends(deps.get_db)
):
    """
    HTTP endpoint for persisting chunks of telemetry data.
    Acts as a reliable fallback if the WebSocket is disconnected.
    """
    # Verify ride ownership
    ride = db.query(models.DiagnosticRide).filter(models.DiagnosticRide.id == ride_id).first()
    if not ride:
        raise HTTPException(status_code=404, detail="Ride not found")
    
    if current_user.role == "student" and ride.student_id != current_user.id:
        raise HTTPException(status_code=403, detail="Not authorized")
    
    # Use the same persistence logic as WebSocket
    await manager.persist_data(str(ride_id), {
        "type": "telemetry",
        "data": [p.dict() for p in request]
    })
    
    return {"status": "persisted", "count": len(request)}


@router.get("/{ride_id}/telemetry")
async def get_ride_telemetry(
    ride_id: int,
    current_user: models.User = Depends(deps.get_current_user),
    db: Session = Depends(deps.get_db)
):
    """
    Retrieve all telemetry points for a specific ride from NoSQL.
    """
    # Verify ride access
    ride = db.query(models.DiagnosticRide).filter(models.DiagnosticRide.id == ride_id).first()
    if not ride:
        raise HTTPException(status_code=404, detail="Ride not found")
    
    if current_user.role == "student" and ride.student_id != current_user.id:
        raise HTTPException(status_code=403, detail="Not authorized")
    
    # Check permissions for instructor
    if current_user.role == "instructor":
        instructor_profile = db.query(models.InstructorProfile).filter(models.InstructorProfile.user_id == current_user.id).first()
        if not instructor_profile:
            raise HTTPException(status_code=404, detail="Instructor profile not found")
        if ride.instructor_id != instructor_profile.id:
            # Check if they have a booking
            booking = db.query(models.BookingRequest).filter(
                models.BookingRequest.instructor_id == instructor_profile.id,
                models.BookingRequest.student_id == ride.student_id
            ).first()
            if not booking:
                raise HTTPException(status_code=403, detail="Not authorized")

    points = await nosql_repo.get_ride_telemetry(str(ride_id))
    
    # Remove _id from MongoDB documents for JSON serialization
    for p in points:
        if "_id" in p:
            p["_id"] = str(p["_id"])
            
    return points


@router.websocket("/live-ride-stream")
async def websocket_endpoint(websocket: WebSocket):
    ride_id = websocket.query_params.get("ride_id")
    client_type = websocket.query_params.get("client_type", "web")
    
    if not ride_id:
        await websocket.accept()
        await websocket.close(code=1008)
        return

    await manager.connect(websocket, ride_id, client_type)
    try:
        while True:
            data = await websocket.receive_json()
            
            # Simple ping-pong
            if data.get("type") == "ping":
                await websocket.send_json({"type": "pong"})
                continue

            # Persist data from mobile
            if client_type == "mobile" and data.get("type") in ["telemetry", "event"]:
                await manager.persist_data(ride_id, data)

            # Broadcast message to others in the same ride
            await manager.broadcast(data, ride_id, client_type)
            
    except WebSocketDisconnect:
        manager.disconnect(websocket, ride_id, client_type)
    except Exception as e:
        logger.error("WebSocket error for ride %s (client=%s): %s", ride_id, client_type, e)
        manager.disconnect(websocket, ride_id, client_type)


@router.get("/speed-limit")
async def get_speed_limit_for_location(
    lat: float,
    lon: float,
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
    # Determine student_id based on who creates the ride
    if current_user.role == "student":
        student_id = current_user.id
    elif current_user.role == "instructor":
        # Instructors can create rides for booked students
        if not ride.booking_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Instructors must provide a booking_id to create rides"
            )
        booking = db.query(models.BookingRequest).filter(
            models.BookingRequest.id == ride.booking_id
        ).first()
        if not booking:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Booking not found"
            )
        instructor_profile = db.query(models.InstructorProfile).filter(
            models.InstructorProfile.user_id == current_user.id
        ).first()
        if not instructor_profile or booking.instructor_id != instructor_profile.id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Not authorized for this booking"
            )
        if booking.status != "accepted":
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Booking must be accepted to start a ride"
            )
        student_id = booking.student_id
        ride.instructor_id = instructor_profile.id
    else:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Not authorized to create diagnostic rides"
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
        student_id=student_id,
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
        evaluator_notes=ride.evaluator_notes,
        human_feedback=ride.human_feedback,
        status="pending",
        created_at=datetime.now().isoformat()
    )

    db.add(db_ride)
    db.commit()
    db.refresh(db_ride)

    return db_ride


@router.put("/{ride_id}", response_model=schemas.DiagnosticRide)
async def update_diagnostic_ride(
    ride_id: int,
    ride_update: schemas.DiagnosticRideCreate,
    current_user: models.User = Depends(deps.get_current_user),
    db: Session = Depends(deps.get_db)
):
    """
    Update an existing diagnostic ride with final data.
    """
    db_ride = db.query(models.DiagnosticRide).filter(models.DiagnosticRide.id == ride_id).first()
    if not db_ride:
        raise HTTPException(status_code=404, detail="Ride not found")

    if current_user.role == "student" and db_ride.student_id != current_user.id:
        raise HTTPException(status_code=403, detail="Not authorized")
    elif current_user.role == "instructor":
        instructor_profile = db.query(models.InstructorProfile).filter(
            models.InstructorProfile.user_id == current_user.id
        ).first()
        if not instructor_profile or db_ride.instructor_id != instructor_profile.id:
            raise HTTPException(status_code=403, detail="Not authorized")

    update_data = ride_update.dict(exclude_unset=True)
    for key, value in update_data.items():
        setattr(db_ride, key, value)

    db.commit()
    db.refresh(db_ride)
    return db_ride


class HumanFeedbackRequest(BaseModel):
    feedback: str  # JSON string: [{code, label, category, count, timestamps}]


@router.post("/{ride_id}/feedback")
async def submit_human_feedback(
    ride_id: int,
    request: HumanFeedbackRequest,
    current_user: models.User = Depends(deps.get_current_user),
    db: Session = Depends(deps.get_db)
):
    """
    Submit or update human feedback for a diagnostic ride.
    Can be called by the student (parent-supervised) or supervising instructor.
    """
    ride = db.query(models.DiagnosticRide).filter(
        models.DiagnosticRide.id == ride_id
    ).first()

    if not ride:
        raise HTTPException(status_code=404, detail="Ride not found")

    if current_user.role == "student" and ride.student_id != current_user.id:
        raise HTTPException(status_code=403, detail="Not authorized")
    elif current_user.role == "instructor":
        instructor_profile = db.query(models.InstructorProfile).filter(
            models.InstructorProfile.user_id == current_user.id
        ).first()
        if not instructor_profile or ride.instructor_id != instructor_profile.id:
            raise HTTPException(status_code=403, detail="Not authorized")

    ride.human_feedback = request.feedback
    db.commit()
    db.refresh(ride)
    return {"message": "Feedback saved", "ride_id": ride.id}


@router.post("/live-evaluate")
async def live_evaluate(
    request: schemas.LiveEvaluationRequest,
):
    """
    Evaluate a window of sensor data for real-time feedback.
    Returns any detected events (mistakes) in the current window.
    """
    evaluator = DiagnosticEvaluator()
    
    # Convert SensorDataPoints to dicts for evaluator
    acceleration_data = [p.dict() for p in request.acceleration_window]
    speed_data = [p.dict() for p in request.speed_window]
    rotation_data = [p.dict() for p in request.rotation_window]

    # Pre-filter acceleration to remove noise spikes (potholes, vibration)
    acceleration_data = evaluator._median_filter(acceleration_data)

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


@router.post("/evaluate-chunk")
async def evaluate_chunk(
    request: schemas.ChunkEvaluationRequest,
):
    """
    Evaluates a specific chunk of sensor data from the mobile app.
    Used for real-time coaching feedback.
    """
    evaluator = DiagnosticEvaluator()
    
    # Format data for evaluator
    # Evaluator expects list of dicts with 'timestamp', 'x', 'y', 'z', 'latitude', 'longitude'
    accel_data = []
    lat = request.location.get('latitude') if request.location else None
    lon = request.location.get('longitude') if request.location else None

    for p in request.acceleration_data:
        accel_data.append({
            'timestamp': p.get('timestamp') or request.timestamp,
            'x': p.get('x', 0),
            'y': p.get('y', 0),
            'z': p.get('z', 0),
            'latitude': lat,
            'longitude': lon
        })
    
    speed_data = [{
        'timestamp': request.timestamp,
        'speed': request.speed,
        'latitude': lat,
        'longitude': lon
    }]

    # Pre-filter
    accel_data = evaluator._median_filter(accel_data)

    # Evaluate
    speed_limit_data = []
    if request.current_speed_limit:
        speed_limit_data = [{
            'timestamp': request.timestamp,
            'speed_limit': request.current_speed_limit,
            'zone_type': request.zone_type or "regular"
        }]

    _, braking_feedback = evaluator._evaluate_braking(accel_data, speed_data)
    _, speed_feedback = evaluator._evaluate_speed(speed_data, 1, speed_limit_data=speed_limit_data)
    _, cornering_feedback = evaluator._evaluate_cornering(accel_data, [], speed_data=speed_data)

    events = []
    events.extend(braking_feedback.get('events', []))
    events.extend(speed_feedback.get('events', []))
    events.extend(cornering_feedback.get('events', []))

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
    query = db.query(models.DiagnosticRide).options(
        joinedload(models.DiagnosticRide.student),
        joinedload(models.DiagnosticRide.instructor)
    )

    if current_user.role == "student":
        rides = query.filter(
            models.DiagnosticRide.student_id == current_user.id
        ).order_by(models.DiagnosticRide.created_at.desc()).all()
    elif current_user.role == "instructor":
        instructor_profile = db.query(models.InstructorProfile).filter(
            models.InstructorProfile.user_id == current_user.id
        ).first()
        if not instructor_profile:
            return []
        rides = query.filter(
            models.DiagnosticRide.instructor_id == instructor_profile.id
        ).order_by(models.DiagnosticRide.created_at.desc()).all()
    else:
        rides = []

    return rides


@router.get("/progress-trends")
async def get_progress_trends(
    student_id: Optional[int] = Query(None),
    current_user: models.User = Depends(deps.get_current_user),
    db: Session = Depends(deps.get_db)
):
    """
    Get progress trends across diagnostic rides.
    Students see their own trends.
    Instructors see a specific student's trends OR their aggregate supervision trends.
    """
    query = db.query(models.DiagnosticRide).filter(
        models.DiagnosticRide.status == "completed"
    )

    if current_user.role == "student":
        query = query.filter(models.DiagnosticRide.student_id == current_user.id)
    elif current_user.role == "instructor":
        instructor_profile = db.query(models.InstructorProfile).filter(
            models.InstructorProfile.user_id == current_user.id
        ).first()
        if not instructor_profile:
            raise HTTPException(status_code=404, detail="Instructor profile not found")
        
        if student_id:
            # Verify instructor supervised at least one ride for this student
            supervised = db.query(models.DiagnosticRide).filter(
                models.DiagnosticRide.student_id == student_id,
                models.DiagnosticRide.instructor_id == instructor_profile.id
            ).first()
            if not supervised:
                raise HTTPException(
                    status_code=403,
                    detail="You can only view trends for students you have supervised"
                )
            query = query.filter(models.DiagnosticRide.student_id == student_id)
        else:
            # Aggregate trends for all rides this instructor supervised
            query = query.filter(models.DiagnosticRide.instructor_id == instructor_profile.id)
    else:
        raise HTTPException(status_code=400, detail="Invalid user role")

    rides = query.order_by(models.DiagnosticRide.created_at.asc()).all()

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
    avg_duration = total_minutes / total_rides if total_rides > 0 else 0

    # Category averages
    avg_braking = sum(braking_scores) / len(braking_scores) if braking_scores else 0
    avg_speed = sum(speed_scores) / len(speed_scores) if speed_scores else 0
    avg_cornering = sum(cornering_scores) / len(cornering_scores) if cornering_scores else 0

    # Identify weakest areas (analytical approach: scores < 75 in category averages)
    improvement_areas = []
    if avg_braking < 75: improvement_areas.append('braking')
    if avg_speed < 75: improvement_areas.append('speed control')
    if avg_cornering < 75: improvement_areas.append('cornering')

    recent_ride = rides[-1]
    
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
        "recent_score": round(recent_ride.overall_score or 0, 1),
        "avg_duration_minutes": round(avg_duration, 1),
        "category_averages": {
            "braking": round(avg_braking, 1),
            "speed": round(avg_speed, 1),
            "cornering": round(avg_cornering, 1)
        },
        "score_trend": score_trend,
        "improvement_areas": improvement_areas,
        "total_distance_km": round(total_distance, 1),
        "total_time_hours": round(total_minutes / 60, 1),
    }


@router.get("/{ride_id}", response_model=schemas.DiagnosticRide)
async def get_diagnostic_ride(
    ride_id: int,
    current_user: models.User = Depends(deps.get_current_user),
    db: Session = Depends(deps.get_db)
):
    """
    Get details of a specific diagnostic ride.
    """
    ride = db.query(models.DiagnosticRide).options(
        joinedload(models.DiagnosticRide.student),
        joinedload(models.DiagnosticRide.instructor)
    ).filter(
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
        if not instructor_profile:
             raise HTTPException(status_code=404, detail="Instructor profile not found")

        # Allow access if they supervised this ride OR if they have a booking with this student
        has_access = ride.instructor_id == instructor_profile.id
        if not has_access:
            booking = db.query(models.BookingRequest).filter(
                models.BookingRequest.instructor_id == instructor_profile.id,
                models.BookingRequest.student_id == ride.student_id
            ).first()
            if booking:
                has_access = True

        if not has_access:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="You can only view diagnostic rides for students you are training"
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
        
        # We pass basic metadata to evaluator, it will fetch granular telemetry from NoSQL
        ride_data = {
            'speed_limit_data': ride.speed_limit_data or '[]',
            'heading_data': ride.heading_data or '[]',
            'human_feedback': ride.human_feedback or '[]',
            'duration_minutes': ride.duration_minutes or 0,
            'distance_km': ride.distance_km or 0,
            'acceleration_data': ride.acceleration_data or '[]', # Fallback blobs
            'rotation_data': ride.rotation_data or '[]',
            'speed_data': ride.speed_data or '[]'
        }

        evaluation_result = await evaluator.evaluate(str(ride_id), ride_data)

        # Update ride with evaluation results
        ride.braking_score = evaluation_result['braking_score']
        ride.speed_score = evaluation_result['speed_score']
        ride.cornering_score = evaluation_result['cornering_score']
        ride.smoothness_score = evaluation_result['smoothness_score']
        ride.overall_score = evaluation_result['overall_score']
        ride.passed = evaluation_result['passed']
        ride.evaluation_result = evaluation_result['evaluation_result']

        # Persist processed sensor data back to SQL so the results screen can
        # display the SpeedGraph and RouteReplayMap without needing MongoDB.
        if evaluation_result.get('speed_data'):
            ride.speed_data = json.dumps(evaluation_result['speed_data'])
        if evaluation_result.get('route_coords'):
            ride.route_coords = json.dumps(evaluation_result['route_coords'])
        
        # Extract criteria_results from evaluation_result for easier access
        eval_dict = json.loads(ride.evaluation_result)
        ride.criteria_results = json.dumps(eval_dict.get('overall', {}))
        
        ride.status = "completed"
        ride.evaluated_at = datetime.now().isoformat()

        db.commit()
        db.refresh(ride)

        # Broadcast completion to web dashboard
        await manager.broadcast_to_all({"type": "system", "data": {"event": "ride_completed"}}, str(ride_id))

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
        ride.status = "failed"
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

    return db.query(models.DiagnosticRide).options(
        joinedload(models.DiagnosticRide.student),
        joinedload(models.DiagnosticRide.instructor)
    ).filter(
        models.DiagnosticRide.student_id == student_id
    ).order_by(models.DiagnosticRide.created_at.desc()).all()
