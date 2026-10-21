package com.linkshortener.dto;

import java.net.URI;
import java.util.List;

public record ProblemDetailResponse(
    URI type,
    String title,
    int status,
    String detail,
    URI instance,
    List<InvalidParam> invalidParams
) {
    public record InvalidParam(String name, String reason) {}
}
