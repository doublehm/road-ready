# Implementation Plan: Database Redesign (Hybrid SQL + NoSQL)

#### Phase 1: Infrastructure & Tech Stack Update [checkpoint: 43d21384]
- [x] Task: Update `tech-stack.md` to reflect the new Hybrid SQL + NoSQL architecture. b0a15393
- [x] Task: Configure NoSQL store (targeting MongoDB/Pymongo) for telemetry storage. 79acbb20
- [x] Task: Implement dual-database connection management in `app/database.py`. ae0397ca
- [ ] Task: Conductor - User Manual Verification 'Phase 1: Infrastructure & Tech Stack Update' (Protocol in workflow.md)

#### Phase 2: SQL Schema Refactoring (Relational Clean-up) [checkpoint: 4657be10]
- [x] Task: Refactor `app/models.py` to remove high-frequency tables (`DiagnosticRidePoint`, `Acceleration`, `Rotation`). 69e7ee0e
- [x] Task: Update `DiagnosticRide` model to link to NoSQL document IDs or ride IDs. 17eeceea
- [x] Task: Reset and migrate SQLite `roadready.db` for a fresh start. f812704d
- [ ] Task: Conductor - User Manual Verification 'Phase 2: SQL Schema Refactoring (Relational Clean-up)' (Protocol in workflow.md)

#### Phase 3: NoSQL Persistence Layer [checkpoint: 20c04ca2]
- [x] Task: Implement NoSQL repository for high-throughput sensor data writes. f003d869
- [x] Task: Update `app/api/diagnostic_rides.py` to persist telemetry chunks directly to NoSQL. d74fccf8
- [x] Task: Implement telemetry retrieval service that aggregates data from NoSQL for the frontend. 994ccb73
- [ ] Task: Conductor - User Manual Verification 'Phase 3: NoSQL Persistence Layer' (Protocol in workflow.md)

#### Phase 4: Evaluation Engine & API Integration [checkpoint: 31653d31]
- [x] Task: Update `DiagnosticEvaluator.py` to fetch and process data from the NoSQL store. 84edd257
- [x] Task: Refactor `evaluate_diagnostic_ride` endpoint to work with hybrid data sources. 84edd257
- [x] Task: Update live-streaming WebSockets to pipe data into the NoSQL buffer. 84edd257
- [ ] Task: Conductor - User Manual Verification 'Phase 4: Evaluation Engine & API Integration' (Protocol in workflow.md)

#### Phase 5: Mobile App Buffering & Resilient Sync [checkpoint: b0168c03]
- [x] Task: Implement local telemetry buffering in the mobile app using `AsyncStorage` or `SQLite`. 7e34f634
- [x] Task: Enhance `DiagnosticRideActiveScreen` with a background sync mechanism for buffered data. 7e34f634
- [x] Task: Implement "Offline Mode" indicators in the mobile UI. 7e34f634
- [ ] Task: Conductor - User Manual Verification 'Phase 5: Mobile App Buffering & Resilient Sync' (Protocol in workflow.md)

#### Phase 6: Final Verification & Quality Gates [checkpoint: 9432deba]
- [x] Task: Execute full TDD cycle for the new hybrid services. 9432deba
- [x] Task: Verify end-to-end flow: Mobile Buffer -> NoSQL Sync -> SQL Metadata -> Evaluation. 9432deba
- [x] Task: Run coverage reports and ensure >80% on new data modules. 9432deba
- [x] Task: Conductor - User Manual Verification 'Phase 6: Final Verification & Quality Gates' (Protocol in workflow.md) 9432deba
