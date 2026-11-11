-- Gestión de incidentes con niveles de servicio (ISO/IEC 20000-1, 8.3.3 y 8.6.1).
-- Valores decididos por el Instituto el 2026-11-11: prioridad por servicio y tiempos del informe del proyecto.

-- Prioridad base del servicio: el solicitante no la elige. Crítica solo la asigna un técnico.
ALTER TABLE catalog_services ADD COLUMN default_priority varchar(16) NOT NULL DEFAULT 'MEDIUM'
    CHECK (default_priority IN ('HIGH', 'MEDIUM', 'LOW'));
UPDATE catalog_services SET default_priority = 'HIGH' WHERE name IN ('Plataforma virtual', 'Internet y Wi-Fi');
UPDATE catalog_services SET default_priority = 'LOW' WHERE name = 'Soporte técnico';

-- Tiempos en minutos de horario de atención (lunes a sábado, 7:00 a 21:00, hora de Lima).
CREATE TABLE sla_policies (
    priority varchar(16) PRIMARY KEY CHECK (priority IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    first_response_minutes integer NOT NULL CHECK (first_response_minutes > 0),
    resolution_minutes integer NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_sla_resolution_after_response CHECK (resolution_minutes >= first_response_minutes)
);
INSERT INTO sla_policies (priority, first_response_minutes, resolution_minutes) VALUES
    ('CRITICAL', 10, 30),
    ('HIGH', 15, 60),
    ('MEDIUM', 30, 240),
    ('LOW', 60, 480);

-- El catálogo muestra la misma respuesta objetivo que se aplica a los tickets.
UPDATE catalog_services s SET response_target_minutes = p.first_response_minutes
FROM sla_policies p WHERE p.priority = s.default_priority;

CREATE SEQUENCE ticket_code_seq;

-- Nombre y correo se copian: el ticket conserva quién lo pidió aunque la cuenta se elimine.
CREATE TABLE tickets (
    id uuid PRIMARY KEY,
    code varchar(16) NOT NULL UNIQUE,
    title varchar(160) NOT NULL CHECK (length(trim(title)) > 0),
    description varchar(4000),
    service_id uuid NOT NULL REFERENCES catalog_services(id),
    requester_id uuid REFERENCES identity_users(id) ON DELETE SET NULL,
    requester_name varchar(120) NOT NULL,
    requester_email varchar(254) NOT NULL,
    assignee_id uuid REFERENCES identity_users(id) ON DELETE SET NULL,
    assignee_name varchar(120),
    priority varchar(16) NOT NULL CHECK (priority IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    status varchar(16) NOT NULL DEFAULT 'NEW' CHECK (status IN ('NEW', 'IN_PROGRESS', 'ON_HOLD', 'RESOLVED')),
    created_at timestamptz NOT NULL,
    first_response_due timestamptz NOT NULL,
    resolution_due timestamptz NOT NULL,
    first_responded_at timestamptz,
    resolved_at timestamptz,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_ticket_due_order CHECK (resolution_due >= first_response_due AND first_response_due > created_at)
);
CREATE INDEX idx_tickets_requester ON tickets(requester_id, created_at DESC);
CREATE INDEX idx_tickets_status ON tickets(status, created_at DESC);

INSERT INTO identity_permissions (name, description) VALUES
    ('tickets:create', 'Registrar tickets de soporte'),
    ('tickets:read:self', 'Consultar los tickets propios'),
    ('tickets:manage', 'Atender tickets: tomar, cambiar estado y prioridad'),
    ('sla:manage', 'Configurar los tiempos de atención por prioridad');

INSERT INTO identity_role_permissions (role_name, permission_name) VALUES
    ('ROLE_USER', 'tickets:create'), ('ROLE_USER', 'tickets:read:self'),
    ('ROLE_STUDENT', 'tickets:create'), ('ROLE_STUDENT', 'tickets:read:self'),
    ('ROLE_TECHNICIAN', 'tickets:create'), ('ROLE_TECHNICIAN', 'tickets:read:self'), ('ROLE_TECHNICIAN', 'tickets:manage'),
    ('ROLE_ADMIN', 'tickets:create'), ('ROLE_ADMIN', 'tickets:read:self'), ('ROLE_ADMIN', 'tickets:manage'), ('ROLE_ADMIN', 'sla:manage');
