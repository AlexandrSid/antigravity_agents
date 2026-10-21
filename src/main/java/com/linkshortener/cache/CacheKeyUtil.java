package com.linkshortener.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class CacheKeyUtil {

    private static final String SHORT_PREFIX = "short:";
    private static final String LONG_PREFIX = "long:";

    private CacheKeyUtil() {}

    public static String shortKeyToLongKey(String shortKey) {
        if (shortKey == null || shortKey.isBlank()) {
            throw new IllegalArgumentException("shortKey must not be blank");
        }
        return SHORT_PREFIX + shortKey;
    }

    public static String longUrlToShortKey(String longUrl) {
        if (longUrl == null || longUrl.isBlank()) {
            throw new IllegalArgumentException("longUrl must not be blank");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(longUrl.getBytes(StandardCharsets.UTF_8));
            return LONG_PREFIX + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
