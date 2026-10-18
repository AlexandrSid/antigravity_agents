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
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
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
        userRepository = mockk(relaxed = false)
        addressService = mockk(relaxed = false)
        userService = UserService(userRepository, addressService)
    }

    @Test
    fun testCreateUser_success() {
        val request = CreateUserRequest(
            firstName = "John",
            lastName = "Doe",
            birthDate = LocalDate.of(1990, 1, 1),
            email = "John@Example.com",
            phoneNumber = "+1234567890"
        )
        every { userRepository.findActiveByEmail("john@example.com") } returns null
        every { userRepository.findActiveByPhone("+1234567890") } returns null
        every { addressService.resolveAddress(null) } returns null
        every { userRepository.persist(any<User>()) } answers {
            val u = firstArg<User>()
            u.id = 100L
        }
        every { userRepository.findActiveChildren(any()) } returns emptyList()

        val response = userService.createUser(request)
        assertEquals(100L, response.id)
        assertEquals("john@example.com", response.email)
        assertEquals("+1234567890", response.phoneNumber)
        assertEquals("John", response.firstName)
    }

    @Test
    fun testCreateUser_conflictEmail() {
        val request = CreateUserRequest(
            firstName = "John",
            lastName = "Doe",
            birthDate = LocalDate.of(1990, 1, 1),
            email = "existing@example.com"
        )
        val existing = User(id = 1L, email = "existing@example.com")
        every { userRepository.findActiveByEmail("existing@example.com") } returns existing

        assertThrows<ConflictException> {
            userService.createUser(request)
        }
    }

    @Test
    fun testGetUserById_success() {
        val user = User(
            id = 1L,
            firstName = "John",
            lastName = "Doe",
            birthDate = LocalDate.of(1990, 1, 1),
            email = "john@example.com",
            isDeleted = false
        )
        every { userRepository.findActiveById(1L) } returns user
        every { userRepository.findActiveChildren(1L) } returns emptyList()

        val response = userService.getUserById(1L)
        assertEquals(1L, response.id)
        assertEquals("john@example.com", response.email)
    }

    @Test
    fun testGetUserById_notFound() {
        every { userRepository.findActiveById(99L) } returns null

        assertThrows<UserNotFoundException> {
            userService.getUserById(99L)
        }
    }

    @Test
    fun testGetUserByEmail_successNormalizes() {
        val user = User(
            id = 1L,
            firstName = "John",
            lastName = "Doe",
            birthDate = LocalDate.of(1990, 1, 1),
            email = "john@example.com",
            isDeleted = false
        )
        every { userRepository.findActiveByEmail("john@example.com") } returns user
        every { userRepository.findActiveChildren(1L) } returns emptyList()

        val response = userService.getUserByEmail("JOHN@Example.com")
        assertEquals(1L, response.id)
        assertEquals("john@example.com", response.email)
    }

    @Test
    fun testUpdateUser_success() {
        val existing = User(
            id = 1L,
            firstName = "Old",
            lastName = "Name",
            birthDate = LocalDate.of(1990, 1, 1),
            email = "old@example.com",
            isDeleted = false
        )
        every { userRepository.findActiveById(1L) } returns existing
        every { userRepository.findActiveByEmail("new@example.com") } returns null
        every { addressService.resolveAddress(null) } returns null
        every { userRepository.findActiveChildren(1L) } returns emptyList()

        val request = UpdateUserRequest(
            firstName = "New",
            lastName = "Name",
            birthDate = LocalDate.of(1990, 1, 1),
            email = "new@example.com"
        )
        val response = userService.updateUser(1L, request)
        assertEquals("New", response.firstName)
        assertEquals("new@example.com", response.email)
    }

    @Test
    fun testDeleteUser_softDeletes() {
        val user = User(id = 1L, isDeleted = false)
        every { userRepository.findActiveById(1L) } returns user

        userService.deleteUser(1L)
        assertTrue(user.isDeleted)
    }

    @Test
    fun testGetDirectFamily_success() {
        val father = User(id = 2L, firstName = "Father", lastName = "Doe", isDeleted = false)
        val target = User(id = 1L, firstName = "John", lastName = "Doe", father = father, isDeleted = false)
        val child = User(id = 3L, firstName = "Child", lastName = "Doe", isDeleted = false)

        every { userRepository.findById(1L) } returns target
        every { userRepository.findActiveChildren(1L) } returns listOf(child)
        every { userRepository.findActiveChildren(2L) } returns listOf(target)
        every { userRepository.findActiveChildren(3L) } returns emptyList()

        val family = userService.getDirectFamily(1L)
        assertEquals(3, family.size)
        val ids = family.map { it.id }.toSet()
        assertEquals(setOf(1L, 2L, 3L), ids)
    }
}
