-- Tenancy: organizations, application users and their memberships.

CREATE TABLE organizations (
    id          uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        varchar(200) NOT NULL,
    slug        varchar(100) NOT NULL,
    status      varchar(20)  NOT NULL DEFAULT 'ACTIVE'
                CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    version     bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_organizations_slug UNIQUE (slug)
);

CREATE TABLE app_users (
    id             uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    email          varchar(320) NOT NULL,
    display_name   varchar(200),
    status         varchar(20)  NOT NULL DEFAULT 'ACTIVE'
                   CHECK (status IN ('ACTIVE', 'DISABLED')),
    last_login_at  timestamptz,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    version        bigint       NOT NULL DEFAULT 0
);

-- E-mail addresses are unique regardless of case.
CREATE UNIQUE INDEX uq_app_users_email ON app_users (lower(email));

CREATE TABLE organization_memberships (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id  uuid        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    user_id          uuid        NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    role             varchar(20) NOT NULL
                     CHECK (role IN ('OWNER', 'ADMIN', 'MANAGER', 'VIEWER')),
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    version          bigint      NOT NULL DEFAULT 0,
    CONSTRAINT uq_organization_memberships UNIQUE (organization_id, user_id)
);

CREATE INDEX ix_organization_memberships_user ON organization_memberships (user_id);
