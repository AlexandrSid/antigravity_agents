package com.example.userservice.util

object NormalizationUtils {

    fun normalizeWhitespace(value: String?): String? {
        if (value == null) return null
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return ""
        return trimmed.replace(Regex("\\s+"), " ")
    }

    fun normalizeEmail(email: String): String {
        return email.trim().lowercase()
    }

    fun normalizePhone(phone: String?): String? {
        if (phone.isNullOrBlank()) return null
        val trimmed = phone.trim()
        val digitsOnly = trimmed.filter { it.isDigit() }
        return "+$digitsOnly"
    }
}
