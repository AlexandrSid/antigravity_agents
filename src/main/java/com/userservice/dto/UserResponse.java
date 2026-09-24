package com.userservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserResponse(
        Long id,
        String firstName,
        String lastName,
        LocalDate birthDate,
        String email,
        String phoneNumber,
        AddressDto address,
        Long fatherId,
        Long motherId,
        List<Long> childrenIds
) {
    public UserResponse {
        childrenIds = childrenIds == null ? List.of() : childrenIds;
    }
}
