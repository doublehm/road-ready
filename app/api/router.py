from fastapi import APIRouter
from app.api import auth, users, instructors, drivelogs, bookings, sessions, quiz

api_router = APIRouter()

api_router.include_router(auth.router, prefix="/auth", tags=["auth"])
api_router.include_router(users.router, prefix="/users", tags=["users"])
api_router.include_router(instructors.router, prefix="/instructors", tags=["instructors"])
api_router.include_router(drivelogs.router, prefix="/drivelogs", tags=["drivelogs"])
api_router.include_router(bookings.router, prefix="/bookings", tags=["bookings"])
api_router.include_router(sessions.router, prefix="/sessions", tags=["sessions"])
api_router.include_router(quiz.router, prefix="/quiz", tags=["quiz"])
