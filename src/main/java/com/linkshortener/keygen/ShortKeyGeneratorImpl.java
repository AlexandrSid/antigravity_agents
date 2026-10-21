package com.linkshortener.keygen;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.OptionalLong;

@ApplicationScoped
public class ShortKeyGeneratorImpl implements ShortKeyGenerator {

    @Override
    public String generateKey(long id) {
        throw new UnsupportedOperationException("Not implemented: ShortKeyGenerator.generateKey");
    }

    @Override
    public OptionalLong decodeKey(String shortKey) {
        throw new UnsupportedOperationException("Not implemented: ShortKeyGenerator.decodeKey");
    }
}
