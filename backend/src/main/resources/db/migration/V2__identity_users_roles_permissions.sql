CREATE TABLE identity_users (
    id uuid PRIMARY KEY,
    full_name varchar(120) NOT NULL CHECK (length(trim(full_name)) > 0),
    email varchar(254) NOT NULL UNIQUE CHECK (email = lower(trim(email))),
    password_hash varchar(100) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0
);

CREATE TABLE identity_roles (
    name varchar(32) PRIMARY KEY,
    description varchar(200) NOT NULL
);

CREATE TABLE identity_permissions (
    name varchar(80) PRIMARY KEY,
    description varchar(200) NOT NULL
);

CREATE TABLE identity_user_roles (
    user_id uuid NOT NULL REFERENCES identity_users(id),
    role_name varchar(32) NOT NULL REFERENCES identity_roles(name),
    PRIMARY KEY (user_id, role_name)
);
CREATE INDEX idx_identity_user_roles_role ON identity_user_roles(role_name);

CREATE TABLE identity_role_permissions (
    role_name varchar(32) NOT NULL REFERENCES identity_roles(name),
    permission_name varchar(80) NOT NULL REFERENCES identity_permissions(name),
    PRIMARY KEY (role_name, permission_name)
);
CREATE INDEX idx_identity_role_permissions_permission ON identity_role_permissions(permission_name);

INSERT INTO identity_roles (name, description) VALUES
    ('ROLE_ADMIN', 'Administración del sistema'),
    ('ROLE_TECHNICIAN', 'Atención técnica de servicios TI'),
    ('ROLE_USER', 'Usuario del portal de servicios TI'),
    ('ROLE_STUDENT', 'Estudiante del portal académico');

-- Capacidades de identidad y acceso a portales; no crean módulos académicos ni tickets.
INSERT INTO identity_permissions (name, description) VALUES
    ('identity:users:read', 'Consultar usuarios'),
    ('identity:users:create', 'Crear usuarios de forma controlada'),
    ('identity:roles:assign', 'Asignar roles a usuarios'),
    ('portal:it:access', 'Acceder al portal de servicios TI'),
    ('portal:academic:access', 'Acceder al portal académico propio');

INSERT INTO identity_role_permissions (role_name, permission_name) VALUES
    ('ROLE_ADMIN', 'identity:users:read'),
    ('ROLE_ADMIN', 'identity:users:create'),
    ('ROLE_ADMIN', 'identity:roles:assign'),
    ('ROLE_ADMIN', 'portal:it:access'),
    ('ROLE_TECHNICIAN', 'portal:it:access'),
    ('ROLE_USER', 'portal:it:access'),
    ('ROLE_STUDENT', 'portal:it:access'),
    ('ROLE_STUDENT', 'portal:academic:access');
