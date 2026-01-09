from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from typing import List
from app import schemas, models
from app.api import deps

router = APIRouter()

@router.post("/", response_model=schemas.DriveLog)
async def create_drive_log(
    log: schemas.DriveLogCreate, 
    db: Session = Depends(deps.get_db), 
    current_user: models.User = Depends(deps.get_current_user)
):
    db_log = models.DriveLog(**log.dict(), user_id=current_user.id)
    db.add(db_log)
    db.commit()
    db.refresh(db_log)
    return db_log

@router.get("/", response_model=List[schemas.DriveLog])
async def read_drive_logs(
    skip: int = 0, 
    limit: int = 100, 
    db: Session = Depends(deps.get_db),
    current_user: models.User = Depends(deps.get_current_user)
):
    logs = db.query(models.DriveLog).filter(models.DriveLog.user_id == current_user.id).offset(skip).limit(limit).all()
    return logs
