package com.example.helloworld.persistence.review;

import com.example.helloworld.persistence.BaseEntity;
import com.example.helloworld.persistence.organization.AppUser;
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
import org.springframework.lang.Nullable;

/**
 * A reply being prepared for a review. The database allows only one open
 * draft (DRAFT, PENDING_APPROVAL or APPROVED) per review.
 */
@Entity
@Table(name = "response_drafts")
public class ResponseDraft extends BaseEntity {

    public enum Source { MANUAL, AI_GENERATED, TEMPLATE }

    public enum Status { DRAFT, PENDING_APPROVAL, APPROVED, REJECTED, PUBLISHED, SUPERSEDED, DISCARDED }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Source source = Source.MANUAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private AppUser createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private AppUser approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    protected ResponseDraft() {}

    public ResponseDraft(Review review, String body, Source source, AppUser createdBy) {
        this.organization = review.getOrganization();
        this.review = review;
        this.body = body;
        this.source = source;
        this.createdBy = createdBy;
    }

    public void approve(AppUser approver, Instant at) {
        this.status = Status.APPROVED;
        this.approvedBy = approver;
        this.approvedAt = at;
    }

    public void reject(String reason) {
        this.status = Status.REJECTED;
        this.rejectionReason = reason;
    }

    public Organization getOrganization() { return organization; }
    public Review getReview() { return review; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public Source getSource() { return source; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public @Nullable AppUser getCreatedBy() { return createdBy; }
    public @Nullable AppUser getApprovedBy() { return approvedBy; }
    public @Nullable Instant getApprovedAt() { return approvedAt; }
    public @Nullable String getRejectionReason() { return rejectionReason; }
}
