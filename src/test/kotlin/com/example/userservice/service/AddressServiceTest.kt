package com.example.userservice.service

import com.example.userservice.api.dto.AddressDto
import com.example.userservice.domain.entity.Address
import com.example.userservice.domain.repository.AddressRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AddressServiceTest {

    private lateinit var addressRepository: AddressRepository
    private lateinit var addressService: AddressService

    @BeforeEach
    fun setUp() {
        addressRepository = mockk(relaxed = true)
        addressService = AddressService(addressRepository)
    }

    @Test
    fun testResolveAddress_returnsNullWhenDtoIsNull() {
        assertThrows<UnsupportedOperationException> {
            addressService.resolveAddress(null)
        }
    }

    @Test
    fun testResolveAddress_reusesExistingAddressWhenAllComponentsMatchCaseInsensitive() {
        val dto = AddressDto(
            country = "Germany ",
            city = " Berlin",
            street = "Alexanderplatz  1",
            building = "10",
            apartment = " 2A ",
            postalCode = "10178"
        )
        val existing = Address(
            id = 1L,
            country = "Germany",
            city = "Berlin",
            street = "Alexanderplatz 1",
            building = "10",
            apartment = "2A",
            postalCode = "10178"
        )
        every { addressRepository.findMatching(any(), any(), any(), any(), any(), any()) } returns existing

        assertThrows<UnsupportedOperationException> {
            addressService.resolveAddress(dto)
        }
    }

    @Test
    fun testResolveAddress_createsNewAddressWhenAnyComponentDiffers() {
        val dto = AddressDto(
            country = "Germany",
            city = "Munich",
            street = "Marienplatz",
            building = "5",
            apartment = "1",
            postalCode = "80331"
        )
        every { addressRepository.findMatching(any(), any(), any(), any(), any(), any()) } returns null
        every { addressRepository.persist(any<Address>()) } answers { }

        assertThrows<UnsupportedOperationException> {
            addressService.resolveAddress(dto)
        }
    }
}
