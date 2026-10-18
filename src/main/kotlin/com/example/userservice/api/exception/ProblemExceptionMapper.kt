package com.example.userservice.api.exception

import com.example.userservice.api.dto.InvalidParam
import com.example.userservice.api.dto.ProblemDetail
import jakarta.validation.ConstraintViolationException
import jakarta.ws.rs.core.Response
import org.jboss.resteasy.reactive.RestResponse
import org.jboss.resteasy.reactive.server.ServerExceptionMapper

class ProblemExceptionMapper {

    @ServerExceptionMapper
    fun mapConstraintViolation(ex: ConstraintViolationException): RestResponse<ProblemDetail> {
        val invalidParams = ex.constraintViolations.map {
            InvalidParam(
                name = it.propertyPath.toString(),
                reason = it.message
            )
        }
        val problem = ProblemDetail(
            type = "urn:problem:bad-request",
            title = "Bad Request",
            status = 400,
            detail = "Validation failed",
            invalidParams = invalidParams
        )
        return RestResponse.status(Response.Status.BAD_REQUEST, problem)
    }

    @ServerExceptionMapper
    fun mapNotFound(ex: UserNotFoundException): RestResponse<ProblemDetail> {
        val problem = ProblemDetail(
            type = "urn:problem:not-found",
            title = "Not Found",
            status = 404,
            detail = ex.message ?: "Resource not found"
        )
        return RestResponse.status(Response.Status.NOT_FOUND, problem)
    }

    @ServerExceptionMapper
    fun mapConflict(ex: ConflictException): RestResponse<ProblemDetail> {
        val problem = ProblemDetail(
            type = "urn:problem:conflict",
            title = "Conflict",
            status = 409,
            detail = ex.message ?: "Resource conflict"
        )
        return RestResponse.status(Response.Status.CONFLICT, problem)
    }

    @ServerExceptionMapper
    fun mapBadParentage(ex: BadParentageException): RestResponse<ProblemDetail> {
        val problem = ProblemDetail(
            type = "urn:problem:bad-request",
            title = "Bad Request",
            status = 400,
            detail = ex.message ?: "Invalid parental relationship"
        )
        return RestResponse.status(Response.Status.BAD_REQUEST, problem)
    }
}
