package com.example.helloworld.retention;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs {@link RetentionService} on {@code app.retention.cron} (default: daily at 03:30). */
@Component
@ConditionalOnProperty(prefix = "app.retention", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RetentionScheduler {

    private final RetentionService retentionService;

    public RetentionScheduler(RetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @Scheduled(cron = "${app.retention.cron:0 30 3 * * *}")
    public void runRetention() {
        try {
            retentionService.run();
        } catch (RuntimeException e) {
            retentionService.recordFailure(e);
            throw e;
        }
    }
}
