# Fix Signup 500 Error — 2026-02-26

## Problem
POST /api/v1/users/ returned 500 Internal Server Error — new users could not sign up.

## Root Cause
The SQLite database inside the Docker container was missing two columns added in a later
migration that were never applied to the existing DB file:
- `instructor_profiles.stripe_account_id`
- `instructor_profiles.stripe_onboarding_completed`

SQLAlchemy tried to SELECT these columns, threw `sqlite3.OperationalError`, which caused
a ResponseValidationError on the signup response.

## Fix
Ran ALTER TABLE directly inside the running container to add the missing columns:
```
docker exec roadready_app python3 -c "ALTER TABLE instructor_profiles ADD COLUMN stripe_account_id VARCHAR; ..."
```

## Recommendation
Run `migrate_stripe_fields.py` (already in repo) before starting the server, or ensure
`app/main.py` startup migration covers these columns automatically.
