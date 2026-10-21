package com.linkshortener.cache;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

@ApplicationScoped
public class RedisLinkCache {

    public Optional<String> getLongUrl(String shortKey) {
        throw new UnsupportedOperationException("Not implemented: RedisLinkCache.getLongUrl");
    }

    public void putShortToLong(String shortKey, String longUrl, long ttlSeconds) {
        throw new UnsupportedOperationException("Not implemented: RedisLinkCache.putShortToLong");
    }

    public Optional<String> getShortKeyByLongUrl(String longUrl) {
        throw new UnsupportedOperationException("Not implemented: RedisLinkCache.getShortKeyByLongUrl");
    }

    public void putLongToShort(String longUrl, String shortKey, long ttlSeconds) {
        throw new UnsupportedOperationException("Not implemented: RedisLinkCache.putLongToShort");
    }

    public void refreshShortToLongTtl(String shortKey, long ttlSeconds) {
        throw new UnsupportedOperationException("Not implemented: RedisLinkCache.refreshShortToLongTtl");
    }
}
