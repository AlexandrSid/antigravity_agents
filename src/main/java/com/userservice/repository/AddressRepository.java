package com.userservice.repository;

import com.userservice.domain.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {

    Optional<Address> findByNormalizedText(String normalizedText);

    @Query("""
            SELECT a FROM Address a
            WHERE LOWER(a.country) = LOWER(:country)
              AND LOWER(a.city) = LOWER(:city)
              AND LOWER(a.street) = LOWER(:street)
              AND LOWER(a.building) = LOWER(:building)
              AND ((a.apartment IS NULL AND :apartment IS NULL) OR LOWER(a.apartment) = LOWER(:apartment))
              AND ((a.postalCode IS NULL AND :postalCode IS NULL) OR LOWER(a.postalCode) = LOWER(:postalCode))
            """)
    Optional<Address> findMatchingNormalized(
            @Param("country") String country,
            @Param("city") String city,
            @Param("street") String street,
            @Param("building") String building,
            @Param("apartment") String apartment,
            @Param("postalCode") String postalCode
    );
}
