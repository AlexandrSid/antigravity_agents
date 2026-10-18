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
        val query = StringBuilder("LOWER(country) = LOWER(?1) AND LOWER(city) = LOWER(?2) AND LOWER(street) = LOWER(?3) AND LOWER(building) = LOWER(?4)")
        val params = mutableListOf<Any>(country, city, street, building)

        if (apartment != null) {
            params.add(apartment)
            query.append(" AND LOWER(apartment) = LOWER(?${params.size})")
        } else {
            query.append(" AND apartment IS NULL")
        }

        if (postalCode != null) {
            params.add(postalCode)
            query.append(" AND LOWER(postalCode) = LOWER(?${params.size})")
        } else {
            query.append(" AND postalCode IS NULL")
        }

        return find(query.toString(), *params.toTypedArray()).firstResult()
    }
}
