# Quarkus Architecture Slot Implementation

> **Layer**: `architecture`  
> **Implements**: `.ai/guidelines/architecture/META.md`  
> **Target**: Framework / Quarkus 3.x / Jakarta REST / Panache

---

## 1. ROUTING_AND_CONTROLLER_STRUCTURE
```java
@Path("/api/v1/links")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LinkResource {

    private final LinkService linkService;

    @Inject
    public LinkResource(LinkService linkService) {
        this.linkService = linkService;
    }

    @POST
    public Response createShortLink(@Valid CreateLinkRequest request) {
        LinkResponse response = linkService.createLink(request);
        return Response.created(URI.create("/api/v1/links/" + response.shortCode()))
                       .entity(response)
                       .build();
    }
}
```

## 2. DTO_VALIDATION_MAPPING
- Ingress parameters annotated with `@Valid`.
- DTO fields annotated with standard Jakarta Validation constraints (`@NotBlank`, `@Size`, `@NotNull`, etc.).
- Validation failures intercepted and mapped to RFC 7807 problem details.

## 3. RFC7807_ERROR_MAPPER_IMPLEMENTATION
Global Exception Mapper returning `application/problem+json`:
```java
@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        List<ProblemDetailResponse.InvalidParam> params = exception.getConstraintViolations().stream()
            .map(v -> new ProblemDetailResponse.InvalidParam(
                StreamSupport.stream(v.getPropertyPath().spliterator(), false)
                    .reduce((first, second) -> second).map(Path.Node::getName).orElse("unknown"),
                v.getMessage()
            ))
            .toList();

        ProblemDetailResponse problem = new ProblemDetailResponse(
            URI.create("urn:problem:validation-error"),
            "Validation Error",
            Response.Status.BAD_REQUEST.getStatusCode(),
            "Validation failed for one or more fields",
            URI.create(uriInfo.getPath()),
            params
        );

        return Response.status(Response.Status.BAD_REQUEST)
                       .type("application/problem+json")
                       .entity(problem)
                       .build();
    }
}
```

## 4. DI_ANNOTATIONS_AND_SCOPE & FRAMEWORK INVARIANTS
1. **Dependency Injection**:
   - Services and Resources use `@ApplicationScoped`.
   - Prefer constructor injection over field `@Inject`.
2. **Panache Boundaries**:
   - Entities extend `PanacheEntity` or `PanacheEntityBase`.
   - Never return Panache entities directly from Resource endpoints; map to dedicated immutable Response DTOs.
3. **Database Dialect Compatibility**:
   - Quarkus 3.15+ targets H2 versions `>= 2.3.230` by default. If connecting to external H2 TCP Server running `2.1.x`, explicitly set:
     `quarkus.datasource.db-version=2.1.0` in `application.properties`.
4. **Fast-JAR Packaging**:
   - Production Dockerfiles must build using Quarkus Fast-JAR (`quarkus.package.jar.type=fast-jar`).
