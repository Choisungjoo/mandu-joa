package com.example.helloworld;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.helloworld.audit.AuditEvent;
import com.example.helloworld.audit.AuditEventRepository;
import com.example.helloworld.integration.BusinessLocation;
import com.example.helloworld.integration.BusinessLocationRepository;
import com.example.helloworld.integration.ConnectedAccount;
import com.example.helloworld.integration.ConnectedAccountRepository;
import com.example.helloworld.integration.IntegrationProvider;
import com.example.helloworld.organization.AppUser;
import com.example.helloworld.organization.AppUserRepository;
import com.example.helloworld.organization.Organization;
import com.example.helloworld.organization.OrganizationRepository;
import com.example.helloworld.retention.RetentionService;
import com.example.helloworld.review.ProviderRecordUpserter;
import com.example.helloworld.review.ProviderRecordUpserter.ReviewData;
import com.example.helloworld.review.ResponseDraft;
import com.example.helloworld.review.ResponseDraftRepository;
import com.example.helloworld.review.Review;
import com.example.helloworld.review.ReviewRepository;
import com.example.helloworld.sync.PollingConfiguration;
import com.example.helloworld.sync.PollingConfigurationRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "app.retention.enabled=false")
@Import(PostgresTestConfiguration.class)
class PersistenceIntegrationTest {

    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrganizationRepository organizations;
    @Autowired AppUserRepository users;
    @Autowired ConnectedAccountRepository accounts;
    @Autowired BusinessLocationRepository locations;
    @Autowired ReviewRepository reviews;
    @Autowired ResponseDraftRepository drafts;
    @Autowired PollingConfigurationRepository pollingConfigurations;
    @Autowired AuditEventRepository auditEvents;
    @Autowired ProviderRecordUpserter upserter;
    @Autowired RetentionService retention;

