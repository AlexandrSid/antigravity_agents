package com.linkshortener.service;

import com.linkshortener.dto.CreateLinkRequest;
import com.linkshortener.dto.LinkResponse;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class LinkServiceImpl implements LinkService {

    @Override
    public LinkResponse createShortLink(CreateLinkRequest request) {
        throw new UnsupportedOperationException("Not implemented: LinkService.createShortLink");
    }

    @Override
    public String resolveOriginalUrl(String shortKey) {
        throw new UnsupportedOperationException("Not implemented: LinkService.resolveOriginalUrl");
    }
}
