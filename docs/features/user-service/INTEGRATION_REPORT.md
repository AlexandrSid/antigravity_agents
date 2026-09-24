# User Service Integration/E2E Experiment Report

## 1. Result

- **Final status**: PASS.
- **Date**: 2026-09-24.
- **Unit/Slice gate**: `.\gradlew.bat test` — PASS.
- **Integration/E2E gate**: `.\scripts\run-integration-e2e.ps1` — PASS.
- **Docker build**: H2 and user-service images — PASS.
- **Runtime**: H2 health, application readiness, Flyway startup, JPA schema validation — PASS.
- **Persistence restart**: marker created before Compose restart and read after restart — PASS.
- **Cleanup**: containers, network, and test volume removed; `docker compose ls` returned no projects.

The final run executed 11 Integration/E2E tests in the main phase and one targeted persistence verification after restart. Existing unit/slice tests remained green.

## 2. Test Harness

- Isolated Gradle source set: `src/integrationTest/**`.
- Dedicated task: `integrationTest`; it is not part of the fast `test` task.
- Orchestrator: `scripts/run-integration-e2e.ps1`.
- Every run uses a unique Compose project and a clean volume.
- Readiness has a finite timeout.
- Failures print Compose status and logs.
- Successful runs restart both containers, verify persistent data, and then remove all test resources.

## 3. Coverage Matrix

### Database and Flyway

- Public schema tables, user foreign keys, generated active identity columns, and required indexes.
- Flyway history and all three successful migrations.
- Exactly 20 seeded addresses and 1,000 seeded active users in reserved ID ranges.
- Uneven 10–120 user address distribution.
- Existing and chronological parent links, including multiple generations.
- User and address identity restart behavior through generated API IDs above seed ranges.
- Database-level active email/phone uniqueness and reuse after soft deletion.

### Docker and Runtime

- Multi-stage application image build.
- H2 image build and health check.
- `user-service` connection to `h2-db:9092`.
- Automatic Flyway execution.
- Hibernate `ddl-auto=validate`.
- Persistent H2 data across container restart.
- Deterministic cleanup.

### HTTP Happy Paths

- Create with `201`, persisted DTO, normalized email/phone, generated ID, and `Location`.
- Lookup by ID and case-normalized email.
- Full PUT replacement and null-reset semantics.
- Direct-family top-level array.
- Soft delete with `204` and empty body.

### HTTP Edge and Error Paths

- Bean validation, malformed JSON, and non-normalizable phone.
- Active email/phone conflicts and identity reuse after deletion.
- Missing parent, same parents, self-parent, and direct cycle.
- Address whitespace/case normalization, exact reuse, postal-code difference, and concurrent reuse.
- Deleted relation redaction.
- Active and deleted family-anchor behavior.
- RFC 7807 status, type, title, detail, instance, and content type.

## 4. Findings and Corrections

### Production defect: lazy relations outside a transaction

- **Observed**: seeded `GET /api/v1/users/1` returned HTTP 500 with `LazyInitializationException` because `open-in-view=false` and DTO mapping accessed lazy address/parent relations after repository transactions closed.
- **Correction**: added a service-layer `@Transactional` boundary to `UserServiceImpl`.
- **Verification**: readiness endpoint and all HTTP scenarios pass; unit tests remain green.

### Production defect: concurrent address duplication

- **Observed**: eight simultaneous requests with the same normalized address produced eight address rows.
- **Correction**: added `V3__serialize_address_resolution.sql` with a singleton database lock row, acquired through `SELECT ... FOR UPDATE` before match-or-insert.
- **Reason**: a database transaction lock works across multiple service instances; a JVM-only `synchronized` block would not.
- **Verification**: all concurrent responses are `201` and reference one address ID.

### Harness defects found during the experiment

- Windows PowerShell did not support `Invoke-WebRequest -SkipHttpErrorCheck`.
- Windows PowerShell native stderr handling caused transient `curl` startup responses to abort the script.
- Flyway stores its schema-history table name in lowercase and includes a non-versioned table-creation row.
- Seed assertions initially depended on test-class execution order.

All four harness issues were corrected before the final pass.

### External infrastructure friction

- Docker Desktop was installed but initially stopped.
- Docker Desktop DNS intermittently failed for Docker Hub CDN and Maven Central.
- A Docker Desktop restart and image pre-pull recovered the environment.
- Future orchestration should distinguish external pull/DNS failures from project defects and support bounded retry.

## 5. Retrospective Input for Agent Design

The experiment required four distinct capabilities:

1. Author immutable Integration/E2E tests from Section 2 and `SPEC.md`.
2. Orchestrate Docker lifecycle, readiness, logs, restart, and cleanup.
3. Diagnose whether failures belong to the harness, external environment, or production.
4. Fix production/infrastructure without changing the accepted tests.

The recommended permanent model remains two roles:

- `integration-test-writer`: owns test authoring and orchestration artifacts; production is read-only.
- `integration-code-writer`: owns production/infrastructure fixes; Integration/E2E tests are immutable.

The test-writer role should also own bounded retry and failure classification because these are inseparable from reliable Docker test execution. A third orchestrator role is not justified by this experiment.
