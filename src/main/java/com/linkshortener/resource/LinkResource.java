package com.linkshortener.resource;

import com.linkshortener.dto.CreateLinkRequest;
import com.linkshortener.dto.LinkResponse;
import com.linkshortener.service.LinkService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/links")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LinkResource {

    @Inject
    LinkService linkService;

    @POST
    public Response createShortLink(@Valid CreateLinkRequest request) {
        LinkResponse response = linkService.createShortLink(request);
        return Response.status(Response.Status.CREATED)
                .entity(response)
                .build();
    }
}
