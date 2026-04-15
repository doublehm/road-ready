from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session, joinedload
from typing import List
import stripe
import os
from app import schemas, models
from app.api import deps

router = APIRouter()

# Stripe Configuration
stripe.api_key = os.getenv("STRIPE_API_KEY")

@router.post("/me/stripe-onboarding")
async def initiate_stripe_onboarding(
    current_user: models.User = Depends(deps.get_current_user),
    db: Session = Depends(deps.get_db)
):
    """
    Initiate Stripe Connect onboarding for the current instructor.
    """
    if current_user.role != "instructor":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Only instructors can initiate Stripe onboarding"
        )

    instructor_profile = db.query(models.InstructorProfile).filter(
        models.InstructorProfile.user_id == current_user.id
    ).first()

    if not instructor_profile:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Instructor profile not found"
        )

    # 1. Create Stripe Account if it doesn't exist
    if not instructor_profile.stripe_account_id:
        try:
            account = stripe.Account.create(
                type="express",
                country="CA", # Default to Canada based on project context
                email=current_user.email,
                capabilities={
                    "card_payments": {"requested": True},
                    "transfers": {"requested": True},
                },
                business_type="individual",
            )
            instructor_profile.stripe_account_id = account.id
            db.commit()
        except Exception as e:
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail=f"Stripe account creation failed: {str(e)}"
            )

    # 2. Create Account Link for onboarding
    try:
        base_url = os.getenv("APP_URL", "http://localhost:8000")
        
        account_link = stripe.AccountLink.create(
            account=instructor_profile.stripe_account_id,
            refresh_url=f"{base_url}/api/v1/instructors/me/stripe-onboarding", 
            return_url=f"{base_url}/stripe-callback?session_id=mock", 
            type="account_onboarding",
        )
        return {"url": account_link.url}
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Stripe onboarding link creation failed: {str(e)}"
        )

@router.get("/", response_model=List[schemas.UserWithProfile])
async def read_instructors(
    skip: int = 0, 
    limit: int = 100, 
    city: str = None, 
    db: Session = Depends(deps.get_db)
):
    query = db.query(models.User).filter(models.User.role == "instructor")
    
    if city:
        query = query.join(models.User.instructor_profile).filter(models.InstructorProfile.city.ilike(f"%{city}%"))
    
    # Preload profile to avoid N+1 queries
    instructors = query.options(joinedload(models.User.instructor_profile)).offset(skip).limit(limit).all()
    return instructors
