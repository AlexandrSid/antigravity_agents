package com.example.userservice.api.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Past
import jakarta.validation.constraints.Size
import java.time.LocalDate

data class UpdateUserRequest(
    @field:NotBlank(message = "First name must not be blank")
    @field:Size(min = 1, max = 100, message = "First name must be between 1 and 100 characters")
    val firstName: String,

    @field:NotBlank(message = "Last name must not be blank")
    @field:Size(min = 1, max = 100, message = "Last name must be between 1 and 100 characters")
    val lastName: String,

    @field:NotNull(message = "Birth date must not be null")
    @field:Past(message = "Birth date must be in the past")
    val birthDate: LocalDate,

    @field:NotBlank(message = "Email must not be blank")
    @field:Email(message = "Email must be a valid email address")
    val email: String,

    val phoneNumber: String? = null,

    @field:Valid
    val address: AddressDto? = null,

    val fatherId: Long? = null,
    val motherId: Long? = null
)
