# Road Ready 🚗

A platform connecting driving instructors with students.

## Tech Stack

*   **Backend:** FastAPI (Python)
*   **Database:** SQLite
*   **Templates:** Jinja2 + Bootstrap 5

## Setup

1.  **Dependencies:**
    Dependencies are installed locally in the `lib` folder.

2.  **Database:**
    The database is pre-seeded with dummy instructors.
    To re-seed:
    ```bash
    PYTHONPATH=./lib python3 seed.py
    ```

3.  **Run the Server:**
    ```bash
    PYTHONPATH=./lib python3 -m uvicorn app.main:app --reload
    ```

4.  **Access:**
    Open [http://localhost:8000](http://localhost:8000)

## Features

*   **Student Portal:** Search for instructors by City and Budget.
*   **Instructor Portal:** Manage availability and view profile.
*   **Verification:** Mock flows for instructor and student verification.