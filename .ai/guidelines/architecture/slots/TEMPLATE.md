# [Framework Name] Architecture Slot Implementation

> **Layer**: `architecture`  
> **Implements**: `.ai/guidelines/architecture/META.md`

---

## 1. ROUTING_AND_CONTROLLER_STRUCTURE
```[language]
// Idiomatic controller or router declaration with DTO parameter bindings
```

## 2. DTO_VALIDATION_MAPPING
```[language]
// Bean validation / schema validation binding
```

## 3. RFC7807_ERROR_MAPPER_IMPLEMENTATION
```[language]
// Global exception handler / filter emitting application/problem+json
```

## 4. DI_ANNOTATIONS_AND_SCOPE
- **Service Scope**: `[e.g. @ApplicationScoped / @Injectable]`
- **Repository Pattern**: `[e.g. PanacheRepository / Spring Data / SQLAlchemy session]`
