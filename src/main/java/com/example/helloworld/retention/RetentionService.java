package com.example.helloworld.retention;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies data-retention rules.
 *
 * <p><b>Provider content</b> (review text, ratings, reviewer names and photos,
 * interaction bodies, location contact details, raw API payloads) is cleared
 * when either:
 * <ul>
 *   <li>it has not been re-fetched from the provider within the provider's
 *       {@code content_retention_days} (30 days for Google Business Profile), or</li>
 *   <li>the connected account it came through was disconnected more than
 *       {@code disconnected_purge_days} ago (0 = on the next run).</li>
 * </ul>
 * The row itself, its provider identifiers and our own records that point at
 * it (drafts, publications, audit history) are kept, so a later sync restores
 * the content in place instead of creating a duplicate.
 *
 * <p><b>Operational data</b> (sync runs, sync errors, audit events) is deleted
 * after the ages set in {@link RetentionProperties}.
 */
@Service
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    /** Arbitrary constant key for the Postgres advisory lock that serializes runs across instances. */
    private static final long LOCK_KEY = 0x52455445_4E54494FL;

    public record Report(boolean ran, int locations, int reviews, int interactions,
                         int syncErrors, int syncRuns, int auditEvents) {
        static Report skipped() { return new Report(false, 0, 0, 0, 0, 0, 0); }
    }

    // Content is purged when it is stale for its provider, or its account has been disconnected.
    private static final String EXPIRED_VIA_LOCATION = """
              EXISTS (SELECT 1 FROM integration_providers p
                       WHERE p.code = t.provider_code
                         AND p.content_retention_days IS NOT NULL
                         AND t.provider_data_fetched_at < now() - make_interval(days => p.content_retention_days))
              OR EXISTS (SELECT 1 FROM business_locations l
                         JOIN connected_accounts a ON a.id = l.connected_account_id
                         JOIN integration_providers p ON p.code = a.provider_code
                         WHERE l.id = t.location_id
                           AND a.status = 'DISCONNECTED'
                           AND a.disconnected_at <= now() - make_interval(days => p.disconnected_purge_days))
            """;

    private static final String PURGE_REVIEWS = """
            UPDATE reviews t SET
                reviewer_display_name = NULL, reviewer_profile_photo_url = NULL, star_rating = NULL,
                comment = NULL, language_code = NULL, raw_payload = NULL,
                content_purged_at = now(), updated_at = now(), version = t.version + 1
            WHERE t.content_purged_at IS NULL AND (
            """ + EXPIRED_VIA_LOCATION + ")";

    private static final String PURGE_INTERACTIONS = """
            UPDATE interactions t SET
                author_display_name = NULL, body = NULL, raw_payload = NULL,
                content_purged_at = now(), updated_at = now(), version = t.version + 1
            WHERE t.content_purged_at IS NULL AND (
            """ + EXPIRED_VIA_LOCATION + ")";

    // The location name is kept as a label so drafts and history stay readable.
    private static final String PURGE_LOCATIONS = """
            UPDATE business_locations t SET
                address = NULL, phone = NULL, website_url = NULL, raw_payload = NULL,
                content_purged_at = now(), updated_at = now(), version = t.version + 1
            WHERE t.content_purged_at IS NULL AND (
              EXISTS (SELECT 1 FROM integration_providers p
                      WHERE p.code = t.provider_code
                        AND p.content_retention_days IS NOT NULL
                        AND t.provider_data_fetched_at < now() - make_interval(days => p.content_retention_days))
              OR EXISTS (SELECT 1 FROM connected_accounts a
                         JOIN integration_providers p ON p.code = a.provider_code
                         WHERE a.id = t.connected_account_id
                           AND a.status = 'DISCONNECTED'
                           AND a.disconnected_at <= now() - make_interval(days => p.disconnected_purge_days)))
            """;

    private final JdbcTemplate jdbc;
    private final RetentionProperties props;

    public RetentionService(JdbcTemplate jdbc, RetentionProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    @Transactional
    public Report run() {
        Boolean locked = jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)", Boolean.class, LOCK_KEY);
        if (!Boolean.TRUE.equals(locked)) {
            log.info("Retention run skipped: another instance holds the lock");
            return Report.skipped();
        }

        int reviews = jdbc.update(PURGE_REVIEWS);
        int interactions = jdbc.update(PURGE_INTERACTIONS);
        int locations = jdbc.update(PURGE_LOCATIONS);

        int syncErrors = jdbc.update(
                "DELETE FROM sync_errors WHERE occurred_at < now() - make_interval(days => ?)",
                props.syncErrorDays());
        int syncRuns = jdbc.update(
                "DELETE FROM sync_runs WHERE status <> 'RUNNING' AND started_at < now() - make_interval(days => ?)",
                props.syncRunDays());
        int auditEvents = jdbc.update(
                "DELETE FROM audit_events WHERE occurred_at < now() - make_interval(days => ?)",
                props.auditEventDays());

        var report = new Report(true, locations, reviews, interactions, syncErrors, syncRuns, auditEvents);
        log.info("Retention run finished: {}", report);
        return report;
    }
}
