# Session Log: 2026-02-23 — Diagnostic Ride Fixes + Docker Auto-Reload
**Device:** Original machine (crashing — switch to other device after pulling alpha)

---

## What Was Done This Session

### 1. Fixed Diagnostic Ride 500 Error
**Problem:** Completing a diagnostic ride returned `500 Internal Server Error`:
```
Evaluation failed: '<' not supported between instances of 'NoneType' and 'int'
```
MongoDB returns telemetry documents where sensor fields (`y`, `z`, `x`) exist with `None` values. `dict.get('y', 0)` returns `None` when the key is present but null — comparison then crashed.

**Fix:** Changed `point.get('field', 0)` → `(point.get('field') or 0)` in:
- `app/services/diagnostic_evaluator.py` — `_evaluate_braking` and `_evaluate_cornering`

---

### 2. Fixed Response Validation Errors on User/Instructor Schema
**Problem:** `POST /api/v1/diagnostic-rides/` returned `ResponseValidationError`:
- `phone_number: Input should be a valid string (got None)` — DB allows NULL for old users
- `insurance_policy/certification_id too short` — test data used single chars

**Fix:**
- `app/schemas.py` — Added `phone_number: Optional[str] = None` override in `User` response class + `None` guard in `validate_phone` validator
- `tests/test_api_diagnostic_rides.py` — Updated test data to `"INS-12345"` / `"CERT-001"`

**Result:** 3 tests fixed, 0 regressions (9 failed → 6 failed overall suite).

---

### 3. Docker Hot Reload for Code Changes
**Problem:** Every Python code change required `docker-compose build` + restart.

**Fix:** `docker-compose.yml`
- Added `./app:/app/app` volume mount — host `app/` folder is live-shared into the container
- Changed command from `gunicorn` to `uvicorn ... --reload`
- Uvicorn now detects any `.py` change and restarts the server process in ~1-2 seconds
- **No rebuild needed for code changes**

---

### 4. Auto-Rebuild for requirements.txt / Dockerfile Changes
**Problem:** Changing `requirements.txt` or `Dockerfile` still required manual `docker-compose build`.

**Fix:** Created `watch-rebuild.sh` + systemd service `road-ready-watcher`:
- Uses `inotifywait` to watch `requirements.txt` and `Dockerfile` for saves
- On change: automatically runs `docker-compose up --build -d`
- Service starts on boot, restarts itself if it crashes

```bash
# Check watcher status
sudo systemctl status road-ready-watcher
sudo journalctl -u road-ready-watcher -f
```

---

### 5. Docker Health Checks (IN PROGRESS — not yet applied cleanly)
**Problem:** `restart: always` restarts crashed containers but not stuck/deadlocked ones.

**Fix:** Added `healthcheck:` to both services in `docker-compose.yml`:
- **web:** Hits `http://localhost:8000/openapi.json` every 30s. After 3 failures → container restarts.
- **mongodb:** Runs `mongosh --eval "db.adminCommand('ping')"` every 30s.

**Status:** healthcheck URL was fixed to `/openapi.json` (was `/api/v1/users/me` which returned 401 → exception). `docker-compose up -d` applied. Need to verify `(healthy)` status on the other device after pulling.

```bash
# Verify health status after pulling and starting
sudo docker ps
# Should show: roadready_app   Up X seconds (healthy)
# If still "starting", wait 30s and check again
```

---

## What To Do On The Other Device

```bash
cd /path/to/road-ready

# Pull latest
ssh-agent bash -c 'ssh-add ./new_deploy_key && git pull origin alpha'

# Start everything (no build needed — image will rebuild automatically if needed)
sudo docker-compose up -d

# Watch logs
sudo docker logs roadready_app -f

# Verify health checks pass after ~30s
sudo docker ps
```

---

## Current State of Each File

| File | Change |
|------|--------|
| `app/services/diagnostic_evaluator.py` | `or 0` null-safe sensor reads |
| `app/schemas.py` | `phone_number: Optional[str] = None` in User response |
| `tests/test_api_diagnostic_rides.py` | Valid test data for insurance/cert |
| `docker-compose.yml` | Volume mount + `--reload` + healthchecks |
| `watch-rebuild.sh` | Auto-rebuild on requirements/Dockerfile change |
| `/etc/systemd/system/road-ready-watcher.service` | Systemd service (installed on this machine only — re-install on new machine) |

---

## Re-install Watcher on New Machine

```bash
sudo apt-get install -y inotify-tools

sudo tee /etc/systemd/system/road-ready-watcher.service << 'EOF'
[Unit]
Description=Road Ready Docker auto-rebuild watcher
After=docker.service
Requires=docker.service

[Service]
Type=simple
User=YOUR_USERNAME
WorkingDirectory=/path/to/road-ready
ExecStart=/path/to/road-ready/watch-rebuild.sh
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable road-ready-watcher
sudo systemctl start road-ready-watcher
```
