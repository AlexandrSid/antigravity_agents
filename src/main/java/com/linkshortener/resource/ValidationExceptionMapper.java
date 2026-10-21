package com.linkshortener.resource;

import com.linkshortener.dto.ProblemDetailResponse;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.net.URI;
import java.util.List;

@Provider
public class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        List<ProblemDetailResponse.InvalidParam> invalidParams = exception.getConstraintViolations().stream()
                .map(cv -> new ProblemDetailResponse.InvalidParam(cv.getPropertyPath().toString(), cv.getMessage()))
                .toList();

        ProblemDetailResponse problem = new ProblemDetailResponse(
                URI.create("urn:problem-type:bad-request"),
                "Bad Request",
                Response.Status.BAD_REQUEST.getStatusCode(),
                "Validation failed",
                URI.create("/api/v1/links"),
                invalidParams
        );

        return Response.status(Response.Status.BAD_REQUEST)
                .type("application/problem+json")
                .entity(problem)
                .build();
    }
}
