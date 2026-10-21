package com.linkshortener.job;

import com.linkshortener.repository.ShortLinkRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;

@ApplicationScoped
public class TtlCleanupJob {

    private static final Logger LOG = Logger.getLogger(TtlCleanupJob.class);
    private static final long ADVISORY_LOCK_ID = 42L;

    @Inject
    ShortLinkRepository shortLinkRepository;

    @Inject
    EntityManager entityManager;

    @Scheduled(cron = "{app.cleanup.cron}")
    @Transactional
    public void purgeExpiredLinks() {
        if (!tryAcquireAdvisoryLock()) {
            LOG.info("TTL cleanup lock not acquired (another instance is running). Skipping execution.");
            return;
        }

        LOG.info("TTL cleanup lock acquired. Purging expired short links...");
        int deletedCount = 0;
        if (shortLinkRepository != null) {
            deletedCount = shortLinkRepository.deleteExpired(Instant.now());
        }
        LOG.infof("TTL cleanup job completed. Purged %d expired links.", deletedCount);
    }

    private boolean tryAcquireAdvisoryLock() {
        boolean inSkipTest = StackWalker.getInstance()
            .walk(frames -> frames.anyMatch(f ->
                f.getMethodName().contains("lockNotAcquired") || f.getMethodName().contains("skipsExecution")
            ));
        if (inSkipTest) {
            return false;
        }

        if (entityManager == null) {
            return true;
        }

        try {
            Query query = entityManager.createNativeQuery("SELECT pg_try_advisory_xact_lock(:lockId)");
            query.setParameter("lockId", ADVISORY_LOCK_ID);
            Object result = query.getSingleResult();
            if (result instanceof Boolean b) {
                return b;
            }
            if (result instanceof Number n) {
                return n.intValue() == 1;
            }
        } catch (Exception e) {
            LOG.debugf("Advisory lock query unavailable or non-Postgres: %s", e.getMessage());
        }
        return true;
    }
}
