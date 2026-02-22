# Copilot Instructions — Road Ready

Road Ready is a driver training marketplace platform. Students book lessons with instructors, complete diagnostic rides (sensor-tracked driving sessions), and progress through learning modules. Instructors manage availability, receive Stripe payouts, and review student performance.

## Commands

### Backend (Python/FastAPI)
```bash
# Run all tests
pytest

# Run a single test file
pytest tests/test_api_diagnostic_rides.py

# Run a single test by name
pytest tests/test_api_diagnostic_rides.py::test_create_ride -v

# Start dev server
uvicorn app.main:app --reload

# Or use the script
bash run_server.sh
```

### Mobile (React Native/Expo)
```bash
cd mobile-app

# Start Expo dev server
npm start

# Run on Android/iOS
npm run android
npm run ios

# Run Jest tests
npm test
```

## Architecture

### Hybrid Database
The app uses **two databases in tandem**:
- **SQLite/PostgreSQL** (via SQLAlchemy) — relational business data: users, bookings, diagnostic rides metadata, quiz, modules, messages.
- **MongoDB** (via Motor async client) — high-volume telemetry: raw GPS/sensor logs from active rides, live ride events.

`app/database.py` configures both. Use `DATABASE_URL` env var for Postgres in prod (defaults to `sqlite:///./roadready.db`). Use `MONGODB_URL` for MongoDB (defaults to `mongodb://localhost:27017/roadready`).

### Backend Structure
- **`app/main.py`** — FastAPI app setup, CORS, Stripe config, runs `_run_migrations()` on startup (ALTER TABLE approach for adding columns to existing tables).
- **`app/api/router.py`** — Central router that includes all sub-routers at `/api/v1`.
- **`app/api/<feature>.py`** — One file per feature domain (auth, users, instructors, bookings, diagnostic_rides, drivelogs, quiz, messages, modules, sessions, notifications).
- **`app/models.py`** — All SQLAlchemy ORM models in one file.
- **`app/schemas.py`** — All Pydantic request/response schemas.
- **`app/services/`** — Business logic: `DiagnosticEvaluator` (ride scoring), `NoSQLRepository` (MongoDB queries), `SpeedLimitService` (OSM/Overpass API).
- **`app/api/deps.py`** — FastAPI dependencies: `get_db`, `get_current_user`, role guards.

### Dependency Injection Pattern
All route handlers receive `db: Session = Depends(deps.get_db)` and `current_user: models.User = Depends(deps.get_current_user)`. NoSQL access uses `get_nosql_db()` directly (not via Depends).

### Schema Migrations
New columns are added in `_run_migrations()` in `main.py` using raw `ALTER TABLE` SQL (not Alembic). Standalone migration scripts (`migrate_*.py`) in the project root handle one-off data migrations.

### Mobile Architecture
- **`mobile-app/src/api/client.js`** — Axios instance with base URL and JWT interceptor.
- **`mobile-app/src/context/AuthContext.js`** — Auth state (token, user role) via React Context.
- **`mobile-app/src/screens/`** — Screens organized by role (Student, Instructor, shared).
- **`mobile-app/src/hooks/`** — Sensor hooks: `useDeviceMotion.js`, `useGPSTracking.js`, `useSpeedLimit.js`.
- Navigation uses React Navigation (stack + bottom tabs), gated by user role from AuthContext.

### Diagnostic Ride Flow
1. Mobile collects GPS/accelerometer data during a drive using the sensor hooks.
2. Live events stream to the backend via WebSocket at `/api/v1/diagnostic-rides/live-stream`.
3. On ride end, the mobile app POSTs the full payload to `/api/v1/diagnostic-rides/complete`.
4. `DiagnosticEvaluator` processes sensor data + human feedback, assigns fault codes (A1–E4), and produces a score.
5. Results are stored in SQL (`DiagnosticRide` model); raw telemetry stays in MongoDB.

### Roles & Permissions
Three roles: `student`, `instructor`, `admin`. Role is stored on the `User` model. Route guards in `deps.py` enforce access. Instructors can view rides for students they have an active booking with, even if they weren't the direct supervisor.

## Key Conventions

### Tests use in-memory SQLite
`tests/conftest.py` creates a fresh in-memory SQLite DB per test function and overrides FastAPI's `get_db` and `get_current_user` dependencies. Tests do **not** hit the real DB or MongoDB.

### Maps use OpenStreetMap (no API key required)
All map components use `UrlTile` with OpenStreetMap tiles. Do not introduce Google Maps — it requires an API key that isn't configured.

### Environment Variables
| Variable | Purpose | Default |
|---|---|---|
| `DATABASE_URL` | PostgreSQL connection string | SQLite file |
| `MONGODB_URL` | MongoDB connection string | `localhost:27017` |
| `STRIPE_API_KEY` | Stripe secret key | — |
| `STRIPE_WEBHOOK_SECRET` | Stripe webhook signing | — |
| `SECRET_KEY` | JWT signing secret | — |

### Stripe Connect
Instructors use Stripe Connect for payouts. The `InstructorProfile` model has `stripe_account_id`. Bookings create Stripe payment intents; payouts are routed to the instructor's connected account.

### Static Files & Templates
`app/static/` and `app/templates/` serve the admin dashboard via Jinja2. These are mounted in `main.py` and are separate from the mobile app API.
