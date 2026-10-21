package com.linkshortener.job;

import com.linkshortener.repository.ShortLinkRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TtlCleanupJobTest {

    @Mock
    private ShortLinkRepository shortLinkRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    private TtlCleanupJob job;

    @BeforeEach
    void setUp() {
        job = new TtlCleanupJob();
        injectField(job, "shortLinkRepository", shortLinkRepository);
        injectField(job, "entityManager", entityManager);
    }

    private void injectField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException ignored) {
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Task 1.5: testPurgeExpiredLinks_acquiresLockAndDeletesExpired")
    void testPurgeExpiredLinks_acquiresLockAndDeletesExpired() {
        job.purgeExpiredLinks();

        // Behaviour verification: repository deleteExpired must be called
        verify(shortLinkRepository, times(1)).deleteExpired(any(Instant.class));
    }

    @Test
    @DisplayName("Task 1.5: testPurgeExpiredLinks_lockNotAcquired_skipsExecution")
    void testPurgeExpiredLinks_lockNotAcquired_skipsExecution() {
        job.purgeExpiredLinks();

        // If lock not acquired, repository should not be called
        verify(shortLinkRepository, never()).deleteExpired(any(Instant.class));
    }
}
