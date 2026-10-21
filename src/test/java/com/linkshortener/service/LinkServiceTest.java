package com.linkshortener.service;

import com.linkshortener.cache.RedisLinkCache;
import com.linkshortener.domain.ShortLink;
import com.linkshortener.dto.CreateLinkRequest;
import com.linkshortener.dto.LinkResponse;
import com.linkshortener.exception.LinkNotFoundException;
import com.linkshortener.keygen.ShortKeyGenerator;
import com.linkshortener.repository.ShortLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkServiceTest {

    @Mock
    private ShortLinkRepository shortLinkRepository;

    @Mock
    private RedisLinkCache redisLinkCache;

    @Mock
    private ShortKeyGenerator shortKeyGenerator;

    private LinkServiceImpl linkService;

    @BeforeEach
    void setUp() throws Exception {
        linkService = new LinkServiceImpl();

        // Inject mocks via reflection to support standard CDI field injection
        injectField(linkService, "shortLinkRepository", shortLinkRepository);
        injectField(linkService, "redisLinkCache", redisLinkCache);
        injectField(linkService, "shortKeyGenerator", shortKeyGenerator);
        injectField(linkService, "baseUrl", "https://shortner.url");
    }

    private void injectField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException ignored) {
            // Skeletons might not have the fields declared yet
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    // --- Task 1.3: Link Creation & Idempotency Domain Service ---

    @Test
    @DisplayName("Task 1.3: testCreateShortLink_newUrl_reservesIdAndPersists")
    void testCreateShortLink_newUrl_reservesIdAndPersists() {
        String url = "https://example.com/unique-url";
        CreateLinkRequest request = new CreateLinkRequest(url);

        LinkResponse response = linkService.createShortLink(request);

        assertNotNull(response);
        assertEquals("aB3dEfGh", response.shortKey());
        assertEquals("https://shortner.url/aB3dEfGh", response.shortUrl());
        assertEquals(url, response.originalUrl());

        // Mandatory interaction assertions (Behaviour verification)
        verify(shortLinkRepository, times(1)).persist(any(ShortLink.class));
        verify(redisLinkCache, times(1)).putShortToLong(eq("aB3dEfGh"), eq(url), eq(3600L));
        verify(redisLinkCache, times(1)).putLongToShort(eq(url), eq("aB3dEfGh"), eq(1800L));
    }

    @Test
    @DisplayName("Task 1.3: testCreateShortLink_idempotentHit_returnsExistingAndExtendsTtl")
    void testCreateShortLink_idempotentHit_returnsExistingAndExtendsTtl() {
        String url = "https://example.com/existing-url";
        CreateLinkRequest request = new CreateLinkRequest(url);

        LinkResponse response = linkService.createShortLink(request);

        assertNotNull(response);
        assertEquals("aB3dEfGh", response.shortKey());

        // Key generator must NOT be invoked on idempotent hit
        verify(shortKeyGenerator, never()).generateKey(anyLong());
        verify(redisLinkCache, times(1)).refreshShortToLongTtl(eq("aB3dEfGh"), eq(300L));
    }

    @Test
    @DisplayName("Task 1.3: testCreateShortLink_expiredIdempotency_generatesNewLink")
    void testCreateShortLink_expiredIdempotency_generatesNewLink() {
        String url = "https://example.com/expired-idempotency";
        CreateLinkRequest request = new CreateLinkRequest(url);

        LinkResponse response = linkService.createShortLink(request);

        assertNotNull(response);
        verify(shortLinkRepository, times(1)).persist(any(ShortLink.class));
    }

    @Test
    @DisplayName("Task 1.3: testCreateShortLink_invalidUrlThrowsValidationException")
    void testCreateShortLink_invalidUrlThrowsValidationException() {
        CreateLinkRequest request = new CreateLinkRequest("not-a-valid-url");
        assertThrows(IllegalArgumentException.class, () -> linkService.createShortLink(request));
    }

    // --- Task 1.4: Link Resolution & Cache-Aside Domain Service ---

    @Test
    @DisplayName("Task 1.4: testResolve_cacheHit_refreshesTtlAndReturnsUrl")
    void testResolve_cacheHit_refreshesTtlAndReturnsUrl() {
        String shortKey = "aB3dEfGh";
        String resolvedUrl = linkService.resolveOriginalUrl(shortKey);

        assertEquals("https://example.com/target", resolvedUrl);
        verify(redisLinkCache, times(1)).refreshShortToLongTtl(eq(shortKey), eq(300L));
        verify(shortLinkRepository, never()).findByShortKey(anyString());
    }

    @Test
    @DisplayName("Task 1.4: testResolve_cacheMiss_readsDbAndPopulatesCacheWith5MinTtl")
    void testResolve_cacheMiss_readsDbAndPopulatesCacheWith5MinTtl() {
        String shortKey = "aB3dEfGh";
        String resolvedUrl = linkService.resolveOriginalUrl(shortKey);

        assertEquals("https://example.com/db-target", resolvedUrl);
        verify(shortLinkRepository, times(1)).findByShortKey(eq(shortKey));
        verify(redisLinkCache, times(1)).putShortToLong(eq(shortKey), eq("https://example.com/db-target"), eq(300L));
    }

    @Test
    @DisplayName("Task 1.4: testResolve_linkExpiredInDb_throwsNotFoundException")
    void testResolve_linkExpiredInDb_throwsNotFoundException() {
        String shortKey = "expired1";
        assertThrows(LinkNotFoundException.class, () -> linkService.resolveOriginalUrl(shortKey));
    }

    @Test
    @DisplayName("Task 1.4: testResolve_linkNotFoundInDb_throwsNotFoundException")
    void testResolve_linkNotFoundInDb_throwsNotFoundException() {
        String shortKey = "unknown1";
        assertThrows(LinkNotFoundException.class, () -> linkService.resolveOriginalUrl(shortKey));
    }
}
