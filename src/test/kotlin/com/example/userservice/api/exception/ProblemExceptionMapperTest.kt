package com.example.userservice.api.exception

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ProblemExceptionMapperTest {

    private lateinit var mapper: ProblemExceptionMapper

    @BeforeEach
    fun setUp() {
        mapper = ProblemExceptionMapper()
    }

    @Test
    fun testMapNotFound_returns404ProblemDetails() {
        val ex = UserNotFoundException("User 42 not found")
        val response = mapper.mapNotFound(ex)

        assertEquals(404, response.status)
        val body = response.entity
        assertNotNull(body)
        assertEquals("urn:problem:not-found", body.type)
        assertEquals(404, body.status)
        assertEquals("User 42 not found", body.detail)
    }

    @Test
    fun testMapConflict_returns409ProblemDetails() {
        val ex = ConflictException("Email already in use")
        val response = mapper.mapConflict(ex)

        assertEquals(409, response.status)
        val body = response.entity
        assertNotNull(body)
        assertEquals("urn:problem:conflict", body.type)
        assertEquals(409, body.status)
        assertEquals("Email already in use", body.detail)
    }

    @Test
    fun testMapBadParentage_returns400ProblemDetails() {
        val ex = BadParentageException("Cyclic ancestry detected")
        val response = mapper.mapBadParentage(ex)

        assertEquals(400, response.status)
        val body = response.entity
        assertNotNull(body)
        assertEquals("urn:problem:bad-request", body.type)
        assertEquals(400, body.status)
        assertEquals("Cyclic ancestry detected", body.detail)
    }
}
