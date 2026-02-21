# Track Specification: Database Redesign (Hybrid SQL + NoSQL)

**Overview:**
Redesign the "Road Ready" data persistence layer to separate relational business processes from high-volume sensor telemetry. This involves introducing a NoSQL store (targeting MongoDB or similar) for granular ride data while retaining SQLite/PostgreSQL for structured business logic.

**Functional Requirements:**
- **Relational Store (SQL):** Maintain Users, Profiles, Bookings, Payments, and Evaluation Summaries.
- **Document Store (NoSQL):** Store high-frequency GPS, Acceleration, and Rotation points as documents.
- **Unified API:** Update `DiagnosticRide` service to fetch metadata from SQL and sensor blobs from NoSQL.
- **Mobile Buffering:** Enhance the mobile app to buffer telemetry locally (SQLite/AsyncStorage) and sync when the connection is restored.
- **Real-time Sync:** Continue streaming via WebSockets, but persist directly to the NoSQL buffer for better write performance.

**Non-Functional Requirements:**
- **Write Throughput:** Handle >10Hz telemetry per active ride without blocking API threads.
- **Latency:** Retrieve full ride telemetry for evaluation in <2 seconds.
- **Scalability:** Support multiple concurrent live rides.

**Acceptance Criteria:**
- [ ] New rides successfully persist metadata to SQL and telemetry to NoSQL.
- [ ] Evaluation engine correctly aggregates data from both sources.
- [ ] Mobile app demonstrates local buffering during simulated network loss.
- [ ] Existing `roadready.db` is reset, and the system starts with the new schema.

**Out of Scope:**
- Historical data migration (confirmed "Fresh Start").
- Downsampling or archiving policies (confirmed "High Fidelity").
