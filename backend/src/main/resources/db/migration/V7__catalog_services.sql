-- Catálogo de servicios TI (ISO/IEC 20000-1, 8.2.4).
CREATE TABLE catalog_categories (
    id uuid PRIMARY KEY,
    name varchar(80) NOT NULL CHECK (length(trim(name)) > 0),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_catalog_category_name ON catalog_categories(lower(name));

CREATE SEQUENCE catalog_service_code_seq;

CREATE TABLE catalog_services (
    id uuid PRIMARY KEY,
    code varchar(16) NOT NULL UNIQUE,
    name varchar(120) NOT NULL CHECK (length(trim(name)) > 0),
    description varchar(1000) NOT NULL CHECK (length(trim(description)) > 0),
    category_id uuid NOT NULL REFERENCES catalog_categories(id),
    owner varchar(120) NOT NULL CHECK (length(trim(owner)) > 0),
    icon varchar(32) NOT NULL CHECK (icon IN ('MONITOR','GRADUATION','CREDIT','MAIL','WIFI','PROJECTOR','HEADSET')),
    lifecycle_status varchar(16) NOT NULL DEFAULT 'DRAFT' CHECK (lifecycle_status IN ('DRAFT','PUBLISHED','RETIRED')),
    operational_status varchar(16) NOT NULL DEFAULT 'OPERATIONAL' CHECK (operational_status IN ('OPERATIONAL','DEGRADED','UNAVAILABLE')),
    availability_target numeric(5,2) NOT NULL CHECK (availability_target BETWEEN 0 AND 100),
    response_target_minutes integer NOT NULL CHECK (response_target_minutes > 0),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version bigint NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_catalog_service_name ON catalog_services(lower(name));
CREATE INDEX idx_catalog_service_category ON catalog_services(category_id);
CREATE INDEX idx_catalog_service_lifecycle ON catalog_services(lifecycle_status);

INSERT INTO identity_permissions (name, description) VALUES
    ('catalog:manage', 'Administrar servicios y categorías del catálogo'),
    ('catalog:availability:update', 'Actualizar el estado operativo de los servicios');
INSERT INTO identity_role_permissions (role_name, permission_name) VALUES
    ('ROLE_ADMIN', 'catalog:manage'),
    ('ROLE_ADMIN', 'catalog:availability:update'),
    ('ROLE_TECHNICIAN', 'catalog:availability:update');

-- Catálogo inicial tomado del diseño del portal; se ajusta desde Administración.
INSERT INTO catalog_categories (id, name)
SELECT gen_random_uuid(), name
FROM unnest(ARRAY['Académico','Administrativo','Comunicación','Infraestructura','Equipamiento','Soporte']) AS name;

INSERT INTO catalog_services (id, code, name, description, category_id, owner, icon, lifecycle_status, availability_target, response_target_minutes)
SELECT gen_random_uuid(), 'SRV-' || lpad(nextval('catalog_service_code_seq')::text, 3, '0'),
       s.name, s.description, c.id, 'Service Desk TI', s.icon, 'PUBLISHED', s.target, s.minutes
FROM (VALUES
    (1, 'Plataforma virtual', 'Aulas, evaluaciones y recursos de aprendizaje.', 'Académico', 'MONITOR', 99.80, 30),
    (2, 'Matrícula', 'Inscripción, cambios de horario y constancias.', 'Académico', 'GRADUATION', 99.50, 120),
    (3, 'Pagos', 'Validación de pagos, recibos y devoluciones.', 'Administrativo', 'CREDIT', 98.90, 60),
    (4, 'Correo institucional', 'Cuentas, recuperación y configuración.', 'Comunicación', 'MAIL', 99.90, 30),
    (5, 'Internet y Wi-Fi', 'Conectividad en aulas y laboratorios.', 'Infraestructura', 'WIFI', 97.60, 20),
    (6, 'Proyectores', 'Reserva, instalación y soporte audiovisual.', 'Equipamiento', 'PROJECTOR', 96.40, 60),
    (7, 'Soporte técnico', 'Asistencia para equipos y aplicaciones.', 'Soporte', 'HEADSET', 99.20, 15)
) AS s(position, name, description, category, icon, target, minutes)
JOIN catalog_categories c ON c.name = s.category
ORDER BY s.position;
