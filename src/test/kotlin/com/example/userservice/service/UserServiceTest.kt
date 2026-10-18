package com.example.userservice.service

import com.example.userservice.api.dto.CreateUserRequest
import com.example.userservice.api.dto.UpdateUserRequest
import com.example.userservice.api.exception.ConflictException
import com.example.userservice.api.exception.UserNotFoundException
import com.example.userservice.domain.entity.Address
import com.example.userservice.domain.entity.User
import com.example.userservice.domain.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class UserServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var addressService: AddressService
    private lateinit var userService: UserService

    @BeforeEach
    fun setUp() {
        userRepository = mockk(relaxed = true)
        addressService = mockk(relaxed = true)
        userService = UserService(userRepository, addressService)
    }

    @Test
    fun testCreateUser_redPhaseThrowsUnsupportedOperation() {
        val request = CreateUserRequest(
            firstName = "John",
            lastName = "Doe",
            birthDate = LocalDate.of(1990, 1, 1),
            email = "john@example.com",
            phoneNumber = "+1234567890"
        )
        assertThrows<UnsupportedOperationException> {
            userService.createUser(request)
        }
    }

    @Test
    fun testGetUserById_redPhaseThrowsUnsupportedOperation() {
        val user = User(
            id = 1L,
            firstName = "John",
            lastName = "Doe",
            birthDate = LocalDate.of(1990, 1, 1),
            email = "john@example.com",
            isDeleted = false
        )
        every { userRepository.findActiveById(1L) } returns user

        assertThrows<UnsupportedOperationException> {
            userService.getUserById(1L)
        }
    }

    @Test
    fun testGetUserByEmail_redPhaseThrowsUnsupportedOperation() {
        val user = User(
            id = 1L,
            firstName = "John",
            lastName = "Doe",
            birthDate = LocalDate.of(1990, 1, 1),
            email = "john@example.com",
            isDeleted = false
        )
        every { userRepository.findActiveByEmail("john@example.com") } returns user

        assertThrows<UnsupportedOperationException> {
            userService.getUserByEmail("JOHN@example.com")
        }
    }

    @Test
    fun testUpdateUser_redPhaseThrowsUnsupportedOperation() {
        val request = UpdateUserRequest(
            firstName = "John",
            lastName = "Updated",
            birthDate = LocalDate.of(1990, 1, 1),
            email = "john.updated@example.com"
        )
        assertThrows<UnsupportedOperationException> {
            userService.updateUser(1L, request)
        }
    }

    @Test
    fun testDeleteUser_redPhaseThrowsUnsupportedOperation() {
        assertThrows<UnsupportedOperationException> {
            userService.deleteUser(1L)
        }
    }

    @Test
    fun testGetDirectFamily_redPhaseThrowsUnsupportedOperation() {
        assertThrows<UnsupportedOperationException> {
            userService.getDirectFamily(1L)
        }
    }
}
