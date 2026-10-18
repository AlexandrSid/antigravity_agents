package com.example.userservice.service

import com.example.userservice.api.dto.AddressDto
import com.example.userservice.domain.entity.Address
import com.example.userservice.domain.repository.AddressRepository
import com.example.userservice.util.NormalizationUtils
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
class AddressService(
    private val addressRepository: AddressRepository
) {
    @Transactional
    fun resolveAddress(dto: AddressDto?): Address? {
        if (dto == null) return null

        val country = NormalizationUtils.normalizeWhitespace(dto.country) ?: ""
        val city = NormalizationUtils.normalizeWhitespace(dto.city) ?: ""
        val street = NormalizationUtils.normalizeWhitespace(dto.street) ?: ""
        val building = NormalizationUtils.normalizeWhitespace(dto.building) ?: ""
        val apartment = NormalizationUtils.normalizeWhitespace(dto.apartment)?.ifEmpty { null }
        val postalCode = NormalizationUtils.normalizeWhitespace(dto.postalCode)?.ifEmpty { null }

        val existing = addressRepository.findMatching(country, city, street, building, apartment, postalCode)
        if (existing != null) {
            return existing
        }

        val newAddress = Address(
            country = country,
            city = city,
            street = street,
            building = building,
            apartment = apartment,
            postalCode = postalCode
        )
        addressRepository.persist(newAddress)
        return newAddress
    }
}
