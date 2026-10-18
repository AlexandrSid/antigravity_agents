package com.example.userservice.service

import com.example.userservice.api.dto.AddressDto
import com.example.userservice.domain.entity.Address
import com.example.userservice.domain.repository.AddressRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AddressServiceTest {

    private lateinit var addressRepository: AddressRepository
    private lateinit var addressService: AddressService

    @BeforeEach
    fun setUp() {
        addressRepository = mockk(relaxed = false)
        addressService = AddressService(addressRepository)
    }

    @Test
    fun testResolveAddress_returnsNullWhenDtoIsNull() {
        val result = addressService.resolveAddress(null)
        assertNull(result)
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
        every { addressRepository.findMatching("Germany", "Berlin", "Alexanderplatz 1", "10", "2A", "10178") } returns existing

        val result = addressService.resolveAddress(dto)
        assertNotNull(result)
        assertEquals(1L, result?.id)
        verify(exactly = 0) { addressRepository.persist(any<Address>()) }
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
        every { addressRepository.findMatching("Germany", "Munich", "Marienplatz", "5", "1", "80331") } returns null
        every { addressRepository.persist(any<Address>()) } answers { }

        val result = addressService.resolveAddress(dto)
        assertNotNull(result)
        assertEquals("Munich", result?.city)
        verify(exactly = 1) { addressRepository.persist(any<Address>()) }
    }
}
