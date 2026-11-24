package com.example.helloworld.review;

import com.example.helloworld.organization.AppUser;
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
import java.util.UUID;

/**
 * One request to publish an approved draft to the provider, with its retry
 * state. Only one publication per review may be QUEUED or IN_PROGRESS.
 */
@Entity
@Table(name = "response_publications")
public class ResponsePublication extends BaseEntity {

    public enum Status { QUEUED, IN_PROGRESS, SUCCEEDED, FAILED, CANCELLED }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "draft_id", nullable = false)
    private ResponseDraft draft;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.QUEUED;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "published_body", columnDefinition = "text")
    private String publishedBody;

    @Column(name = "external_reply_id", length = 512)
    private String externalReplyId;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "last_error_message", columnDefinition = "text")
    private String lastErrorMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_user_id")
    private AppUser requestedBy;

    protected ResponsePublication() {}

    public ResponsePublication(ResponseDraft draft, AppUser requestedBy) {
        this.organization = draft.getOrganization();
        this.draft = draft;
        this.review = draft.getReview();
        this.requestedBy = requestedBy;
        this.publishedBody = draft.getBody();
        this.idempotencyKey = UUID.randomUUID().toString();
        this.nextAttemptAt = Instant.now();
    }

    public void markInProgress() {
        this.status = Status.IN_PROGRESS;
        this.attemptCount++;
    }

    public void markSucceeded(String externalReplyId, Instant at) {
        this.status = Status.SUCCEEDED;
        this.externalReplyId = externalReplyId;
        this.publishedAt = at;
        this.nextAttemptAt = null;
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
    }

    /** Records a failure; pass a retry time to requeue, or {@code null} to give up. */
    public void markFailed(String errorCode, String message, Instant retryAt) {
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = message;
        this.nextAttemptAt = retryAt;
        this.status = retryAt == null ? Status.FAILED : Status.QUEUED;
    }

    public Organization getOrganization() { return organization; }
    public ResponseDraft getDraft() { return draft; }
    public Review getReview() { return review; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getPublishedBody() { return publishedBody; }
    public String getExternalReplyId() { return externalReplyId; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getLastErrorCode() { return lastErrorCode; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public AppUser getRequestedBy() { return requestedBy; }
}
