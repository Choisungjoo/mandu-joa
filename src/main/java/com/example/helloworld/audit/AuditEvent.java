package com.example.helloworld.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Append-only record of a user action or system change. A database trigger
 * rejects UPDATEs; rows are only ever inserted, or deleted by retention.
 */
@Entity
@Immutable
@Table(name = "audit_events")
public class AuditEvent {

    public enum ActorType { USER, SYSTEM, PROVIDER }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 20)
    private ActorType actorType;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    /** Snapshot of who acted (e.g. e-mail), kept even if the user is later deleted. */
    @Column(name = "actor_label", length = 320)
    private String actorLabel;

    /** Dotted verb, e.g. {@code response_draft.approved}. */
    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", length = 100)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String changes;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    protected AuditEvent() {}

    private AuditEvent(UUID organizationId, ActorType actorType, UUID actorUserId, String actorLabel,
                       String action, String entityType, UUID entityId, String changesJson) {
        this.organizationId = organizationId;
        this.actorType = actorType;
        this.actorUserId = actorUserId;
        this.actorLabel = actorLabel;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.changes = changesJson;
    }

    public static AuditEvent byUser(UUID organizationId, UUID userId, String userLabel, String action,
                                    String entityType, UUID entityId, String changesJson) {
        return new AuditEvent(organizationId, ActorType.USER, userId, userLabel, action, entityType, entityId, changesJson);
    }

    public static AuditEvent bySystem(UUID organizationId, String action, String entityType, UUID entityId,
                                      String changesJson) {
        return new AuditEvent(organizationId, ActorType.SYSTEM, null, "system", action, entityType, entityId, changesJson);
    }

    /** Request metadata; call before the event is saved. */
    public AuditEvent withRequest(String ipAddress, String userAgent, String requestId) {
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.requestId = requestId;
        return this;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public ActorType getActorType() { return actorType; }
    public UUID getActorUserId() { return actorUserId; }
    public String getActorLabel() { return actorLabel; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public String getChanges() { return changes; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public String getRequestId() { return requestId; }
    public Instant getOccurredAt() { return occurredAt; }
}
