package com.example.userservice.api.resource

import com.example.userservice.api.dto.CreateUserRequest
import com.example.userservice.api.dto.UpdateUserRequest
import com.example.userservice.api.dto.UserResponse
import com.example.userservice.service.UserService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class UserResourceTest {

    private lateinit var userService: UserService
    private lateinit var userResource: UserResource

    @BeforeEach
    fun setUp() {
        userService = mockk(relaxed = false)
        userResource = UserResource(userService)
    }

    @Test
    fun testCreateUser_endpointReturns201AndLocation() {
        val request = CreateUserRequest(
            firstName = "Alice",
            lastName = "Wonder",
            birthDate = LocalDate.of(1995, 3, 20),
            email = "alice@example.com"
        )
        val responseDto = UserResponse(
            id = 42L,
            firstName = "Alice",
            lastName = "Wonder",
            birthDate = LocalDate.of(1995, 3, 20),
            email = "alice@example.com"
        )
        every { userService.createUser(request) } returns responseDto

        val response = userResource.createUser(request)
        assertEquals(201, response.status)
        assertEquals("/api/v1/users/42", response.location.toString())
        assertEquals(responseDto, response.entity)
    }

    @Test
    fun testGetUserById_endpointReturns200() {
        val responseDto = UserResponse(
            id = 42L,
            firstName = "Alice",
            lastName = "Wonder",
            birthDate = LocalDate.of(1995, 3, 20),
            email = "alice@example.com"
        )
        every { userService.getUserById(42L) } returns responseDto

        val result = userResource.getUserById(42L)
        assertEquals(42L, result.id)
    }

    @Test
    fun testGetUserByEmail_endpointReturns200() {
        val responseDto = UserResponse(
            id = 42L,
            firstName = "Alice",
            lastName = "Wonder",
            birthDate = LocalDate.of(1995, 3, 20),
            email = "alice@example.com"
        )
        every { userService.getUserByEmail("alice@example.com") } returns responseDto

        val result = userResource.getUserByEmail("alice@example.com")
        assertEquals(42L, result.id)
    }

    @Test
    fun testUpdateUser_endpointReturns200() {
        val request = UpdateUserRequest(
            firstName = "Alice",
            lastName = "Updated",
            birthDate = LocalDate.of(1995, 3, 20),
            email = "alice.new@example.com"
        )
        val responseDto = UserResponse(
            id = 42L,
            firstName = "Alice",
            lastName = "Updated",
            birthDate = LocalDate.of(1995, 3, 20),
            email = "alice.new@example.com"
        )
        every { userService.updateUser(42L, request) } returns responseDto

        val result = userResource.updateUser(42L, request)
        assertEquals("Updated", result.lastName)
    }

    @Test
    fun testDeleteUser_endpointReturns204() {
        every { userService.deleteUser(42L) } answers { }

        val response = userResource.deleteUser(42L)
        assertEquals(204, response.status)
        verify(exactly = 1) { userService.deleteUser(42L) }
    }

    @Test
    fun testGetDirectFamily_endpointReturnsList() {
        val member = UserResponse(
            id = 42L,
            firstName = "Alice",
            lastName = "Wonder",
            birthDate = LocalDate.of(1995, 3, 20),
            email = "alice@example.com"
        )
        every { userService.getDirectFamily(42L) } returns listOf(member)

        val result = userResource.getDirectFamily(42L)
        assertEquals(1, result.size)
        assertEquals(42L, result[0].id)
    }
}
