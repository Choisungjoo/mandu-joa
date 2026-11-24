package com.example.helloworld.review;

import com.example.helloworld.integration.BusinessLocation;
import com.example.helloworld.organization.Organization;
import com.example.helloworld.persistence.BaseEntity;
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

/** A non-review interaction from a provider: owner replies, comments, questions and answers, reactions. */
@Entity
@Table(name = "interactions")
public class Interaction extends BaseEntity {

    public enum Type { OWNER_REPLY, CUSTOMER_COMMENT, QUESTION, ANSWER, REACTION }

    public enum AuthorType { OWNER, CUSTOMER, PROVIDER, UNKNOWN }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private BusinessLocation location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id")
    private Review review;

    @Column(name = "provider_code", nullable = false, length = 50)
    private String providerCode;

    @Column(name = "external_interaction_id", nullable = false, length = 512)
    private String externalInteractionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "interaction_type", nullable = false, length = 30)
    private Type interactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_type", nullable = false, length = 20)
    private AuthorType authorType = AuthorType.UNKNOWN;

    @Column(name = "author_display_name")
    private String authorDisplayName;

    @Column(columnDefinition = "text")
    private String body;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;

    @Column(name = "provider_data_fetched_at", nullable = false)
    private Instant providerDataFetchedAt = Instant.now();

    @Column(name = "removed_at_provider_at")
    private Instant removedAtProviderAt;

    @Column(name = "content_purged_at")
    private Instant contentPurgedAt;

    protected Interaction() {}

    public Interaction(BusinessLocation location, Type type, String externalInteractionId) {
        this.organization = location.getOrganization();
        this.location = location;
        this.providerCode = location.getProviderCode();
        this.interactionType = type;
        this.externalInteractionId = externalInteractionId;
    }

    public Organization getOrganization() { return organization; }
    public BusinessLocation getLocation() { return location; }
    public Review getReview() { return review; }
    public void setReview(Review review) { this.review = review; }
    public String getProviderCode() { return providerCode; }
    public String getExternalInteractionId() { return externalInteractionId; }
    public Type getInteractionType() { return interactionType; }
    public AuthorType getAuthorType() { return authorType; }
    public void setAuthorType(AuthorType authorType) { this.authorType = authorType; }
    public String getAuthorDisplayName() { return authorDisplayName; }
    public void setAuthorDisplayName(String name) { this.authorDisplayName = name; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }
    public Instant getProviderDataFetchedAt() { return providerDataFetchedAt; }
    public void setProviderDataFetchedAt(Instant at) { this.providerDataFetchedAt = at; }
    public Instant getRemovedAtProviderAt() { return removedAtProviderAt; }
    public void setRemovedAtProviderAt(Instant at) { this.removedAtProviderAt = at; }
    public Instant getContentPurgedAt() { return contentPurgedAt; }
}
