package com.example.helloworld.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Snapshot of database health, rendered by the status page and returned as JSON. */
public record DatabaseStatus(
        Instant checkedAt,
        Health health,
        List<String> warnings,
        Connection connection,
        Migrations migrations,
        List<TableStat> tables,
        Retention retention,
        Sync sync) {

    public enum Health { OK, WARNING, DOWN }

    public record Connection(
            boolean up, String error, Long latencyMs, String serverVersion, String databaseName,
            Long databaseSizeBytes, Instant serverStartedAt, Pool pool) {}

    public record Pool(int active, int idle, int total, int max, int waiting) {}

    public record Migrations(
            String currentVersion, int applied, int pending, int failed, List<Migration> items, String error) {}

    public record Migration(
            String version, String description, String state, Instant installedOn, Integer executionTimeMs) {}

    public record TableStat(String name, long estimatedRows, long totalBytes, Instant lastAnalyzedAt) {}

    public record Retention(
            boolean enabled, String cron, Instant nextRunAt,
            Instant lastRunAt, Map<String, Integer> lastRunCounts, String lastRunError,
            List<ProviderRetention> providers,
            int syncRunDays, int syncErrorDays, int auditEventDays) {}

    public record ProviderRetention(
            String code, String displayName, Integer contentRetentionDays, int disconnectedPurgeDays,
            long liveRecords, long purgedRecords, long dueForPurge) {}

    public record Sync(
            long pollingConfigs, long enabledConfigs, long dueNow, long failingConfigs,
            Map<String, Long> runsLast24h, long errorsLast24h, List<RecentError> recentErrors) {}

    public record RecentError(
            Instant occurredAt, String providerCode, String errorCode, Integer httpStatus, String message) {}
}
