package com.linkshortener.service;

import com.linkshortener.dto.CreateLinkRequest;
import com.linkshortener.dto.LinkResponse;

public interface LinkService {

    LinkResponse createShortLink(CreateLinkRequest request);

    String resolveOriginalUrl(String shortKey);
}
