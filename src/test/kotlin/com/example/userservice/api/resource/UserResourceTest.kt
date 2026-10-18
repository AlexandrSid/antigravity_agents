package com.example.userservice.api.resource

import com.example.userservice.api.dto.CreateUserRequest
import com.example.userservice.api.dto.UpdateUserRequest
import com.example.userservice.service.UserService
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class UserResourceTest {

    private lateinit var userService: UserService
    private lateinit var userResource: UserResource

    @BeforeEach
    fun setUp() {
        userService = mockk(relaxed = true)
        userResource = UserResource(userService)
    }

    @Test
    fun testCreateUser_endpointRedPhaseThrowsUnsupportedOperation() {
        val request = CreateUserRequest(
            firstName = "Alice",
            lastName = "Wonder",
            birthDate = LocalDate.of(1995, 3, 20),
            email = "alice@example.com"
        )
        assertThrows<UnsupportedOperationException> {
            userResource.createUser(request)
        }
    }

    @Test
    fun testGetUserById_endpointRedPhaseThrowsUnsupportedOperation() {
        assertThrows<UnsupportedOperationException> {
            userResource.getUserById(1L)
        }
    }

    @Test
    fun testGetUserByEmail_endpointRedPhaseThrowsUnsupportedOperation() {
        assertThrows<UnsupportedOperationException> {
            userResource.getUserByEmail("alice@example.com")
        }
    }

    @Test
    fun testUpdateUser_endpointRedPhaseThrowsUnsupportedOperation() {
        val request = UpdateUserRequest(
            firstName = "Alice",
            lastName = "Updated",
            birthDate = LocalDate.of(1995, 3, 20),
            email = "alice.new@example.com"
        )
        assertThrows<UnsupportedOperationException> {
            userResource.updateUser(1L, request)
        }
    }

    @Test
    fun testDeleteUser_endpointRedPhaseThrowsUnsupportedOperation() {
        assertThrows<UnsupportedOperationException> {
            userResource.deleteUser(1L)
        }
    }

    @Test
    fun testGetDirectFamily_endpointRedPhaseThrowsUnsupportedOperation() {
        assertThrows<UnsupportedOperationException> {
            userResource.getDirectFamily(1L)
        }
    }
}
