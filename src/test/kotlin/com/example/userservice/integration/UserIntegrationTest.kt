package com.example.userservice.integration

import com.example.userservice.api.dto.AddressDto
import com.example.userservice.api.dto.CreateUserRequest
import com.example.userservice.api.dto.UpdateUserRequest
import com.example.userservice.domain.entity.Address
import com.example.userservice.domain.entity.User
import com.example.userservice.domain.repository.AddressRepository
import com.example.userservice.domain.repository.UserRepository
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import jakarta.inject.Inject
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.LocalDate

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class UserIntegrationTest {

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var addressRepository: AddressRepository

    @Test
    @Order(1)
    fun testSeedDataset_contains1000UsersAnd20Addresses() {
        val userCount = userRepository.count()
        val addressCount = addressRepository.count()

        assertTrue(userCount >= 1000L, "Seed database must contain at least 1000 users, found: $userCount")
        assertEquals(20L, addressCount, "Seed database must contain exactly 20 distinct addresses")
    }

    @Test
    @Order(2)
    fun testSeedDataset_parentalAgeInvariant() {
        val seededUsers = userRepository.listAll()
        for (user in seededUsers) {
            val father = user.father
            if (father != null) {
                assertTrue(
                    father.birthDate.isBefore(user.birthDate),
                    "Father (${father.id}, born ${father.birthDate}) must be older than child (${user.id}, born ${user.birthDate})"
                )
            }
            val mother = user.mother
            if (mother != null) {
                assertTrue(
                    mother.birthDate.isBefore(user.birthDate),
                    "Mother (${mother.id}, born ${mother.birthDate}) must be older than child (${user.id}, born ${user.birthDate})"
                )
            }
        }
    }

    @Test
    @Order(3)
    fun testRestApi_createUserFlow() {
        val request = CreateUserRequest(
            firstName = "Integration",
            lastName = "Tester",
            birthDate = LocalDate.of(1998, 7, 24),
            email = "integration.tester@example.com",
            phoneNumber = "+19998887766",
            address = AddressDto(
                country = "Germany",
                city = "Berlin",
                street = "Alexanderplatz",
                building = "1",
                apartment = "101",
                postalCode = "10178"
            )
        )

        val createdId = given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/api/v1/users")
            .then()
            .statusCode(201)
            .header("Location", org.hamcrest.Matchers.containsString("/api/v1/users/"))
            .body("id", notNullValue())
            .body("email", equalTo("integration.tester@example.com"))
            .body("phoneNumber", equalTo("+19998887766"))
            .body("address.city", equalTo("Berlin"))
            .extract()
            .path<Number>("id")
            .toLong()

        // Lookup by ID
        given()
            .`when`()
            .get("/api/v1/users/$createdId")
            .then()
            .statusCode(200)
            .body("id", equalTo(createdId.toInt()))
            .body("firstName", equalTo("Integration"))

        // Lookup by email
        given()
            .`when`()
            .get("/api/v1/users/by-email?email=INTEGRATION.TESTER@EXAMPLE.COM")
            .then()
            .statusCode(200)
            .body("id", equalTo(createdId.toInt()))

        // Soft delete
        given()
            .`when`()
            .delete("/api/v1/users/$createdId")
            .then()
            .statusCode(204)

        // Subsequent lookup returns 404 Problem Details
        given()
            .`when`()
            .get("/api/v1/users/$createdId")
            .then()
            .statusCode(404)
            .body("type", equalTo("urn:problem:not-found"))
            .body("status", equalTo(404))
    }

    @Test
    @Order(4)
    fun testRestApi_familyEndpoint() {
        val user1 = userRepository.findActiveById(1L) ?: return
        given()
            .`when`()
            .get("/api/v1/users/1/family")
            .then()
            .statusCode(200)
            .body("$", notNullValue())
    }
}
