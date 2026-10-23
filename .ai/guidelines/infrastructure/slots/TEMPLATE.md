# [Tool Name] Infrastructure Slot Implementation

> **Layer**: `infrastructure`  
> **Implements**: `.ai/guidelines/infrastructure/META.md`

---

## 1. BASE_IMAGE_STANDARD
- **Base Image**: `[e.g. eclipse-temurin:21-jre-alpine]`
- **User**: Non-root UID `[e.g. 185]`

## 2. PACKAGING_AND_COPY_LAYOUT
```dockerfile
# Multi-stage copy layout
COPY --chown=185 build/output/ /deployments/
```

## 3. COMPOSE_SERVICE_TEMPLATE
```yaml
# Standard service definition with dynamic namespace and healthcheck dependencies
```
