from pydantic import BaseModel

class BookingAction(BaseModel):
    action: str
    reason: str = None
