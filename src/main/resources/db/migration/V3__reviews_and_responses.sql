-- Reviews, other interactions (replies, Q&A, comments), response drafts and
-- the publication attempts that push approved drafts back to the provider.

CREATE TABLE reviews (
    id                          uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id             uuid          NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    location_id                 uuid          NOT NULL REFERENCES business_locations (id),
    provider_code               varchar(50)   NOT NULL REFERENCES integration_providers (code),
    external_review_id          varchar(255)  NOT NULL,
    external_resource_name      varchar(512),
    reviewer_display_name       varchar(255),
    reviewer_profile_photo_url  varchar(2048),
    reviewer_is_anonymous       boolean       NOT NULL DEFAULT false,
    star_rating                 smallint      CHECK (star_rating BETWEEN 1 AND 5),
    comment                     text,
    language_code               varchar(20),
    review_created_at           timestamptz,
    review_updated_at           timestamptz,
    reply_status                varchar(20)   NOT NULL DEFAULT 'UNANSWERED'
                                CHECK (reply_status IN ('UNANSWERED', 'ANSWERED')),
    raw_payload                 jsonb,
    first_seen_at               timestamptz   NOT NULL DEFAULT now(),
    provider_data_fetched_at    timestamptz   NOT NULL DEFAULT now(),
    removed_at_provider_at      timestamptz,
    content_purged_at           timestamptz,
    created_at                  timestamptz   NOT NULL DEFAULT now(),
    updated_at                  timestamptz   NOT NULL DEFAULT now(),
    version                     bigint        NOT NULL DEFAULT 0,
    CONSTRAINT uq_reviews_external UNIQUE (location_id, external_review_id)
);

CREATE INDEX ix_reviews_location_created ON reviews (location_id, review_created_at DESC);
CREATE INDEX ix_reviews_org_reply_status ON reviews (organization_id, reply_status);
CREATE INDEX ix_reviews_retention ON reviews (provider_data_fetched_at) WHERE content_purged_at IS NULL;

CREATE TABLE interactions (
    id                        uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id           uuid          NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    location_id               uuid          NOT NULL REFERENCES business_locations (id),
    review_id                 uuid          REFERENCES reviews (id),
    provider_code             varchar(50)   NOT NULL REFERENCES integration_providers (code),
    -- Always set. For provider sub-resources without their own ID (e.g. a
    -- Google review reply) derive one from the parent: "<review name>/reply".
    external_interaction_id   varchar(512)  NOT NULL,
    interaction_type          varchar(30)   NOT NULL
                              CHECK (interaction_type IN ('OWNER_REPLY', 'CUSTOMER_COMMENT', 'QUESTION', 'ANSWER', 'REACTION')),
    author_type               varchar(20)   NOT NULL DEFAULT 'UNKNOWN'
                              CHECK (author_type IN ('OWNER', 'CUSTOMER', 'PROVIDER', 'UNKNOWN')),
    author_display_name       varchar(255),
    body                      text,
    occurred_at               timestamptz,
    raw_payload               jsonb,
    provider_data_fetched_at  timestamptz   NOT NULL DEFAULT now(),
    removed_at_provider_at    timestamptz,
    content_purged_at         timestamptz,
    created_at                timestamptz   NOT NULL DEFAULT now(),
    updated_at                timestamptz   NOT NULL DEFAULT now(),
    version                   bigint        NOT NULL DEFAULT 0,
    CONSTRAINT uq_interactions_external UNIQUE (location_id, interaction_type, external_interaction_id)
);

CREATE INDEX ix_interactions_review ON interactions (review_id);
CREATE INDEX ix_interactions_retention ON interactions (provider_data_fetched_at) WHERE content_purged_at IS NULL;

CREATE TABLE response_drafts (
    id                    uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id       uuid         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    review_id             uuid         NOT NULL REFERENCES reviews (id),
    body                  text         NOT NULL,
    source                varchar(20)  NOT NULL DEFAULT 'MANUAL'
                          CHECK (source IN ('MANUAL', 'AI_GENERATED', 'TEMPLATE')),
    status                varchar(20)  NOT NULL DEFAULT 'DRAFT'
                          CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED',
                                            'PUBLISHED', 'SUPERSEDED', 'DISCARDED')),
    created_by_user_id    uuid         REFERENCES app_users (id) ON DELETE SET NULL,
    approved_by_user_id   uuid         REFERENCES app_users (id) ON DELETE SET NULL,
    approved_at           timestamptz,
    rejection_reason      text,
    created_at            timestamptz  NOT NULL DEFAULT now(),
    updated_at            timestamptz  NOT NULL DEFAULT now(),
    version               bigint       NOT NULL DEFAULT 0
);

-- At most one open (editable, or approved but not yet published) draft per review.
CREATE UNIQUE INDEX uq_response_drafts_open_per_review ON response_drafts (review_id)
    WHERE status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED');

CREATE TABLE response_publications (
    id                    uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id       uuid          NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    draft_id              uuid          NOT NULL REFERENCES response_drafts (id),
    review_id             uuid          NOT NULL REFERENCES reviews (id),
    status                varchar(20)   NOT NULL DEFAULT 'QUEUED'
                          CHECK (status IN ('QUEUED', 'IN_PROGRESS', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    -- Guards against double-submits; also sent to the provider where supported.
    idempotency_key       varchar(100)  NOT NULL,
    attempt_count         integer       NOT NULL DEFAULT 0,
    next_attempt_at       timestamptz,
    published_body        text,
    external_reply_id     varchar(512),
    published_at          timestamptz,
    last_error_code       varchar(100),
    last_error_message    text,
    requested_by_user_id  uuid          REFERENCES app_users (id) ON DELETE SET NULL,
    created_at            timestamptz   NOT NULL DEFAULT now(),
    updated_at            timestamptz   NOT NULL DEFAULT now(),
    version               bigint        NOT NULL DEFAULT 0,
    CONSTRAINT uq_response_publications_idempotency UNIQUE (idempotency_key)
);

-- Only one publication may be in flight for a review at a time.
CREATE UNIQUE INDEX uq_response_publications_in_flight ON response_publications (review_id)
    WHERE status IN ('QUEUED', 'IN_PROGRESS');
CREATE INDEX ix_response_publications_due ON response_publications (next_attempt_at)
    WHERE status = 'QUEUED';
