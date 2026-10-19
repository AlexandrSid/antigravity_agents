# Docker Compose Infrastructure Standards

> **Stack Profile**: Infrastructure / Docker Compose v2

---

## 1. Project Scoping & Isolation
1. **Dynamic Project Flag**:
   - Always invoke Compose commands with an explicit project flag `-p ${COMPOSE_PROJECT_NAME}`.
2. **Ephemeral Lifecycle**:
   - Up command:
     ```bash
     docker compose -p ${COMPOSE_PROJECT_NAME} up -d --build --force-recreate
     ```
   - Down command:
     ```bash
     docker compose -p ${COMPOSE_PROJECT_NAME} down -v --remove-orphans
     ```

---

## 2. Dependency Wiring & Readiness
1. **Healthcheck Condition**:
   - Applications depending on databases or message brokers must wait for the health condition:
     ```yaml
     depends_on:
       h2-db:
         condition: service_healthy
     ```
