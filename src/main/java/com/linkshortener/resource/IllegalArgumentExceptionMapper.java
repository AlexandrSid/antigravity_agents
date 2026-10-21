package com.linkshortener.resource;

import com.linkshortener.dto.ProblemDetailResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.net.URI;
import java.util.Collections;

@Provider
public class IllegalArgumentExceptionMapper implements ExceptionMapper<IllegalArgumentException> {

    @Override
    public Response toResponse(IllegalArgumentException exception) {
        ProblemDetailResponse problem = new ProblemDetailResponse(
                URI.create("urn:problem-type:bad-request"),
                "Bad Request",
                Response.Status.BAD_REQUEST.getStatusCode(),
                exception.getMessage(),
                URI.create("/api/v1/links"),
                Collections.emptyList()
        );

        return Response.status(Response.Status.BAD_REQUEST)
                .type("application/problem+json")
                .entity(problem)
                .build();
    }
}
