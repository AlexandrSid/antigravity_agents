package com.example.userservice.domain.repository

import com.example.userservice.domain.entity.User
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepository
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class UserRepository : PanacheRepository<User> {

    fun findActiveById(id: Long): User? {
        return find("id = ?1 and isDeleted = false", id).firstResult()
    }

    fun findActiveByEmail(email: String): User? {
        return find("lower(email) = lower(?1) and isDeleted = false", email).firstResult()
    }

    fun findActiveByPhone(phone: String): User? {
        return find("phoneNumber = ?1 and isDeleted = false", phone).firstResult()
    }

    fun findActiveChildren(userId: Long): List<User> {
        return list("(father.id = ?1 or mother.id = ?1) and isDeleted = false", userId)
    }
}
