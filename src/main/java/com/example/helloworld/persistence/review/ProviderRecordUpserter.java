package com.example.helloworld.persistence.review;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent writes for records pulled from a provider. Each call inserts the
 * record or updates the existing row with the same provider identifiers, in a
 * single statement, so concurrent or repeated syncs cannot create duplicates.
 *
 * <p>Every upsert refreshes {@code provider_data_fetched_at} and clears
 * {@code content_purged_at}: re-fetching from the provider restarts the
 * retention clock.
 */
@Repository
public class ProviderRecordUpserter {

    public record UpsertResult(UUID id, boolean inserted) {}

    public record LocationData(
            UUID organizationId, UUID connectedAccountId, String providerCode, String locationType,
            String externalLocationId, String externalResourceName, String externalPlaceId,
            String name, String address, String phone, String websiteUrl, String rawPayloadJson) {}

    public record ReviewData(
            UUID organizationId, UUID locationId, String providerCode,
            String externalReviewId, String externalResourceName,
            String reviewerDisplayName, String reviewerProfilePhotoUrl, boolean reviewerAnonymous,
            Short starRating, String comment, String languageCode,
            Instant reviewCreatedAt, Instant reviewUpdatedAt, Review.ReplyStatus replyStatus,
            String rawPayloadJson) {}

    private static final String UPSERT_LOCATION = """
            INSERT INTO business_locations (
                organization_id, connected_account_id, provider_code, location_type,
                external_location_id, external_resource_name, external_place_id,
                name, address, phone, website_url, raw_payload, provider_data_fetched_at)
            VALUES (
                :organizationId, :connectedAccountId, :providerCode, :locationType,
                :externalLocationId, :externalResourceName, :externalPlaceId,
                :name, :address, :phone, :websiteUrl, CAST(:rawPayload AS jsonb), now())
            ON CONFLICT ON CONSTRAINT uq_business_locations_external DO UPDATE SET
                connected_account_id     = EXCLUDED.connected_account_id,
                location_type            = EXCLUDED.location_type,
                external_resource_name   = EXCLUDED.external_resource_name,
                external_place_id        = EXCLUDED.external_place_id,
                name                     = EXCLUDED.name,
                address                  = EXCLUDED.address,
                phone                    = EXCLUDED.phone,
                website_url              = EXCLUDED.website_url,
                raw_payload              = EXCLUDED.raw_payload,
                status                   = CASE WHEN business_locations.status = 'REMOVED_AT_PROVIDER'
                                                THEN 'ACTIVE' ELSE business_locations.status END,
                provider_data_fetched_at = now(),
                content_purged_at        = NULL,
                updated_at               = now(),
                version                  = business_locations.version + 1
            RETURNING id, (xmax = 0) AS inserted
            """;

    private static final String UPSERT_REVIEW = """
            INSERT INTO reviews (
                organization_id, location_id, provider_code, external_review_id, external_resource_name,
                reviewer_display_name, reviewer_profile_photo_url, reviewer_is_anonymous,
                star_rating, comment, language_code, review_created_at, review_updated_at,
                reply_status, raw_payload, provider_data_fetched_at)
            VALUES (
                :organizationId, :locationId, :providerCode, :externalReviewId, :externalResourceName,
                :reviewerDisplayName, :reviewerProfilePhotoUrl, :reviewerAnonymous,
                :starRating, :comment, :languageCode, :reviewCreatedAt, :reviewUpdatedAt,
                :replyStatus, CAST(:rawPayload AS jsonb), now())
            ON CONFLICT ON CONSTRAINT uq_reviews_external DO UPDATE SET
                external_resource_name     = EXCLUDED.external_resource_name,
                reviewer_display_name      = EXCLUDED.reviewer_display_name,
                reviewer_profile_photo_url = EXCLUDED.reviewer_profile_photo_url,
                reviewer_is_anonymous      = EXCLUDED.reviewer_is_anonymous,
                star_rating                = EXCLUDED.star_rating,
                comment                    = EXCLUDED.comment,
                language_code              = EXCLUDED.language_code,
                review_created_at          = EXCLUDED.review_created_at,
                review_updated_at          = EXCLUDED.review_updated_at,
                reply_status               = EXCLUDED.reply_status,
                raw_payload                = EXCLUDED.raw_payload,
                provider_data_fetched_at   = now(),
                removed_at_provider_at     = NULL,
                content_purged_at          = NULL,
                updated_at                 = now(),
                version                    = reviews.version + 1
            RETURNING id, (xmax = 0) AS inserted
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ProviderRecordUpserter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public UpsertResult upsertLocation(LocationData d) {
        var params = new MapSqlParameterSource()
                .addValue("organizationId", d.organizationId())
                .addValue("connectedAccountId", d.connectedAccountId())
                .addValue("providerCode", d.providerCode())
                .addValue("locationType", d.locationType() == null ? "LOCATION" : d.locationType())
                .addValue("externalLocationId", d.externalLocationId())
                .addValue("externalResourceName", d.externalResourceName())
                .addValue("externalPlaceId", d.externalPlaceId())
                .addValue("name", d.name())
                .addValue("address", d.address())
                .addValue("phone", d.phone())
                .addValue("websiteUrl", d.websiteUrl())
                .addValue("rawPayload", d.rawPayloadJson());
        return run(UPSERT_LOCATION, params);
    }

    @Transactional
    public UpsertResult upsertReview(ReviewData d) {
        var params = new MapSqlParameterSource()
                .addValue("organizationId", d.organizationId())
                .addValue("locationId", d.locationId())
                .addValue("providerCode", d.providerCode())
                .addValue("externalReviewId", d.externalReviewId())
                .addValue("externalResourceName", d.externalResourceName())
                .addValue("reviewerDisplayName", d.reviewerDisplayName())
                .addValue("reviewerProfilePhotoUrl", d.reviewerProfilePhotoUrl())
                .addValue("reviewerAnonymous", d.reviewerAnonymous())
                .addValue("starRating", d.starRating())
                .addValue("comment", d.comment())
                .addValue("languageCode", d.languageCode())
                .addValue("reviewCreatedAt", ts(d.reviewCreatedAt()))
                .addValue("reviewUpdatedAt", ts(d.reviewUpdatedAt()))
                .addValue("replyStatus", (d.replyStatus() == null ? Review.ReplyStatus.UNANSWERED : d.replyStatus()).name())
                .addValue("rawPayload", d.rawPayloadJson());
        return run(UPSERT_REVIEW, params);
    }

    private UpsertResult run(String sql, MapSqlParameterSource params) {
        return jdbc.queryForObject(sql, params,
                (rs, i) -> new UpsertResult(rs.getObject("id", UUID.class), rs.getBoolean("inserted")));
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
