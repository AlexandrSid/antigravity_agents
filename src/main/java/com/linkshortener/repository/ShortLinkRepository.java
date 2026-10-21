package com.linkshortener.repository;

import com.linkshortener.domain.ShortLink;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class ShortLinkRepository implements PanacheRepository<ShortLink> {

    public Optional<ShortLink> findByShortKey(String shortKey) {
        return find("shortKey", shortKey).firstResultOptional();
    }

    public int deleteExpired(Instant now) {
        return (int) delete("expiresAt < ?1", now);
    }
}
