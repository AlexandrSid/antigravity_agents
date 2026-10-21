package com.linkshortener.keygen;

import java.util.OptionalLong;

public interface ShortKeyGenerator {

    String generateKey(long id);

    OptionalLong decodeKey(String shortKey);
}
