from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from typing import List
from app import schemas, models
from app.api import deps

router = APIRouter()

@router.get("/", response_model=List[schemas.InstructorProfile])
async def read_instructors(
    skip: int = 0, 
    limit: int = 100, 
    city: str = None, 
    db: Session = Depends(deps.get_db)
):
    query = db.query(models.InstructorProfile)
    if city:
        query = query.filter(models.InstructorProfile.city.ilike(f"%{city}%"))
    instructors = query.offset(skip).limit(limit).all()
    return instructors
