package com.example.helloworld.sync;

import com.example.helloworld.integration.BusinessLocation;
import com.example.helloworld.integration.ConnectedAccount;
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

/**
 * How often to poll one resource of a connected account. LOCATIONS is polled
 * per account (no location); REVIEWS and INTERACTIONS are polled per location.
 */
@Entity
@Table(name = "polling_configurations")
public class PollingConfiguration extends BaseEntity {

    public enum ResourceType { LOCATIONS, REVIEWS, INTERACTIONS }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "connected_account_id", nullable = false)
    private ConnectedAccount connectedAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private BusinessLocation location;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 30)
    private ResourceType resourceType;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "interval_seconds", nullable = false)
    private int intervalSeconds = 900;

    @Column(name = "next_run_at", nullable = false)
    private Instant nextRunAt = Instant.now();

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    @Column(name = "lease_owner", length = 100)
    private String leaseOwner;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    protected PollingConfiguration() {}

    public static PollingConfiguration forAccount(ConnectedAccount account) {
        var c = new PollingConfiguration();
        c.organization = account.getOrganization();
        c.connectedAccount = account;
        c.resourceType = ResourceType.LOCATIONS;
        return c;
    }

    public static PollingConfiguration forLocation(BusinessLocation location, ResourceType type) {
        if (type == ResourceType.LOCATIONS) {
            throw new IllegalArgumentException("LOCATIONS is polled per account");
        }
        var c = new PollingConfiguration();
        c.organization = location.getOrganization();
        c.connectedAccount = location.getConnectedAccount();
        c.location = location;
        c.resourceType = type;
        return c;
    }

    public void recordSuccess(Instant at) {
        lastRunAt = at;
        lastSuccessAt = at;
        consecutiveFailures = 0;
        nextRunAt = at.plusSeconds(intervalSeconds);
        releaseLease();
    }

    /** Backs off exponentially (capped at 64x the interval) after consecutive failures. */
    public void recordFailure(Instant at) {
        lastRunAt = at;
        consecutiveFailures++;
        long factor = 1L << Math.min(consecutiveFailures, 6);
        nextRunAt = at.plusSeconds(intervalSeconds * factor);
        releaseLease();
    }

    public void releaseLease() {
        leaseOwner = null;
        leaseExpiresAt = null;
    }

    public Organization getOrganization() { return organization; }
    public ConnectedAccount getConnectedAccount() { return connectedAccount; }
    public BusinessLocation getLocation() { return location; }
    public ResourceType getResourceType() { return resourceType; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getIntervalSeconds() { return intervalSeconds; }
    public void setIntervalSeconds(int intervalSeconds) { this.intervalSeconds = intervalSeconds; }
    public Instant getNextRunAt() { return nextRunAt; }
    public void setNextRunAt(Instant nextRunAt) { this.nextRunAt = nextRunAt; }
    public Instant getLastRunAt() { return lastRunAt; }
    public Instant getLastSuccessAt() { return lastSuccessAt; }
    public int getConsecutiveFailures() { return consecutiveFailures; }
    public String getLeaseOwner() { return leaseOwner; }
    public Instant getLeaseExpiresAt() { return leaseExpiresAt; }
}
