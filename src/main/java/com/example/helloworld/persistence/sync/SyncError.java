package com.example.helloworld.persistence.sync;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.lang.Nullable;

/** An error raised while synchronizing. Immutable once written; removed by retention. */
@Entity
@Table(name = "sync_errors")
public class SyncError {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "sync_run_id", updatable = false)
    private UUID syncRunId;

    @Column(name = "connected_account_id", updatable = false)
    private UUID connectedAccountId;

    @Column(name = "location_id", updatable = false)
    private UUID locationId;

    @Column(name = "provider_code", length = 50, updatable = false)
    private String providerCode;

    @Column(name = "error_code", length = 100, updatable = false)
    private String errorCode;

    @Column(name = "http_status", updatable = false)
    private Integer httpStatus;

    @Column(nullable = false, columnDefinition = "text", updatable = false)
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", updatable = false)
    private String details;

    @Column(nullable = false, updatable = false)
    private boolean retryable;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();

    protected SyncError() {}

    public SyncError(SyncRun run, String providerCode, String errorCode, Integer httpStatus,
                     String message, String detailsJson, boolean retryable) {
        this.organizationId = run.getOrganizationId();
        this.syncRunId = run.getId();
        this.connectedAccountId = run.getConnectedAccountId();
        this.locationId = run.getLocationId();
        this.providerCode = providerCode;
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.message = message;
        this.details = detailsJson;
        this.retryable = retryable;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public @Nullable UUID getSyncRunId() { return syncRunId; }
    public @Nullable UUID getConnectedAccountId() { return connectedAccountId; }
    public @Nullable UUID getLocationId() { return locationId; }
    public @Nullable String getProviderCode() { return providerCode; }
    public @Nullable String getErrorCode() { return errorCode; }
    public @Nullable Integer getHttpStatus() { return httpStatus; }
    public String getMessage() { return message; }
    public @Nullable String getDetails() { return details; }
    public boolean isRetryable() { return retryable; }
    public Instant getOccurredAt() { return occurredAt; }
}
