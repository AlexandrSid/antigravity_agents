package com.example.userservice.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "first_name", nullable = false, length = 100)
    var firstName: String = "",

    @Column(name = "last_name", nullable = false, length = 100)
    var lastName: String = "",

    @Column(name = "birth_date", nullable = false)
    var birthDate: LocalDate = LocalDate.now(),

    @Column(name = "email", nullable = false, length = 255)
    var email: String = "",

    @Column(name = "phone_number", length = 50)
    var phoneNumber: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id")
    var address: Address? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "father_id")
    var father: User? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mother_id")
    var mother: User? = null,

    @Column(name = "is_deleted", nullable = false)
    var isDeleted: Boolean = false
)
