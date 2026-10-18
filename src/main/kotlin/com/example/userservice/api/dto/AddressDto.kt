package com.example.userservice.api.dto

import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AddressDto(
    val id: Long? = null,

    @field:NotBlank(message = "Country must not be blank")
    @field:Size(max = 100, message = "Country must not exceed 100 characters")
    val country: String,

    @field:NotBlank(message = "City must not be blank")
    @field:Size(max = 100, message = "City must not exceed 100 characters")
    val city: String,

    @field:NotBlank(message = "Street must not be blank")
    @field:Size(max = 150, message = "Street must not exceed 150 characters")
    val street: String,

    @field:NotBlank(message = "Building must not be blank")
    @field:Size(max = 50, message = "Building must not exceed 50 characters")
    val building: String,

    @field:Size(max = 50, message = "Apartment must not exceed 50 characters")
    val apartment: String? = null,

    @field:Size(max = 20, message = "Postal code must not exceed 20 characters")
    val postalCode: String? = null
)
