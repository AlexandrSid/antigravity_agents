package com.example.userservice.api.dto

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.LocalDate

@JsonInclude(JsonInclude.Include.NON_NULL)
data class UserResponse(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val birthDate: LocalDate,
    val email: String,
    val phoneNumber: String? = null,
    val address: AddressDto? = null,
    val fatherId: Long? = null,
    val motherId: Long? = null,
    val childrenIds: List<Long> = emptyList()
)
