# Tech Stack: Road Ready

## Backend
*   **Framework:** FastAPI (Python) - High-performance web framework for building APIs.
*   **Database (Hybrid Architecture):**
    *   **Relational (SQL):** SQLite - Lightweight, file-based SQL database for business processes (Users, Bookings, Payments).
    *   **Document (NoSQL):** MongoDB (or equivalent Document Store) - For high-volume telemetry and sensor data persistence.
*   **ORM/ODM:**
    *   **SQLAlchemy:** SQL toolkit and Object-Relational Mapper for SQL.
    *   **Pymongo/Motor:** Async MongoDB driver for NoSQL telemetry.
*   **Authentication:** JWT (JSON Web Tokens) using `python-jose` and `PyJWT`.
*   **Real-time Communication:** WebSockets (FastAPI) for live telemetry and event streaming.

## Frontend (Web)
*   **Templating:** Jinja2 - Modern and designer-friendly templating language for Python.
*   **Styling:** Bootstrap 5 - Powerful, extensible, and feature-packed frontend toolkit.
*   **Mapping:** Leaflet.js - Open-source JavaScript library for mobile-friendly interactive maps.
*   **Map Tiles:** OpenStreetMap & CartoDB Dark Matter.

## Mobile Application
*   **Framework:** React Native with Expo - Framework for building native apps using React.
*   **Navigation:** React Navigation.
*   **State/API:** Axios for networking.
*   **WebView:** react-native-webview for Leaflet-based interactive maps.

## External Services
*   **Payments:** Stripe - Financial infrastructure for the internet.
*   **Geocoding:** Nominatim (OpenStreetMap) - Open-source address search.

## Infrastructure & Tools
*   **Environment:** Docker (docker-compose.yml, Dockerfile).
*   **Server:** Uvicorn/Gunicorn.
