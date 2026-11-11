-- Operación del servicio: gestión de problemas y seguimiento de incidentes
-- (sílabo semanas 11 y 13; ISO/IEC 20000-1, 8.6.1 y 8.6.3).

-- Problema: la causa desconocida de uno o más incidentes. Con causa raíz y solución temporal
-- documentadas pasa a "error conocido"; con la solución definitiva aplicada, a "resuelto".
CREATE SEQUENCE problem_code_seq;
CREATE TABLE problems (
    id uuid PRIMARY KEY,
    code varchar(16) NOT NULL UNIQUE,
    title varchar(160) NOT NULL CHECK (length(trim(title)) > 0),
    description varchar(4000) NOT NULL CHECK (length(trim(description)) > 0),
    service_id uuid NOT NULL REFERENCES catalog_services(id),
    priority varchar(16) NOT NULL CHECK (priority IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    status varchar(16) NOT NULL DEFAULT 'INVESTIGATING' CHECK (status IN ('INVESTIGATING', 'KNOWN_ERROR', 'RESOLVED')),
    owner_id uuid REFERENCES identity_users(id) ON DELETE SET NULL,
    owner_name varchar(120) NOT NULL,
    root_cause varchar(4000),
    workaround varchar(4000),
    permanent_solution varchar(4000),
    created_at timestamptz NOT NULL,
    known_error_at timestamptz,
    resolved_at timestamptz,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_problem_known_error CHECK (status = 'INVESTIGATING' OR (root_cause IS NOT NULL AND workaround IS NOT NULL)),
    CONSTRAINT ck_problem_resolved CHECK (status <> 'RESOLVED' OR permanent_solution IS NOT NULL)
);
CREATE INDEX idx_problems_status ON problems(status, created_at DESC);
CREATE INDEX idx_problems_service ON problems(service_id);

-- Un incidente pertenece a lo sumo a un problema.
CREATE TABLE problem_incidents (
    problem_id uuid NOT NULL REFERENCES problems(id),
    ticket_id uuid NOT NULL UNIQUE REFERENCES tickets(id),
    linked_at timestamptz NOT NULL,
    linked_by_name varchar(120) NOT NULL,
    PRIMARY KEY (problem_id, ticket_id)
);

-- Seguimiento del ticket. Las notas internas solo las ve el equipo de TI.
CREATE TABLE ticket_comments (
    id uuid PRIMARY KEY,
    ticket_id uuid NOT NULL REFERENCES tickets(id),
    author_id uuid REFERENCES identity_users(id) ON DELETE SET NULL,
    author_name varchar(120) NOT NULL,
    staff boolean NOT NULL,
    internal boolean NOT NULL DEFAULT false,
    body varchar(2000) NOT NULL CHECK (length(trim(body)) > 0),
    created_at timestamptz NOT NULL,
    CONSTRAINT ck_comment_internal_staff CHECK (NOT internal OR staff)
);
CREATE INDEX idx_ticket_comments_ticket ON ticket_comments(ticket_id, created_at);

INSERT INTO identity_permissions (name, description) VALUES
    ('problems:manage', 'Registrar problemas, causas raíz y errores conocidos'),
    ('tickets:comment', 'Comentar en los tickets propios o atendidos');

INSERT INTO identity_role_permissions (role_name, permission_name) VALUES
    ('ROLE_TECHNICIAN', 'problems:manage'), ('ROLE_ADMIN', 'problems:manage'),
    ('ROLE_USER', 'tickets:comment'), ('ROLE_STUDENT', 'tickets:comment'),
    ('ROLE_TECHNICIAN', 'tickets:comment'), ('ROLE_ADMIN', 'tickets:comment');
