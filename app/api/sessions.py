from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from typing import List
from datetime import datetime
from app import schemas, models
from app.api import deps

router = APIRouter()

@router.post("/", response_model=schemas.DrivingSession)
async def create_session(
    session: schemas.DrivingSessionCreate, 
    db: Session = Depends(deps.get_db), 
    current_user: models.User = Depends(deps.get_current_user)
):
    if current_user.role != "instructor":
        raise HTTPException(status_code=403, detail="Only instructors can create sessions")

    db_session = models.DrivingSession(
        **session.dict(),
        created_at=datetime.utcnow().isoformat()
    )
    db.add(db_session)
    db.commit()
    db.refresh(db_session)
    return db_session
