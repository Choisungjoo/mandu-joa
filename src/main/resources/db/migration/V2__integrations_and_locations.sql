-- Integration providers, connected provider accounts and the external
-- business locations / pages reachable through them.

CREATE TABLE integration_providers (
    code                     varchar(50)  PRIMARY KEY,
    display_name             varchar(100) NOT NULL,
    enabled                  boolean      NOT NULL DEFAULT true,
    -- Provider content (review text, reviewer names, raw payloads...) that has
    -- not been refreshed from the provider within this many days is purged.
    -- NULL disables time-based purging for the provider.
    content_retention_days   integer      CHECK (content_retention_days > 0),
    -- Days after an account is disconnected before its provider content is purged.
    disconnected_purge_days  integer      NOT NULL DEFAULT 0 CHECK (disconnected_purge_days >= 0),
    created_at               timestamptz  NOT NULL DEFAULT now(),
    updated_at               timestamptz  NOT NULL DEFAULT now()
);

-- Retention values are conservative defaults. Confirm them against each
-- provider's current API terms before going live; change them in a new migration.
INSERT INTO integration_providers (code, display_name, content_retention_days, disconnected_purge_days)
VALUES ('GOOGLE_BUSINESS_PROFILE', 'Google Business Profile', 30, 0),
       ('FACEBOOK_PAGES',          'Facebook Pages',          30, 0);

CREATE TABLE connected_accounts (
    id                    uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id       uuid         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    provider_code         varchar(50)  NOT NULL REFERENCES integration_providers (code),
    -- Provider identifier, e.g. "accounts/1234567890" for Google.
    external_account_id   varchar(255) NOT NULL,
    display_name          varchar(255),
    account_type          varchar(50),
    status                varchar(20)  NOT NULL DEFAULT 'ACTIVE'
                          CHECK (status IN ('ACTIVE', 'REAUTH_REQUIRED', 'DISCONNECTED', 'ERROR')),
    granted_scopes        text,
    -- Reference into a secret store. OAuth tokens are never stored in this table.
    credential_ref        varchar(255),
    connected_by_user_id  uuid         REFERENCES app_users (id) ON DELETE SET NULL,
    connected_at          timestamptz  NOT NULL DEFAULT now(),
    disconnected_at       timestamptz,
    last_synced_at        timestamptz,
    created_at            timestamptz  NOT NULL DEFAULT now(),
    updated_at            timestamptz  NOT NULL DEFAULT now(),
    version               bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_connected_accounts_external UNIQUE (organization_id, provider_code, external_account_id),
    CONSTRAINT ck_connected_accounts_disconnected
        CHECK (status <> 'DISCONNECTED' OR disconnected_at IS NOT NULL)
);

CREATE INDEX ix_connected_accounts_provider_external ON connected_accounts (provider_code, external_account_id);

CREATE TABLE business_locations (
    id                        uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id           uuid          NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    connected_account_id      uuid          NOT NULL REFERENCES connected_accounts (id),
    provider_code             varchar(50)   NOT NULL REFERENCES integration_providers (code),
    location_type             varchar(20)   NOT NULL DEFAULT 'LOCATION'
                              CHECK (location_type IN ('LOCATION', 'PAGE')),
    -- Provider identifiers, e.g. "locations/987" and a Google Maps place ID.
    external_location_id      varchar(255)  NOT NULL,
    external_resource_name    varchar(512),
    external_place_id         varchar(255),
    name                      varchar(255),
    address                   text,
    phone                     varchar(50),
    website_url               varchar(2048),
    raw_payload               jsonb,
    status                    varchar(30)   NOT NULL DEFAULT 'ACTIVE'
                              CHECK (status IN ('ACTIVE', 'ARCHIVED', 'REMOVED_AT_PROVIDER')),
    provider_data_fetched_at  timestamptz,
    content_purged_at         timestamptz,
    created_at                timestamptz   NOT NULL DEFAULT now(),
    updated_at                timestamptz   NOT NULL DEFAULT now(),
    version                   bigint        NOT NULL DEFAULT 0,
    CONSTRAINT uq_business_locations_external UNIQUE (organization_id, provider_code, external_location_id)
);

CREATE INDEX ix_business_locations_account ON business_locations (connected_account_id);
CREATE INDEX ix_business_locations_retention ON business_locations (provider_data_fetched_at)
    WHERE content_purged_at IS NULL;
