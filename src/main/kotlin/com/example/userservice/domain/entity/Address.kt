package com.example.userservice.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "addresses")
class Address(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "country", nullable = false, length = 100)
    var country: String = "",

    @Column(name = "city", nullable = false, length = 100)
    var city: String = "",

    @Column(name = "street", nullable = false, length = 150)
    var street: String = "",

    @Column(name = "building", nullable = false, length = 50)
    var building: String = "",

    @Column(name = "apartment", length = 50)
    var apartment: String? = null,

    @Column(name = "postal_code", length = 20)
    var postalCode: String? = null
)
