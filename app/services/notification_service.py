from sqlalchemy.orm import Session
from datetime import datetime
from app import models

def create_notification(db: Session, user_id: int, title: str, message: str):
    notification = models.Notification(
        user_id=user_id,
        title=title,
        message=message,
        timestamp=datetime.now().isoformat()
    )
    db.add(notification)
    db.commit()
    db.refresh(notification)
    return notification
