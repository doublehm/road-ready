from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from sqlalchemy.sql.expression import func
from app import schemas, models
from app.api import deps

router = APIRouter()

@router.get("/random", response_model=schemas.QuizQuestion)
async def get_random_question(db: Session = Depends(deps.get_db)):
    question = db.query(models.QuizQuestion).order_by(func.random()).first()
    return question
