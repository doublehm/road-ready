from fastapi import APIRouter
from app.api import auth, users, instructors, drivelogs, bookings, sessions, quiz, messages, notifications, modules, diagnostic_rides, road_conditions, speed_limits

api_router = APIRouter()

api_router.include_router(auth.router, prefix="/auth", tags=["auth"])
api_router.include_router(users.router, prefix="/users", tags=["users"])
api_router.include_router(instructors.router, prefix="/instructors", tags=["instructors"])
api_router.include_router(drivelogs.router, prefix="/drivelogs", tags=["drivelogs"])
api_router.include_router(bookings.router, prefix="/bookings", tags=["bookings"])
api_router.include_router(sessions.router, prefix="/sessions", tags=["sessions"])
api_router.include_router(quiz.router, prefix="/quiz", tags=["quiz"])
api_router.include_router(messages.router, prefix="/messages", tags=["messages"])
api_router.include_router(notifications.router, prefix="/notifications", tags=["notifications"])
api_router.include_router(modules.router, prefix="/modules", tags=["modules"])
api_router.include_router(diagnostic_rides.router, prefix="/diagnostic-rides", tags=["diagnostic-rides"])
api_router.include_router(road_conditions.router, prefix="/road-conditions", tags=["road-conditions"])
api_router.include_router(speed_limits.router, prefix="/speed-limits", tags=["speed-limits"])
