-- Append-only history of user actions and system changes.

CREATE TABLE audit_events (
    id               uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id  uuid          REFERENCES organizations (id) ON DELETE CASCADE,
    actor_type       varchar(20)   NOT NULL CHECK (actor_type IN ('USER', 'SYSTEM', 'PROVIDER')),
    -- Deliberately no foreign key: the trail must outlive the user record.
    actor_user_id    uuid,
    actor_label      varchar(320),
    action           varchar(100)  NOT NULL,
    entity_type      varchar(100),
    entity_id        uuid,
    changes          jsonb,
    ip_address       varchar(45),
    user_agent       varchar(512),
    request_id       varchar(100),
    occurred_at      timestamptz   NOT NULL DEFAULT now()
);

CREATE INDEX ix_audit_events_org_occurred ON audit_events (organization_id, occurred_at DESC);
CREATE INDEX ix_audit_events_entity ON audit_events (entity_type, entity_id);
CREATE INDEX ix_audit_events_actor ON audit_events (actor_user_id, occurred_at DESC);
CREATE INDEX ix_audit_events_occurred ON audit_events (occurred_at);

-- Rows may be deleted by retention, but never rewritten.
CREATE FUNCTION audit_events_block_update() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only';
END;
$$;

CREATE TRIGGER trg_audit_events_block_update
    BEFORE UPDATE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION audit_events_block_update();
