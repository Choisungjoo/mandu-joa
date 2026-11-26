package com.example.helloworld.persistence.review;

import com.example.helloworld.persistence.BaseEntity;
import com.example.helloworld.persistence.integration.BusinessLocation;
import com.example.helloworld.persistence.organization.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.lang.Nullable;

/**
 * A review imported from a provider. Write synced reviews through
 * {@link ProviderRecordUpserter} so that re-imports never create duplicates.
 */
@Entity
@Table(name = "reviews")
public class Review extends BaseEntity {

    public enum ReplyStatus { UNANSWERED, ANSWERED }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private BusinessLocation location;

    @Column(name = "provider_code", nullable = false, length = 50)
    private String providerCode;

    @Column(name = "external_review_id", nullable = false)
    private String externalReviewId;

    @Column(name = "external_resource_name", length = 512)
    private String externalResourceName;

    @Column(name = "reviewer_display_name")
    private String reviewerDisplayName;

    @Column(name = "reviewer_profile_photo_url", length = 2048)
    private String reviewerProfilePhotoUrl;

    @Column(name = "reviewer_is_anonymous", nullable = false)
    private boolean reviewerAnonymous;

    @Column(name = "star_rating")
    private Short starRating;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(name = "language_code", length = 20)
    private String languageCode;

    @Column(name = "review_created_at")
    private Instant reviewCreatedAt;

    @Column(name = "review_updated_at")
    private Instant reviewUpdatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "reply_status", nullable = false, length = 20)
    private ReplyStatus replyStatus = ReplyStatus.UNANSWERED;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private Instant firstSeenAt = Instant.now();

    @Column(name = "provider_data_fetched_at", nullable = false)
    private Instant providerDataFetchedAt = Instant.now();

    @Column(name = "removed_at_provider_at")
    private Instant removedAtProviderAt;

    @Column(name = "content_purged_at")
    private Instant contentPurgedAt;

    protected Review() {}

    public Review(BusinessLocation location, String externalReviewId) {
        this.organization = location.getOrganization();
        this.location = location;
        this.providerCode = location.getProviderCode();
        this.externalReviewId = externalReviewId;
    }

    public Organization getOrganization() { return organization; }
    public BusinessLocation getLocation() { return location; }
    public String getProviderCode() { return providerCode; }
    public String getExternalReviewId() { return externalReviewId; }
    public @Nullable String getExternalResourceName() { return externalResourceName; }
    public void setExternalResourceName(String name) { this.externalResourceName = name; }
    public @Nullable String getReviewerDisplayName() { return reviewerDisplayName; }
    public void setReviewerDisplayName(String name) { this.reviewerDisplayName = name; }
    public @Nullable String getReviewerProfilePhotoUrl() { return reviewerProfilePhotoUrl; }
    public void setReviewerProfilePhotoUrl(String url) { this.reviewerProfilePhotoUrl = url; }
    public boolean isReviewerAnonymous() { return reviewerAnonymous; }
    public void setReviewerAnonymous(boolean anonymous) { this.reviewerAnonymous = anonymous; }
    public @Nullable Short getStarRating() { return starRating; }
    public void setStarRating(Short starRating) { this.starRating = starRating; }
    public @Nullable String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public @Nullable String getLanguageCode() { return languageCode; }
    public void setLanguageCode(String languageCode) { this.languageCode = languageCode; }
    public @Nullable Instant getReviewCreatedAt() { return reviewCreatedAt; }
    public void setReviewCreatedAt(Instant at) { this.reviewCreatedAt = at; }
    public @Nullable Instant getReviewUpdatedAt() { return reviewUpdatedAt; }
    public void setReviewUpdatedAt(Instant at) { this.reviewUpdatedAt = at; }
    public ReplyStatus getReplyStatus() { return replyStatus; }
    public void setReplyStatus(ReplyStatus replyStatus) { this.replyStatus = replyStatus; }
    public @Nullable String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public Instant getProviderDataFetchedAt() { return providerDataFetchedAt; }
    public void setProviderDataFetchedAt(Instant at) { this.providerDataFetchedAt = at; }
    public @Nullable Instant getRemovedAtProviderAt() { return removedAtProviderAt; }
    public void setRemovedAtProviderAt(Instant at) { this.removedAtProviderAt = at; }
    public @Nullable Instant getContentPurgedAt() { return contentPurgedAt; }
}
