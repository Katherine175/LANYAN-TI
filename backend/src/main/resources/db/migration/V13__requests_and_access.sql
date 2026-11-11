-- Operación del servicio: cumplimiento de solicitudes y gestión de accesos
-- (sílabo semanas 13 y 14; ISO/IEC 20000-1, 8.6.2 y 8.7.3).
-- Aprueba la Jefatura de TI (rol Administrador); atiende el equipo técnico.

-- Pedido estándar de un servicio del catálogo: algo nuevo, no una falla.
CREATE SEQUENCE service_request_code_seq;
CREATE TABLE service_requests (
    id uuid PRIMARY KEY,
    code varchar(16) NOT NULL UNIQUE,
    service_id uuid NOT NULL REFERENCES catalog_services(id),
    title varchar(160) NOT NULL CHECK (length(trim(title)) > 0),
    justification varchar(2000) NOT NULL CHECK (length(trim(justification)) > 0),
    needed_by date,
    requester_id uuid REFERENCES identity_users(id) ON DELETE SET NULL,
    requester_name varchar(120) NOT NULL,
    requester_email varchar(254) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'IN_PROGRESS', 'FULFILLED', 'CANCELLED')),
    decided_by_name varchar(120),
    decided_at timestamptz,
    decision_note varchar(500),
    assignee_id uuid REFERENCES identity_users(id) ON DELETE SET NULL,
    assignee_name varchar(120),
    fulfilled_at timestamptz,
    fulfillment_note varchar(1000),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0
);
CREATE INDEX idx_service_requests_requester ON service_requests(requester_id, created_at DESC);
CREATE INDEX idx_service_requests_status ON service_requests(status, created_at DESC);

-- Recursos sobre los que se conceden permisos.
CREATE TABLE access_resources (
    id uuid PRIMARY KEY,
    name varchar(120) NOT NULL CHECK (length(trim(name)) > 0),
    description varchar(500) NOT NULL,
    owner varchar(120) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_access_resource_name ON access_resources(lower(name));

INSERT INTO access_resources (id, name, description, owner) VALUES
    (gen_random_uuid(), 'Aula virtual', 'Cursos, evaluaciones y recursos de cada sección.', 'Coordinación académica'),
    (gen_random_uuid(), 'Sistema de matrícula', 'Inscripciones, cambios de sección y constancias.', 'Secretaría académica'),
    (gen_random_uuid(), 'Reportes académicos', 'Notas, asistencia y estadísticas por periodo.', 'Dirección del Instituto'),
    (gen_random_uuid(), 'Sistema de pagos', 'Registro y validación de pagos y recibos.', 'Administración'),
    (gen_random_uuid(), 'Carpeta compartida de docentes', 'Materiales y formatos del equipo docente.', 'Coordinación académica');

-- Cada solicitud de acceso es también la concesión: aprobada = acceso vigente hasta revocarlo o vencer.
CREATE SEQUENCE access_request_code_seq;
CREATE TABLE access_requests (
    id uuid PRIMARY KEY,
    code varchar(16) NOT NULL UNIQUE,
    resource_id uuid NOT NULL REFERENCES access_resources(id),
    level varchar(16) NOT NULL CHECK (level IN ('READ', 'EDIT', 'ADMIN')),
    justification varchar(2000) NOT NULL CHECK (length(trim(justification)) > 0),
    expires_on date,
    requester_id uuid REFERENCES identity_users(id) ON DELETE SET NULL,
    requester_name varchar(120) NOT NULL,
    requester_email varchar(254) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'REVOKED', 'CANCELLED')),
    decided_by_name varchar(120),
    decided_at timestamptz,
    decision_note varchar(500),
    revoked_by_name varchar(120),
    revoked_at timestamptz,
    revoke_reason varchar(500),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0
);
CREATE INDEX idx_access_requests_requester ON access_requests(requester_id, created_at DESC);
CREATE INDEX idx_access_requests_status ON access_requests(status, created_at DESC);

INSERT INTO identity_permissions (name, description) VALUES
    ('requests:create', 'Solicitar servicios del catálogo'),
    ('requests:approve', 'Aprobar o rechazar solicitudes de servicio'),
    ('requests:fulfill', 'Atender y cerrar solicitudes aprobadas'),
    ('access:request', 'Solicitar acceso a un recurso'),
    ('access:approve', 'Conceder, rechazar y revocar accesos');

INSERT INTO identity_role_permissions (role_name, permission_name) VALUES
    ('ROLE_USER', 'requests:create'), ('ROLE_USER', 'access:request'),
    ('ROLE_STUDENT', 'requests:create'), ('ROLE_STUDENT', 'access:request'),
    ('ROLE_TECHNICIAN', 'requests:create'), ('ROLE_TECHNICIAN', 'access:request'), ('ROLE_TECHNICIAN', 'requests:fulfill'),
    ('ROLE_ADMIN', 'requests:create'), ('ROLE_ADMIN', 'access:request'), ('ROLE_ADMIN', 'requests:fulfill'),
    ('ROLE_ADMIN', 'requests:approve'), ('ROLE_ADMIN', 'access:approve');
