package com.example.helloworld.integration;

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
import org.springframework.lang.Nullable;

/** An external business location (Google) or page (Facebook) reachable through a connected account. */
@Entity
@Table(name = "business_locations")
public class BusinessLocation extends BaseEntity {

    public enum Type { LOCATION, PAGE }

    public enum Status { ACTIVE, ARCHIVED, REMOVED_AT_PROVIDER }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "connected_account_id", nullable = false)
    private ConnectedAccount connectedAccount;

    @Column(name = "provider_code", nullable = false, length = 50)
    private String providerCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_type", nullable = false, length = 20)
    private Type locationType = Type.LOCATION;

    @Column(name = "external_location_id", nullable = false)
    private String externalLocationId;

    @Column(name = "external_resource_name", length = 512)
    private String externalResourceName;

    @Column(name = "external_place_id")
    private String externalPlaceId;

    private String name;

    @Column(columnDefinition = "text")
    private String address;

    @Column(length = 50)
    private String phone;

    @Column(name = "website_url", length = 2048)
    private String websiteUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.ACTIVE;

    @Column(name = "provider_data_fetched_at")
    private Instant providerDataFetchedAt;

    @Column(name = "content_purged_at")
    private Instant contentPurgedAt;

    protected BusinessLocation() {}

    public BusinessLocation(Organization organization, ConnectedAccount connectedAccount, String externalLocationId) {
        this.organization = organization;
        this.connectedAccount = connectedAccount;
        this.providerCode = connectedAccount.getProviderCode();
        this.externalLocationId = externalLocationId;
    }

    public Organization getOrganization() { return organization; }
    public ConnectedAccount getConnectedAccount() { return connectedAccount; }
    public void setConnectedAccount(ConnectedAccount connectedAccount) { this.connectedAccount = connectedAccount; }
    public String getProviderCode() { return providerCode; }
    public Type getLocationType() { return locationType; }
    public void setLocationType(Type locationType) { this.locationType = locationType; }
    public String getExternalLocationId() { return externalLocationId; }
    public @Nullable String getExternalResourceName() { return externalResourceName; }
    public void setExternalResourceName(String name) { this.externalResourceName = name; }
    public @Nullable String getExternalPlaceId() { return externalPlaceId; }
    public void setExternalPlaceId(String externalPlaceId) { this.externalPlaceId = externalPlaceId; }
    public @Nullable String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public @Nullable String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public @Nullable String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public @Nullable String getWebsiteUrl() { return websiteUrl; }
    public void setWebsiteUrl(String websiteUrl) { this.websiteUrl = websiteUrl; }
    public @Nullable String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public @Nullable Instant getProviderDataFetchedAt() { return providerDataFetchedAt; }
    public void setProviderDataFetchedAt(Instant at) { this.providerDataFetchedAt = at; }
    public @Nullable Instant getContentPurgedAt() { return contentPurgedAt; }
}
