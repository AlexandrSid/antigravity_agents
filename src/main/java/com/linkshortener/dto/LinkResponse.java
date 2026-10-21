package com.linkshortener.dto;

import java.time.Instant;

public record LinkResponse(
    String shortUrl,
    String shortKey,
    String originalUrl,
    Instant createdAt,
    Instant expiresAt
) {}
