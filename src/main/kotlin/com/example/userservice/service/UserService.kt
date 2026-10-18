package com.example.userservice.service

import com.example.userservice.api.dto.CreateUserRequest
import com.example.userservice.api.dto.UpdateUserRequest
import com.example.userservice.api.dto.UserResponse
import com.example.userservice.domain.repository.UserRepository
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class UserService(
    private val userRepository: UserRepository,
    private val addressService: AddressService
) {
    fun createUser(request: CreateUserRequest): UserResponse {
        throw UnsupportedOperationException("Not implemented")
    }

    fun getUserById(id: Long): UserResponse {
        throw UnsupportedOperationException("Not implemented")
    }

    fun getUserByEmail(email: String): UserResponse {
        throw UnsupportedOperationException("Not implemented")
    }

    fun updateUser(id: Long, request: UpdateUserRequest): UserResponse {
        throw UnsupportedOperationException("Not implemented")
    }

    fun deleteUser(id: Long) {
        throw UnsupportedOperationException("Not implemented")
    }

    fun getDirectFamily(id: Long): List<UserResponse> {
        throw UnsupportedOperationException("Not implemented")
    }
}