    Organization org;
    ConnectedAccount account;
    BusinessLocation location;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        org = organizations.save(new Organization("Acme", "acme-" + suffix));
        account = accounts.save(new ConnectedAccount(org, IntegrationProvider.GOOGLE_BUSINESS_PROFILE, "accounts/" + suffix));
        location = new BusinessLocation(org, account, "locations/" + suffix);
        location.setName("Acme Downtown");
        location = locations.save(location);
    }

    @Test
    void migrationsAreAppliedAutomatically() {
        assertThat(flyway.info().applied()).hasSize(5);
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT content_retention_days FROM integration_providers WHERE code = 'GOOGLE_BUSINESS_PROFILE'",
                Integer.class)).isEqualTo(30);
    }

    @Test
    void duplicateExternalRecordsAreRejected() {
        assertThatThrownBy(() -> accounts.saveAndFlush(
                new ConnectedAccount(org, account.getProviderCode(), account.getExternalAccountId())))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> locations.saveAndFlush(
                new BusinessLocation(org, account, location.getExternalLocationId())))
                .isInstanceOf(DataIntegrityViolationException.class);

        reviews.saveAndFlush(new Review(location, "review-1"));
        assertThatThrownBy(() -> reviews.saveAndFlush(new Review(location, "review-1")))
                .isInstanceOf(DataIntegrityViolationException.class);

        pollingConfigurations.saveAndFlush(PollingConfiguration.forAccount(account));
        assertThatThrownBy(() -> pollingConfigurations.saveAndFlush(PollingConfiguration.forAccount(account)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameExternalAccountCanBeConnectedByDifferentOrganizations() {
        var other = organizations.save(new Organization("Other", "other-" + UUID.randomUUID()));
        accounts.saveAndFlush(new ConnectedAccount(other, account.getProviderCode(), account.getExternalAccountId()));
        assertThat(accounts.findByOrganizationId(other.getId())).hasSize(1);
    }

    @Test
    void emailsAreUniqueIgnoringCase() {
        String email = "owner-" + UUID.randomUUID() + "@example.com";
        users.saveAndFlush(new AppUser(email, "Owner"));
        assertThatThrownBy(() -> users.saveAndFlush(new AppUser(email.toUpperCase(), "Owner again")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void upsertingTheSameReviewTwiceUpdatesInsteadOfDuplicating() {
        var first = upserter.upsertReview(review("rev-42", "Great coffee", (short) 5));
        var second = upserter.upsertReview(review("rev-42", "Great coffee, slow service", (short) 4));

        assertThat(first.inserted()).isTrue();
        assertThat(second.inserted()).isFalse();
        assertThat(second.id()).isEqualTo(first.id());

        var stored = reviews.findById(first.id()).orElseThrow();
        assertThat(stored.getComment()).isEqualTo("Great coffee, slow service");
        assertThat(stored.getStarRating()).isEqualTo((short) 4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reviews WHERE location_id = ?", Long.class,
                location.getId())).isEqualTo(1);
    }

    @Test
    void onlyOneOpenDraftPerReview() {
        var review = reviews.saveAndFlush(new Review(location, "rev-draft"));
        drafts.saveAndFlush(new ResponseDraft(review, "Thanks!", ResponseDraft.Source.MANUAL, null));
        assertThatThrownBy(() -> drafts.saveAndFlush(
                new ResponseDraft(review, "Thank you!", ResponseDraft.Source.AI_GENERATED, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void retentionPurgesStaleProviderContentButKeepsIdentifiers() {
        var stale = upserter.upsertReview(review("rev-old", "Old review text", (short) 2));
        var fresh = upserter.upsertReview(review("rev-new", "New review text", (short) 5));
        jdbc.update("UPDATE reviews SET provider_data_fetched_at = now() - interval '31 days' WHERE id = ?", stale.id());

        retention.run();

        Map<String, Object> purged = jdbc.queryForMap(
                "SELECT external_review_id, comment, star_rating, reviewer_display_name, raw_payload, content_purged_at "
                        + "FROM reviews WHERE id = ?", stale.id());
        assertThat(purged.get("external_review_id")).isEqualTo("rev-old");
        assertThat(purged.get("comment")).isNull();
        assertThat(purged.get("star_rating")).isNull();
        assertThat(purged.get("reviewer_display_name")).isNull();
        assertThat(purged.get("raw_payload")).isNull();
        assertThat(purged.get("content_purged_at")).isNotNull();

        assertThat(reviews.findById(fresh.id()).orElseThrow().getComment()).isEqualTo("New review text");

        // A later sync restores the content into the same row.
        var resynced = upserter.upsertReview(review("rev-old", "Old review text", (short) 2));
        assertThat(resynced.id()).isEqualTo(stale.id());
        var restored = reviews.findById(stale.id()).orElseThrow();
        assertThat(restored.getComment()).isEqualTo("Old review text");
        assertThat(restored.getContentPurgedAt()).isNull();
    }

    @Test
    void retentionPurgesContentOfDisconnectedAccounts() {
        var r = upserter.upsertReview(review("rev-disc", "Some text", (short) 3));
        account = accounts.findById(account.getId()).orElseThrow();
        account.disconnect(Instant.now().minusSeconds(60));
        accounts.saveAndFlush(account);

        retention.run();

        assertThat(reviews.findById(r.id()).orElseThrow().getComment()).isNull();
        assertThat(jdbc.queryForObject("SELECT content_purged_at FROM business_locations WHERE id = ?",
                java.time.OffsetDateTime.class, location.getId())).isNotNull();
    }

    @Test
    void auditEventsAreAppendOnly() {
        var event = auditEvents.save(AuditEvent.bySystem(org.getId(), "review.imported", "review", UUID.randomUUID(),
                "{\"source\":\"test\"}"));
        assertThatThrownBy(() -> jdbc.update("UPDATE audit_events SET action = 'tampered' WHERE id = ?", event.getId()))
                .hasMessageContaining("append-only");
        assertThat(jdbc.update("DELETE FROM audit_events WHERE id = ?", event.getId())).isEqualTo(1);
    }

    private ReviewData review(String externalId, String comment, short stars) {
        return new ReviewData(org.getId(), location.getId(), IntegrationProvider.GOOGLE_BUSINESS_PROFILE,
                externalId, "accounts/1/locations/2/reviews/" + externalId,
                "Jane Reviewer", null, false, stars, comment, "en",
                Instant.parse("2026-11-01T10:00:00Z"), null, Review.ReplyStatus.UNANSWERED,
                "{\"reviewId\":\"" + externalId + "\"}");
    }
}
