# Implementation Plan: Database Redesign (Hybrid SQL + NoSQL)

#### Phase 1: Infrastructure & Tech Stack Update
- [x] Task: Update `tech-stack.md` to reflect the new Hybrid SQL + NoSQL architecture. b0a15393
- [ ] Task: Configure NoSQL store (targeting MongoDB/Pymongo) for telemetry storage.
- [ ] Task: Implement dual-database connection management in `app/database.py`.
- [ ] Task: Conductor - User Manual Verification 'Phase 1: Infrastructure & Tech Stack Update' (Protocol in workflow.md)

#### Phase 2: SQL Schema Refactoring (Relational Clean-up)
- [ ] Task: Refactor `app/models.py` to remove high-frequency tables (`DiagnosticRidePoint`, `Acceleration`, `Rotation`).
- [ ] Task: Update `DiagnosticRide` model to link to NoSQL document IDs or ride IDs.
- [ ] Task: Reset and migrate SQLite `roadready.db` for a fresh start.
- [ ] Task: Conductor - User Manual Verification 'Phase 2: SQL Schema Refactoring (Relational Clean-up)' (Protocol in workflow.md)

#### Phase 3: NoSQL Persistence Layer
- [ ] Task: Implement NoSQL repository for high-throughput sensor data writes.
- [ ] Task: Update `app/api/diagnostic_rides.py` to persist telemetry chunks directly to NoSQL.
- [ ] Task: Implement telemetry retrieval service that aggregates data from NoSQL for the frontend.
- [ ] Task: Conductor - User Manual Verification 'Phase 3: NoSQL Persistence Layer' (Protocol in workflow.md)

#### Phase 4: Evaluation Engine & API Integration
- [ ] Task: Update `DiagnosticEvaluator.py` to fetch and process data from the NoSQL store.
- [ ] Task: Refactor `evaluate_diagnostic_ride` endpoint to work with hybrid data sources.
- [ ] Task: Update live-streaming WebSockets to pipe data into the NoSQL buffer.
- [ ] Task: Conductor - User Manual Verification 'Phase 4: Evaluation Engine & API Integration' (Protocol in workflow.md)

#### Phase 5: Mobile App Buffering & Resilient Sync
- [ ] Task: Implement local telemetry buffering in the mobile app using `AsyncStorage` or `SQLite`.
- [ ] Task: Enhance `DiagnosticRideActiveScreen` with a background sync mechanism for buffered data.
- [ ] Task: Implement "Offline Mode" indicators in the mobile UI.
- [ ] Task: Conductor - User Manual Verification 'Phase 5: Mobile App Buffering & Resilient Sync' (Protocol in workflow.md)

#### Phase 6: Final Verification & Quality Gates
- [ ] Task: Execute full TDD cycle for the new hybrid services.
- [ ] Task: Verify end-to-end flow: Mobile Buffer -> NoSQL Sync -> SQL Metadata -> Evaluation.
- [ ] Task: Run coverage reports and ensure >80% on new data modules.
- [ ] Task: Conductor - User Manual Verification 'Phase 6: Final Verification & Quality Gates' (Protocol in workflow.md)
