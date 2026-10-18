package com.example.userservice.api.resource

import com.example.userservice.api.dto.CreateUserRequest
import com.example.userservice.api.dto.UpdateUserRequest
import com.example.userservice.api.dto.UserResponse
import com.example.userservice.service.UserService
import jakarta.validation.Valid
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.net.URI

@Path("/api/v1/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class UserResource(
    private val userService: UserService
) {

    @POST
    fun createUser(@Valid request: CreateUserRequest): Response {
        val created = userService.createUser(request)
        val location = URI.create("/api/v1/users/${created.id}")
        return Response.created(location).entity(created).build()
    }

    @GET
    @Path("/{id}")
    fun getUserById(@PathParam("id") id: Long): UserResponse {
        return userService.getUserById(id)
    }

    @GET
    @Path("/by-email")
    fun getUserByEmail(@QueryParam("email") email: String): UserResponse {
        return userService.getUserByEmail(email)
    }

    @PUT
    @Path("/{id}")
    fun updateUser(@PathParam("id") id: Long, @Valid request: UpdateUserRequest): UserResponse {
        return userService.updateUser(id, request)
    }

    @DELETE
    @Path("/{id}")
    fun deleteUser(@PathParam("id") id: Long): Response {
        userService.deleteUser(id)
        return Response.noContent().build()
    }

    @GET
    @Path("/{id}/family")
    fun getDirectFamily(@PathParam("id") id: Long): List<UserResponse> {
        return userService.getDirectFamily(id)
    }
}
