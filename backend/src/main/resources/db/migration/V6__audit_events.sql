-- Registro de auditoría: evidencia de quién hizo qué y cuándo (ISO/IEC 20000-1, 7.5 y 8.7.3).
-- Sin clave foránea a propósito: el registro debe sobrevivir a la cuenta que lo generó.
CREATE TABLE audit_events (
    id uuid PRIMARY KEY,
    occurred_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    action varchar(64) NOT NULL,
    actor_user_id uuid,
    actor_email varchar(254),
    target_type varchar(64),
    target_id varchar(64),
    remote_address varchar(64),
    detail varchar(500)
);
CREATE INDEX idx_audit_events_occurred ON audit_events(occurred_at DESC, id);
CREATE INDEX idx_audit_events_target ON audit_events(target_type, target_id);

CREATE FUNCTION audit_events_reject_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_events solo admite inserciones';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_events_no_update_delete BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION audit_events_reject_change();
CREATE TRIGGER audit_events_no_truncate BEFORE TRUNCATE ON audit_events
    FOR EACH STATEMENT EXECUTE FUNCTION audit_events_reject_change();

INSERT INTO identity_permissions (name, description) VALUES
    ('audit:read', 'Consultar el registro de auditoría');
INSERT INTO identity_role_permissions (role_name, permission_name) VALUES
    ('ROLE_ADMIN', 'audit:read');
