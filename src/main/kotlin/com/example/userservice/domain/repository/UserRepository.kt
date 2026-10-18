package com.example.userservice.domain.repository

import com.example.userservice.domain.entity.User
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepository
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class UserRepository : PanacheRepository<User> {
    fun findActiveById(id: Long): User? {
        throw UnsupportedOperationException("Not implemented")
    }

    fun findActiveByEmail(email: String): User? {
        throw UnsupportedOperationException("Not implemented")
    }

    fun findActiveByPhone(phone: String): User? {
        throw UnsupportedOperationException("Not implemented")
    }

    fun findActiveChildren(userId: Long): List<User> {
        throw UnsupportedOperationException("Not implemented")
    }
}
