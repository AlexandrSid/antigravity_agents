package com.example.userservice.service

import com.example.userservice.api.dto.AddressDto
import com.example.userservice.domain.entity.Address
import com.example.userservice.domain.repository.AddressRepository
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class AddressService(
    private val addressRepository: AddressRepository
) {
    fun resolveAddress(dto: AddressDto?): Address? {
        throw UnsupportedOperationException("Not implemented")
    }
}
