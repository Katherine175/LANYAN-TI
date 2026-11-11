-- Catálogo de cursos: el estudiante consulta la oferta vigente; el administrador la revisa como la ve el estudiante.
INSERT INTO identity_permissions (name, description) VALUES
    ('academic:catalog:read', 'Consultar el catálogo de cursos y secciones abiertas');

INSERT INTO identity_role_permissions (role_name, permission_name) VALUES
    ('ROLE_STUDENT', 'academic:catalog:read'),
    ('ROLE_ADMIN', 'academic:catalog:read');
