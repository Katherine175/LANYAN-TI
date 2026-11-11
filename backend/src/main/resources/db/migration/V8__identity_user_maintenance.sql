-- El administrador mantiene las cuentas: editar datos, activar o desactivar y eliminar.
INSERT INTO identity_permissions (name, description) VALUES
    ('identity:users:update', 'Editar datos, estado y roles de usuarios'),
    ('identity:users:delete', 'Eliminar usuarios sin historial académico');

INSERT INTO identity_role_permissions (role_name, permission_name) VALUES
    ('ROLE_ADMIN', 'identity:users:update'),
    ('ROLE_ADMIN', 'identity:users:delete');
