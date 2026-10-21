package com.linkshortener.resource;

import com.linkshortener.service.LinkService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

import java.net.URI;

@Path("/")
@ApplicationScoped
public class RedirectResource {

    @Inject
    LinkService linkService;

    @GET
    @Path("/{shortKey}")
    public Response redirectToOriginal(@PathParam("shortKey") String shortKey) {
        String originalUrl = linkService.resolveOriginalUrl(shortKey);
        return Response.status(Response.Status.FOUND)
                .location(URI.create(originalUrl))
                .build();
    }
}