package com.linkshortener.job;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TtlCleanupJob {

    @Scheduled(cron = "{app.cleanup.cron}")
    public void purgeExpiredLinks() {
        throw new UnsupportedOperationException("Not implemented: TtlCleanupJob.purgeExpiredLinks");
    }
}
