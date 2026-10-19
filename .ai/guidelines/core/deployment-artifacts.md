# Core Deployment Artifacts & Container Lifecycle Standards

> **Scope**: Universal & Platform-Agnostic  
> **Mandatory Invariant**: Docker must NEVER receive, reuse, or run an unversioned artifact from different pipeline runs or features.

---

## 1. Artifact Tagging & Versioning Invariants
1. **Strict `:latest` Prohibition**:
   - The `:latest` tag is categorically forbidden for project application images in all pipelines, local runs, and CI/CD stages.
2. **Deterministic Dynamic Tagging**:
   - Every application image must be uniquely and deterministically tagged:
     ```text
     {service-identifier}:${BUILD_VERSION:-0.1.0}-{COMMIT_SHA_SHORT}
     # or for feature development runs:
     {service-identifier}:${FEATURE_NAME}
     ```
3. **No Hardcoded Global Container Names**:
   - Hardcoded global container names (e.g. `container_name: user-service`) are prohibited in `docker-compose.yml`.
   - Containers must either omit `container_name` (allowing Compose to prefix them automatically) or strictly use the project prefix:
     `container_name: ${COMPOSE_PROJECT_NAME:-default}-user-service`.

---

## 2. Docker Compose Project Isolation
1. **Isolated Project Namespace**:
   - Every pipeline run and feature must specify an explicit project namespace:
     ```bash
     COMPOSE_PROJECT_NAME=${FEATURE_NAME}-${RUN_ID:-local}
     ```
   - All CLI invocations must explicitly pass `-p ${COMPOSE_PROJECT_NAME}` to prevent collision with other services, containers, or local networks.
2. **Clean Ephemeral Volumes Contract**:
   - All stateful dependencies (databases, message queues, caches) must utilize named volumes scoped to the project namespace.
   - Before starting an integration run and immediately after completion, containers, networks, and volumes must be purged:
     ```bash
     docker compose -p ${COMPOSE_PROJECT_NAME} down -v --remove-orphans
     ```
   - Reusing a volume from a previous run or another feature branch is strictly prohibited.
3. **Forced Clean Rebuild**:
   - Starting the environment for test execution must unconditionally force recreation and rebuild:
     ```bash
     docker compose -p ${COMPOSE_PROJECT_NAME} up -d --build --force-recreate
     ```

---

## 3. Dependency Readiness Contract (Healthchecks)
1. **Mandatory Service Healthchecks**:
   - Any dependency container (RDBMS, Redis, Kafka, etc.) must declare an explicit `healthcheck` block in Compose with realistic `interval`, `timeout`, and `retries`.
2. **Application Startup Dependency**:
   - The application service must never start until its dependencies report healthy status:
     ```yaml
     depends_on:
       db:
         condition: service_healthy
     ```
   - Relying solely on port binding or container-running status without a verified healthcheck is forbidden to prevent startup race conditions with database migrations.
