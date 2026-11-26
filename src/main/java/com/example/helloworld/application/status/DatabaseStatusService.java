package com.example.helloworld.application.status;

import com.example.helloworld.application.retention.RetentionService;
import com.example.helloworld.application.status.DatabaseStatus.Connection;
import com.example.helloworld.application.status.DatabaseStatus.Health;
import com.example.helloworld.application.status.DatabaseStatus.Migration;
import com.example.helloworld.application.status.DatabaseStatus.Migrations;
import com.example.helloworld.application.status.DatabaseStatus.Pool;
import com.example.helloworld.application.status.DatabaseStatus.ProviderRetention;
import com.example.helloworld.application.status.DatabaseStatus.RecentError;
import com.example.helloworld.application.status.DatabaseStatus.Retention;
import com.example.helloworld.application.status.DatabaseStatus.Sync;
import com.example.helloworld.application.status.DatabaseStatus.TableStat;
import com.example.helloworld.retention.RetentionProperties;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

/**
 * Collects a read-only health snapshot of the database. Every section is
 * gathered independently, so one failing query degrades that section instead
 * of the whole screen.
 */
@Service
public class DatabaseStatusService {

    private static final int FAILING_THRESHOLD = 3;

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    private final Flyway flyway;
    private final RetentionProperties retentionProps;
    private final RetentionService retentionService;

