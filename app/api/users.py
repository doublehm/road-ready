from fastapi import APIRouter, Depends
from app import schemas, models
from app.api import deps

router = APIRouter()

@router.get("/me", response_model=schemas.User)
async def read_users_me(current_user: models.User = Depends(deps.get_current_user)):
    return current_user
