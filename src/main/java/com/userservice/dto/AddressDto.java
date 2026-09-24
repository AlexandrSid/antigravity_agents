package com.userservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AddressDto(
        @JsonProperty(access = JsonProperty.Access.READ_ONLY) Long id,
        @NotBlank @Size(max = 100) String country,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 150) String street,
        @NotBlank @Size(max = 50) String building,
        @Size(max = 50) String apartment,
        @Size(max = 20) String postalCode
) {
}