    public DatabaseStatusService(JdbcTemplate jdbc, DataSource dataSource, Flyway flyway,
                                 RetentionProperties retentionProps, RetentionService retentionService) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
        this.flyway = flyway;
        this.retentionProps = retentionProps;
        this.retentionService = retentionService;
    }

    public DatabaseStatus snapshot() {
        Instant now = Instant.now();
        Connection connection = connection();
        if (!connection.up()) {
            return new DatabaseStatus(now, Health.DOWN, List.of("Database is unreachable: " + connection.error()),
                    connection, null, List.of(), retention(false), null);
        }

        Migrations migrations = migrations();
        List<TableStat> tables = safe(this::tables, List.of());
        Retention retention = retention(true);
        Sync sync = safe(this::sync, null);

        List<String> warnings = new ArrayList<>();
        if (migrations.error() != null) warnings.add("Could not read migration history: " + migrations.error());
        if (migrations.failed() > 0) warnings.add(migrations.failed() + " migration(s) failed");
        if (migrations.pending() > 0) warnings.add(migrations.pending() + " migration(s) pending");
        if (connection.pool() != null && connection.pool().waiting() > 0) {
            warnings.add(connection.pool().waiting() + " thread(s) waiting for a database connection");
        }
        if (!retention.enabled()) warnings.add("Scheduled retention is disabled");
        if (retention.lastRunError() != null) warnings.add("Last retention run failed: " + retention.lastRunError());
        if (sync != null && sync.failingConfigs() > 0) {
            warnings.add(sync.failingConfigs() + " polling configuration(s) failing repeatedly");
        }
        if (sync == null) warnings.add("Could not read sync status");

        Health health = warnings.isEmpty() ? Health.OK : Health.WARNING;
        return new DatabaseStatus(now, health, warnings, connection, migrations, tables, retention, sync);
    }

    private Connection connection() {
        try {
            long start = System.nanoTime();
            Map<String, Object> row = jdbc.queryForMap("""
                    SELECT current_setting('server_version')        AS version,
                           current_database()                       AS db,
                           pg_database_size(current_database())     AS size,
                           pg_postmaster_start_time()               AS started
                    """);
            long latencyMs = (System.nanoTime() - start) / 1_000_000;
            return new Connection(true, null, latencyMs, (String) row.get("version"), (String) row.get("db"),
                    ((Number) row.get("size")).longValue(), toInstant(row.get("started")), pool());
        } catch (Exception e) {
            return new Connection(false, rootMessage(e), null, null, null, null, null, pool());
        }
    }

    private Pool pool() {
        if (dataSource instanceof HikariDataSource hikari && hikari.getHikariPoolMXBean() != null) {
            var mx = hikari.getHikariPoolMXBean();
            return new Pool(mx.getActiveConnections(), mx.getIdleConnections(), mx.getTotalConnections(),
                    hikari.getMaximumPoolSize(), mx.getThreadsAwaitingConnection());
        }
        return null;
    }

    private Migrations migrations() {
        try {
            var info = flyway.info();
            List<Migration> items = Arrays.stream(info.all())
                    .map(m -> new Migration(
                            m.getVersion() == null ? null : m.getVersion().getVersion(),
                            m.getDescription(), m.getState().getDisplayName(),
                            m.getInstalledOn() == null ? null : m.getInstalledOn().toInstant(),
                            m.getExecutionTime()))
                    .toList();
            MigrationInfo current = info.current();
            int failed = (int) Arrays.stream(info.all()).filter(m -> m.getState().isFailed()).count();
            int applied = (int) Arrays.stream(info.all()).filter(m -> m.getState() == MigrationState.SUCCESS).count();
            return new Migrations(current == null ? null : current.getVersion().getVersion(),
                    applied, info.pending().length, failed, items, null);
        } catch (Exception e) {
            return new Migrations(null, 0, 0, 0, List.of(), rootMessage(e));
        }
    }

    private List<TableStat> tables() {
        return jdbc.query("""
                SELECT relname,
                       n_live_tup,
                       pg_total_relation_size(relid) AS total_bytes,
                       greatest(last_analyze, last_autoanalyze) AS analyzed
                FROM pg_stat_user_tables
                WHERE schemaname = current_schema()
                ORDER BY relname
                """, (rs, i) -> new TableStat(rs.getString("relname"), rs.getLong("n_live_tup"),
                rs.getLong("total_bytes"), instant(rs, "analyzed")));
    }

    private Retention retention(boolean dbUp) {
        Instant nextRun = null;
        try {
            ZonedDateTime next = CronExpression.parse(retentionProps.cron()).next(ZonedDateTime.now());
            nextRun = next == null ? null : next.toInstant();
        } catch (IllegalArgumentException ignored) {
            // Invalid cron is reported by the scheduler at startup.
        }
        var last = retentionService.lastRun();
        Map<String, Integer> lastCounts = null;
        if (last != null && last.report() != null) {
            var r = last.report();
            lastCounts = new LinkedHashMap<>();
            lastCounts.put("reviews", r.reviews());
            lastCounts.put("interactions", r.interactions());
            lastCounts.put("locations", r.locations());
            lastCounts.put("syncErrors", r.syncErrors());
            lastCounts.put("syncRuns", r.syncRuns());
            lastCounts.put("auditEvents", r.auditEvents());
        }
        List<ProviderRetention> providers = dbUp ? safe(this::providerRetention, List.of()) : List.of();
        return new Retention(retentionProps.enabled(), retentionProps.cron(), nextRun,
                last == null ? null : last.at(), lastCounts, last == null ? null : last.error(),
                providers, retentionProps.syncRunDays(), retentionProps.syncErrorDays(),
                retentionProps.auditEventDays());
    }

    private List<ProviderRetention> providerRetention() {
        // Counts across reviews, interactions and locations: content still held,
        // content already purged, and content past its provider's time limit.
        return jdbc.query("""
                WITH content AS (
                    SELECT provider_code, provider_data_fetched_at, content_purged_at FROM reviews
                    UNION ALL
                    SELECT provider_code, provider_data_fetched_at, content_purged_at FROM interactions
                    UNION ALL
                    SELECT provider_code, provider_data_fetched_at, content_purged_at FROM business_locations
                )
                SELECT p.code, p.display_name, p.content_retention_days, p.disconnected_purge_days,
                       count(c.*) FILTER (WHERE c.content_purged_at IS NULL)     AS live,
                       count(c.*) FILTER (WHERE c.content_purged_at IS NOT NULL) AS purged,
                       count(c.*) FILTER (WHERE c.content_purged_at IS NULL
                                            AND p.content_retention_days IS NOT NULL
                                            AND c.provider_data_fetched_at
                                                < now() - make_interval(days => p.content_retention_days)) AS due
                FROM integration_providers p
                LEFT JOIN content c ON c.provider_code = p.code
                GROUP BY p.code, p.display_name, p.content_retention_days, p.disconnected_purge_days
                ORDER BY p.code
                """, (rs, i) -> new ProviderRetention(rs.getString("code"), rs.getString("display_name"),
                (Integer) rs.getObject("content_retention_days"), rs.getInt("disconnected_purge_days"),
                rs.getLong("live"), rs.getLong("purged"), rs.getLong("due")));
    }

    private Sync sync() {
        Map<String, Object> configs = jdbc.queryForMap("""
                SELECT count(*)                                          AS total,
                       count(*) FILTER (WHERE enabled)                   AS enabled,
                       count(*) FILTER (WHERE enabled AND next_run_at <= now()) AS due,
                       count(*) FILTER (WHERE consecutive_failures >= ?) AS failing
                FROM polling_configurations
                """, FAILING_THRESHOLD);

        Map<String, Long> runs = new LinkedHashMap<>();
        for (String s : List.of("RUNNING", "SUCCEEDED", "PARTIAL", "FAILED")) runs.put(s, 0L);
        jdbc.query("SELECT status, count(*) AS n FROM sync_runs WHERE started_at > now() - interval '24 hours' GROUP BY status",
                rs -> { runs.put(rs.getString("status"), rs.getLong("n")); });

        Long errors = jdbc.queryForObject(
                "SELECT count(*) FROM sync_errors WHERE occurred_at > now() - interval '24 hours'", Long.class);

        List<RecentError> recent = jdbc.query("""
                SELECT occurred_at, provider_code, error_code, http_status, left(message, 300) AS message
                FROM sync_errors ORDER BY occurred_at DESC LIMIT 10
                """, (rs, i) -> new RecentError(instant(rs, "occurred_at"), rs.getString("provider_code"),
                rs.getString("error_code"), (Integer) rs.getObject("http_status"), rs.getString("message")));

        return new Sync(num(configs.get("total")), num(configs.get("enabled")), num(configs.get("due")),
                num(configs.get("failing")), runs, errors == null ? 0 : errors, recent);
    }

    private static <T> T safe(java.util.function.Supplier<T> s, T fallback) {
        try {
            return s.get();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static long num(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }

    private static Instant instant(ResultSet rs, String col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toInstant();
    }

    private static Instant toInstant(Object o) {
        if (o instanceof Timestamp t) return t.toInstant();
        if (o instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        return null;
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }
}
