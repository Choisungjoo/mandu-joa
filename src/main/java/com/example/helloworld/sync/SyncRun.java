package com.example.helloworld.sync;

import com.example.helloworld.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.springframework.lang.Nullable;

/** History of one synchronization pass. */
@Entity
@Table(name = "sync_runs")
public class SyncRun extends BaseEntity {

    public enum Trigger { SCHEDULED, MANUAL, WEBHOOK }

    public enum Status { RUNNING, SUCCEEDED, PARTIAL, FAILED }

    // Plain IDs: history rows outlive the configuration, account or location (ON DELETE SET NULL).
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "polling_configuration_id")
    private UUID pollingConfigurationId;

    @Column(name = "connected_account_id")
    private UUID connectedAccountId;

    @Column(name = "location_id")
    private UUID locationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 30)
    private PollingConfiguration.ResourceType resourceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private Trigger triggerType = Trigger.SCHEDULED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.RUNNING;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "items_fetched", nullable = false)
    private int itemsFetched;

    @Column(name = "items_created", nullable = false)
    private int itemsCreated;

    @Column(name = "items_updated", nullable = false)
    private int itemsUpdated;

    @Column(name = "items_removed", nullable = false)
    private int itemsRemoved;

    @Column(name = "cursor_before", columnDefinition = "text")
    private String cursorBefore;

    @Column(name = "cursor_after", columnDefinition = "text")
    private String cursorAfter;

    protected SyncRun() {}

    public static SyncRun start(PollingConfiguration config, Trigger trigger, String cursorBefore) {
        var run = new SyncRun();
        run.organizationId = config.getOrganization().getId();
        run.pollingConfigurationId = config.getId();
        run.connectedAccountId = config.getConnectedAccount().getId();
        run.locationId = config.getLocation() == null ? null : config.getLocation().getId();
        run.resourceType = config.getResourceType();
        run.triggerType = trigger;
        run.cursorBefore = cursorBefore;
        return run;
    }

    public void recordItem(boolean created) {
        itemsFetched++;
        if (created) itemsCreated++; else itemsUpdated++;
    }

    public void recordRemoved() { itemsRemoved++; }

    public void finish(Status status, String cursorAfter, Instant at) {
        this.status = status;
        this.cursorAfter = cursorAfter;
        this.finishedAt = at;
    }

    public UUID getOrganizationId() { return organizationId; }
    public @Nullable UUID getPollingConfigurationId() { return pollingConfigurationId; }
    public @Nullable UUID getConnectedAccountId() { return connectedAccountId; }
    public @Nullable UUID getLocationId() { return locationId; }
    public PollingConfiguration.ResourceType getResourceType() { return resourceType; }
    public Trigger getTriggerType() { return triggerType; }
    public Status getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public @Nullable Instant getFinishedAt() { return finishedAt; }
    public int getItemsFetched() { return itemsFetched; }
    public int getItemsCreated() { return itemsCreated; }
    public int getItemsUpdated() { return itemsUpdated; }
    public int getItemsRemoved() { return itemsRemoved; }
    public @Nullable String getCursorBefore() { return cursorBefore; }
    public @Nullable String getCursorAfter() { return cursorAfter; }
}
