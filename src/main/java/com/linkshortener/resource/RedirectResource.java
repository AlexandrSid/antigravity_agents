package com.linkshortener.resource;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

@Path("/")
public class RedirectResource {

    @GET
    @Path("/{shortKey}")
    public Response redirectToOriginal(@PathParam("shortKey") String shortKey) {
        throw new UnsupportedOperationException("Not implemented: RedirectResource.redirectToOriginal");
    }
}
