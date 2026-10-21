package com.linkshortener.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "short_links")
public class ShortLink extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "short_links_seq_gen")
    @SequenceGenerator(name = "short_links_seq_gen", sequenceName = "short_links_seq", allocationSize = 1000)
    public Long id;

    @Column(name = "short_key", length = 8, nullable = false, unique = true)
    public String shortKey;

    @Column(name = "original_url", length = 2048, nullable = false)
    public String originalUrl;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;

    public ShortLink() {}

    public ShortLink(Long id, String shortKey, String originalUrl, Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.shortKey = shortKey;
        this.originalUrl = originalUrl;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }
}
