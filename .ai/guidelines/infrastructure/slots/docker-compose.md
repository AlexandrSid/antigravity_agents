# Docker Compose Infrastructure Slot Implementation

> **Layer**: `infrastructure`  
> **Implements**: `.ai/guidelines/infrastructure/META.md`  
> **Target**: Infrastructure / Docker Compose v2

---

## 1. BASE_IMAGE_STANDARD
- **Base Images**: Minimal official images with explicit version tags (e.g., `eclipse-temurin:21-jre-alpine`, `python:3.11-slim`, `node:20-alpine`).
- **User**: Dedicated non-root user (e.g. UID `185` or `appuser`). Never run production application containers as `root`.

## 2. PACKAGING_AND_COPY_LAYOUT
```dockerfile
# Multi-stage production layout (example Quarkus fast-jar)
COPY --chown=185 build/quarkus-app/lib/ /deployments/lib/
COPY --chown=185 build/quarkus-app/*.jar /deployments/
COPY --chown=185 build/quarkus-app/app/ /deployments/app/
COPY --chown=185 build/quarkus-app/quarkus/ /deployments/quarkus/
```

## 3. COMPOSE_SERVICE_TEMPLATE
```yaml
services:
  app:
    image: ${COMPOSE_PROJECT_NAME:-link-shortener}-app:${BUILD_VERSION:-latest}
    build:
      context: .
      dockerfile: Dockerfile
    ports:
      - "${APP_PORT:-8080}:8080"
    environment:
      - QUARKUS_PROFILE=docker
      - QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://db:5432/${POSTGRES_DB}
    depends_on:
      db:
        condition: service_healthy
    networks:
      - internal-net

networks:
  internal-net:
    driver: bridge
```
