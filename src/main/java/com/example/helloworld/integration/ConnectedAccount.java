package com.example.helloworld.integration;

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
import org.springframework.lang.Nullable;

/** A provider account (e.g. a Google Business Profile account) linked to an organization. */
@Entity
@Table(name = "connected_accounts")
public class ConnectedAccount extends BaseEntity {

    public enum Status { ACTIVE, REAUTH_REQUIRED, DISCONNECTED, ERROR }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "provider_code", nullable = false, length = 50)
    private String providerCode;

    @Column(name = "external_account_id", nullable = false)
    private String externalAccountId;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "account_type", length = 50)
    private String accountType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "granted_scopes", columnDefinition = "text")
    private String grantedScopes;

    /** Pointer into the secret store holding the OAuth tokens. */
    @Column(name = "credential_ref")
    private String credentialRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connected_by_user_id")
    private AppUser connectedBy;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt = Instant.now();

    @Column(name = "disconnected_at")
    private Instant disconnectedAt;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    protected ConnectedAccount() {}

    public ConnectedAccount(Organization organization, String providerCode, String externalAccountId) {
        this.organization = organization;
        this.providerCode = providerCode;
        this.externalAccountId = externalAccountId;
    }

    /** Marks the account disconnected; the retention job then purges its provider content. */
    public void disconnect(Instant at) {
        this.status = Status.DISCONNECTED;
        this.disconnectedAt = at;
    }

    public Organization getOrganization() { return organization; }
    public String getProviderCode() { return providerCode; }
    public String getExternalAccountId() { return externalAccountId; }
    public @Nullable String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public @Nullable String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public @Nullable String getGrantedScopes() { return grantedScopes; }
    public void setGrantedScopes(String grantedScopes) { this.grantedScopes = grantedScopes; }
    public @Nullable String getCredentialRef() { return credentialRef; }
    public void setCredentialRef(String credentialRef) { this.credentialRef = credentialRef; }
    public @Nullable AppUser getConnectedBy() { return connectedBy; }
    public void setConnectedBy(AppUser connectedBy) { this.connectedBy = connectedBy; }
    public Instant getConnectedAt() { return connectedAt; }
    public void setConnectedAt(Instant connectedAt) { this.connectedAt = connectedAt; }
    public @Nullable Instant getDisconnectedAt() { return disconnectedAt; }
    public @Nullable Instant getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(Instant lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }
}
