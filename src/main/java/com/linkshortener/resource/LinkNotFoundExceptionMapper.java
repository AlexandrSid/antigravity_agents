package com.linkshortener.resource;

import com.linkshortener.dto.ProblemDetailResponse;
import com.linkshortener.exception.LinkNotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.net.URI;
import java.util.Collections;

@Provider
public class LinkNotFoundExceptionMapper implements ExceptionMapper<LinkNotFoundException> {

    @Override
    public Response toResponse(LinkNotFoundException exception) {
        ProblemDetailResponse problem = new ProblemDetailResponse(
                URI.create("urn:problem-type:link-not-found"),
                "Link Not Found",
                Response.Status.NOT_FOUND.getStatusCode(),
                exception.getMessage(),
                URI.create("/error"),
                Collections.emptyList()
        );
        return Response.status(Response.Status.NOT_FOUND)
                .type("application/problem+json")
                .entity(problem)
                .build();
    }
}
