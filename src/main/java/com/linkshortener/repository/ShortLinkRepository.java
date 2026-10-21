package com.linkshortener.repository;

import com.linkshortener.domain.ShortLink;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class ShortLinkRepository implements PanacheRepository<ShortLink> {

    public Optional<ShortLink> findByShortKey(String shortKey) {
        throw new UnsupportedOperationException("Not implemented: ShortLinkRepository.findByShortKey");
    }

    public int deleteExpired(Instant now) {
        throw new UnsupportedOperationException("Not implemented: ShortLinkRepository.deleteExpired");
    }
}
