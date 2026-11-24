-- Polling configuration, sync cursors, sync run history and sync error log.

CREATE TABLE polling_configurations (
    id                    uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id       uuid          NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    connected_account_id  uuid          NOT NULL REFERENCES connected_accounts (id),
    -- NULL for account-level resources (the list of locations).
    location_id           uuid          REFERENCES business_locations (id),
    resource_type         varchar(30)   NOT NULL
                          CHECK (resource_type IN ('LOCATIONS', 'REVIEWS', 'INTERACTIONS')),
    enabled               boolean       NOT NULL DEFAULT true,
    interval_seconds      integer       NOT NULL DEFAULT 900 CHECK (interval_seconds >= 60),
    next_run_at           timestamptz   NOT NULL DEFAULT now(),
    last_run_at           timestamptz,
    last_success_at       timestamptz,
    consecutive_failures  integer       NOT NULL DEFAULT 0,
    -- Lease so only one worker polls a given configuration at a time.
    lease_owner           varchar(100),
    lease_expires_at      timestamptz,
    created_at            timestamptz   NOT NULL DEFAULT now(),
    updated_at            timestamptz   NOT NULL DEFAULT now(),
    version               bigint        NOT NULL DEFAULT 0,
    CONSTRAINT ck_polling_configurations_scope
        CHECK ((resource_type = 'LOCATIONS') = (location_id IS NULL))
);

CREATE UNIQUE INDEX uq_polling_configurations_scope
    ON polling_configurations (connected_account_id, resource_type, location_id) NULLS NOT DISTINCT;
CREATE INDEX ix_polling_configurations_due ON polling_configurations (next_run_at) WHERE enabled;

CREATE TABLE sync_cursors (
    id                        uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    polling_configuration_id  uuid          NOT NULL REFERENCES polling_configurations (id) ON DELETE CASCADE,
    cursor_key                varchar(100)  NOT NULL DEFAULT 'default',
    page_token                text,
    high_watermark            timestamptz,
    etag                      varchar(255),
    created_at                timestamptz   NOT NULL DEFAULT now(),
    updated_at                timestamptz   NOT NULL DEFAULT now(),
    version                   bigint        NOT NULL DEFAULT 0,
    CONSTRAINT uq_sync_cursors UNIQUE (polling_configuration_id, cursor_key)
);

CREATE TABLE sync_runs (
    id                        uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id           uuid         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    polling_configuration_id  uuid         REFERENCES polling_configurations (id) ON DELETE SET NULL,
    connected_account_id      uuid         REFERENCES connected_accounts (id) ON DELETE SET NULL,
    location_id               uuid         REFERENCES business_locations (id) ON DELETE SET NULL,
    resource_type             varchar(30)  NOT NULL
                              CHECK (resource_type IN ('LOCATIONS', 'REVIEWS', 'INTERACTIONS')),
    trigger_type              varchar(20)  NOT NULL DEFAULT 'SCHEDULED'
                              CHECK (trigger_type IN ('SCHEDULED', 'MANUAL', 'WEBHOOK')),
    status                    varchar(20)  NOT NULL DEFAULT 'RUNNING'
                              CHECK (status IN ('RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED')),
    started_at                timestamptz  NOT NULL DEFAULT now(),
    finished_at               timestamptz,
    items_fetched             integer      NOT NULL DEFAULT 0,
    items_created             integer      NOT NULL DEFAULT 0,
    items_updated             integer      NOT NULL DEFAULT 0,
    items_removed             integer      NOT NULL DEFAULT 0,
    cursor_before             text,
    cursor_after              text,
    created_at                timestamptz  NOT NULL DEFAULT now(),
    updated_at                timestamptz  NOT NULL DEFAULT now(),
    version                   bigint       NOT NULL DEFAULT 0
);

CREATE INDEX ix_sync_runs_config_started ON sync_runs (polling_configuration_id, started_at DESC);
CREATE INDEX ix_sync_runs_started ON sync_runs (started_at);

CREATE TABLE sync_errors (
    id                    uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id       uuid          NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    sync_run_id           uuid          REFERENCES sync_runs (id) ON DELETE CASCADE,
    connected_account_id  uuid          REFERENCES connected_accounts (id) ON DELETE SET NULL,
    location_id           uuid          REFERENCES business_locations (id) ON DELETE SET NULL,
    provider_code         varchar(50)   REFERENCES integration_providers (code),
    error_code            varchar(100),
    http_status           integer,
    message               text          NOT NULL,
    details               jsonb,
    retryable             boolean       NOT NULL DEFAULT false,
    occurred_at           timestamptz   NOT NULL DEFAULT now()
);

CREATE INDEX ix_sync_errors_run ON sync_errors (sync_run_id);
CREATE INDEX ix_sync_errors_occurred ON sync_errors (occurred_at);
