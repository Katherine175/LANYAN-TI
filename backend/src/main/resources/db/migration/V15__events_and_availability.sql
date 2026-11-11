-- Operación del servicio: gestión de eventos; Diseño: gestión de la disponibilidad
-- (sílabo semanas 11 y 7; ISO/IEC 20000-1, 8.7.1).

-- Historial de estados operativos: de aquí se calcula la disponibilidad real de cada servicio.
CREATE TABLE service_status_changes (
    id uuid PRIMARY KEY,
    service_id uuid NOT NULL REFERENCES catalog_services(id),
    from_status varchar(16) NOT NULL CHECK (from_status IN ('OPERATIONAL', 'DEGRADED', 'UNAVAILABLE')),
    to_status varchar(16) NOT NULL CHECK (to_status IN ('OPERATIONAL', 'DEGRADED', 'UNAVAILABLE')),
    changed_at timestamptz NOT NULL,
    changed_by varchar(254)
);
CREATE INDEX idx_status_changes_service ON service_status_changes(service_id, changed_at);

-- Evento: algo que ocurrió y que TI debe conocer. Informativo, advertencia o excepción.
CREATE SEQUENCE event_code_seq;
CREATE TABLE operational_events (
    id uuid PRIMARY KEY,
    code varchar(16) NOT NULL UNIQUE,
    service_id uuid REFERENCES catalog_services(id),
    severity varchar(16) NOT NULL CHECK (severity IN ('INFO', 'WARNING', 'EXCEPTION')),
    source varchar(16) NOT NULL CHECK (source IN ('AUTOMATIC', 'MANUAL')),
    title varchar(160) NOT NULL CHECK (length(trim(title)) > 0),
    detail varchar(2000),
    status varchar(16) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'CLOSED')),
    ticket_id uuid REFERENCES tickets(id),
    created_by varchar(254),
    created_at timestamptz NOT NULL,
    acknowledged_by varchar(254),
    acknowledged_at timestamptz,
    closed_at timestamptz,
    version bigint NOT NULL DEFAULT 0
);
CREATE INDEX idx_events_status ON operational_events(status, created_at DESC);

-- Mantenimiento programado: interrupción planificada y comunicada con anticipación.
CREATE TABLE maintenance_windows (
    id uuid PRIMARY KEY,
    service_id uuid NOT NULL REFERENCES catalog_services(id),
    title varchar(160) NOT NULL CHECK (length(trim(title)) > 0),
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    impact varchar(16) NOT NULL CHECK (impact IN ('LOW', 'MEDIUM', 'HIGH')),
    cancelled boolean NOT NULL DEFAULT false,
    created_by varchar(254),
    created_at timestamptz NOT NULL,
    CONSTRAINT ck_maintenance_order CHECK (ends_at > starts_at)
);
CREATE INDEX idx_maintenance_start ON maintenance_windows(starts_at);

INSERT INTO identity_permissions (name, description) VALUES
    ('operations:manage', 'Registrar y atender eventos, programar mantenimientos y consultar la disponibilidad');

INSERT INTO identity_role_permissions (role_name, permission_name) VALUES
    ('ROLE_TECHNICIAN', 'operations:manage'), ('ROLE_ADMIN', 'operations:manage');
