#!/bin/bash
export PYTHONUNBUFFERED=1
exec ./venv/bin/python3 -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --log-level debug > server.log 2>&1
