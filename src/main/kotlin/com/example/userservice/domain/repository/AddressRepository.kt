package com.example.userservice.domain.repository

import com.example.userservice.domain.entity.Address
import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepository
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class AddressRepository : PanacheRepository<Address> {
    fun findMatching(
        country: String,
        city: String,
        street: String,
        building: String,
        apartment: String?,
        postalCode: String?
    ): Address? {
        throw UnsupportedOperationException("Not implemented")
    }
}
