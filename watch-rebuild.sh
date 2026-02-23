#!/bin/bash
# Watches requirements.txt and Dockerfile for changes.
# When either is modified, rebuilds and restarts the Docker containers.

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
WATCH_FILES=("$PROJECT_DIR/requirements.txt" "$PROJECT_DIR/Dockerfile")

echo "[watch-rebuild] Watching for changes in: ${WATCH_FILES[*]}"

while true; do
    inotifywait -e close_write "${WATCH_FILES[@]}" 2>/dev/null
    echo "[watch-rebuild] Change detected — rebuilding Docker image..."
    cd "$PROJECT_DIR" && sudo docker-compose up --build -d
    echo "[watch-rebuild] Rebuild complete. Watching again..."
done
