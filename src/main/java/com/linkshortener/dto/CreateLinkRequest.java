package com.linkshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateLinkRequest(
    @NotBlank(message = "URL must not be blank")
    @Size(max = 2048, message = "URL must not exceed 2048 characters")
    @Pattern(regexp = "^https?://.+", message = "URL must be a valid HTTP or HTTPS URL")
    String url
) {}
