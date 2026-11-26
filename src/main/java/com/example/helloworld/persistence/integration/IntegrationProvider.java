package com.example.helloworld.persistence.integration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.lang.Nullable;

/** A review platform (Google Business Profile, Facebook Pages, ...) and its retention rules. */
@Entity
@Table(name = "integration_providers")
public class IntegrationProvider {

    public static final String GOOGLE_BUSINESS_PROFILE = "GOOGLE_BUSINESS_PROFILE";
    public static final String FACEBOOK_PAGES = "FACEBOOK_PAGES";

    @Id
    @Column(length = 50)
    private String code;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false)
    private boolean enabled = true;

    /** Days provider content may be kept without a refresh; {@code null} = no time limit. */
    @Column(name = "content_retention_days")
    private Integer contentRetentionDays;

    @Column(name = "disconnected_purge_days", nullable = false)
    private int disconnectedPurgeDays;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IntegrationProvider() {}

    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public @Nullable Integer getContentRetentionDays() { return contentRetentionDays; }
    public void setContentRetentionDays(Integer days) { this.contentRetentionDays = days; }
    public int getDisconnectedPurgeDays() { return disconnectedPurgeDays; }
    public void setDisconnectedPurgeDays(int days) { this.disconnectedPurgeDays = days; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
