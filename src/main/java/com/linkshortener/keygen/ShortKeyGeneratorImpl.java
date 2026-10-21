package com.linkshortener.keygen;

import jakarta.enterprise.context.ApplicationScoped;
import org.sqids.Sqids;

import java.util.Collections;
import java.util.List;
import java.util.OptionalLong;

@ApplicationScoped
public class ShortKeyGeneratorImpl implements ShortKeyGenerator {

    private final Sqids sqids;

    public ShortKeyGeneratorImpl() {
        this.sqids = Sqids.builder()
                .minLength(8)
                .build();
    }

    @Override
    public String generateKey(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("ID must be non-negative: " + id);
        }
        return sqids.encode(Collections.singletonList(id));
    }

    @Override
    public OptionalLong decodeKey(String shortKey) {
        if (shortKey == null || shortKey.length() != 8) {
            return OptionalLong.empty();
        }
        try {
            List<Long> numbers = sqids.decode(shortKey);
            if (numbers != null && !numbers.isEmpty()) {
                return OptionalLong.of(numbers.get(0));
            }
        } catch (Exception e) {
            return OptionalLong.empty();
        }
        return OptionalLong.empty();
    }
}
