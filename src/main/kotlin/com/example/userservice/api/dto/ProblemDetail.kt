package com.example.userservice.api.dto

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ProblemDetail(
    val type: String,
    val title: String,
    val status: Int,
    val detail: String,
    val instance: String? = null,
    val invalidParams: List<InvalidParam>? = null
)

data class InvalidParam(
    val name: String,
    val reason: String
)
