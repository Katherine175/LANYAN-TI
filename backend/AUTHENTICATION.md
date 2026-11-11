# Fase 1 — identidad y autenticación

La identidad es única para los futuros portales TI y académico. Esta fase no
implementa pantallas React, cursos, perfiles estudiantiles ni tickets.
La Fase 2.1 posterior incorpora la consulta del perfil estudiantil propio;
consulta [ACADEMIC.md](ACADEMIC.md). Se mantiene la misma autenticación.

## Estrategia y protección

Se utilizan sesiones de servidor, evitando almacenar tokens de acceso en
`localStorage` o `sessionStorage`. La cookie `LANYAN_SESSION` es `HttpOnly` y
`SameSite=Lax`; en HTTPS debe configurarse `SESSION_COOKIE_SECURE=true`.
La sesión vence tras 30 minutos de inactividad y se pierde al reiniciar Java.
Un despliegue con varias instancias requerirá evaluar almacenamiento compartido
de sesiones; no es necesario para el monolito local actual.

El login utiliza `DaoAuthenticationProvider`, correo normalizado y BCrypt con
coste 12. La contraseña nueva exige 12 caracteres como mínimo y 72 bytes UTF-8
como máximo; no se trunca silenciosamente. Los errores de correo inexistente,
contraseña incorrecta y cuenta desactivada comparten la respuesta `401` con código
`INVALID_CREDENTIALS`. No se exponen hashes ni contraseñas en los DTO.

CSRF permanece habilitado también para login y logout. Obtén un token mediante
`GET /api/v1/auth/csrf` y envíalo en el encabezado indicado por `headerName`.
Tras login y logout debes volver a solicitarlo. La sesión cambia de identificador
al autenticar y el token anterior queda invalidado.

Por defecto se admiten 20 intentos de login por dirección remota en cinco minutos;
después se responde `429`. `AUTH_LOGIN_LIMIT` y `AUTH_LOGIN_WINDOW` permiten ajustar
el límite para una red institucional. La memoria del limitador está acotada y se
reinicia con Java. No se confía en encabezados `X-Forwarded-For` enviados por el
cliente; antes de desplegar detrás de un proxy se debe configurar su confianza.

En cada petición autenticada se releen estado y permisos. Una cuenta desactivada
pierde su sesión y un cambio de roles se aplica en la petición siguiente.
El listado administrativo está paginado, con tamaño máximo de 100.

## Roles y permisos

| Rol | Capacidades de Fase 1 |
| --- | --- |
| `ROLE_ADMIN` | Consultar, crear, editar y eliminar usuarios; consultar y asignar roles; consultar su identidad. |
| `ROLE_TECHNICIAN` | Autenticarse y consultar su identidad; preparado para acceso TI. |
| `ROLE_USER` | Autenticarse y consultar su identidad; preparado para acceso TI. |
| `ROLE_STUDENT` | Autenticarse y consultar su identidad; Fase 2.1 añade la consulta de su perfil académico propio. |

Un usuario puede tener varios roles. Los permisos `portal:it:access` y
`portal:academic:access` son capacidades preparadas para los futuros portales;
no significan que sus módulos o endpoints ya estén implementados.
La asignación administrativa cambia el conjunto completo de roles. No se permite
que un administrador retire su propio rol de administrador. Los cambios se
serializan en PostgreSQL y se comprueba que el actor siga siendo administrador.

## Correo institucional y mantenimiento de cuentas (V8)

Las cuentas nuevas solo se crean con correo `@unp.edu.pe`, sin subdominios; se
rechazan con `400 EMAIL_DOMAIN_NOT_ALLOWED`. La regla se aplica también al primer
administrador y a cualquier cambio de correo. Las cuentas anteriores a la regla
pueden seguir ingresando y editarse sin cambiar su correo.

El administrador edita nombre, correo, estado, roles y, si lo indica, una nueva
contraseña. No puede desactivarse, eliminarse ni quitarse el rol de administrador
a sí mismo. Una cuenta con perfil académico no se elimina (`409
USER_HAS_ACADEMIC_PROFILE`): se desactiva para conservar matrículas e historial.
Editar y eliminar quedan en auditoría (`USER_UPDATED`, `USER_DELETED`); el
registro sobrevive a la cuenta eliminada.

## Endpoints

Base local: `http://localhost:8080`.

| Método y ruta | Acceso | Resultado |
| --- | --- | --- |
| `GET /actuator/health` | Público | `200`, salud sin detalles internos. |
| `GET /api/v1/auth/csrf` | Público | `200`, token CSRF y nombre del encabezado. |
| `POST /api/v1/auth/login` | Público + CSRF | `200`, identidad y cookie de sesión. |
| `GET /api/v1/auth/me` | Autenticado | `200`, identidad propia, roles y permisos. |
| `POST /api/v1/auth/logout` | CSRF | `204`, sesión y cookie invalidadas. |
| `GET /api/v1/users?page=0&size=20` | ADMIN + `identity:users:read` | `200`, página de usuarios. |
| `GET /api/v1/users/{id}` | ADMIN + `identity:users:read` | `200` o `404`. |
| `POST /api/v1/users` | ADMIN + `identity:users:create` + CSRF | `201`, nueva cuenta. |
| `PUT /api/v1/users/{id}` | ADMIN + `identity:users:update` + `identity:roles:assign` + CSRF | `200`, nombre, correo, estado, roles y contraseña opcional. |
| `DELETE /api/v1/users/{id}` | ADMIN + `identity:users:delete` + CSRF | `204`, cuenta eliminada; `409` si es la propia o tiene perfil académico. |
| `PUT /api/v1/users/{id}/roles` | ADMIN + `identity:roles:assign` + CSRF | `200`, roles actualizados. |
| `GET /api/v1/roles` | ADMIN + `identity:roles:assign` | `200`, roles y permisos. |

La Fase 2.1 añade `GET /api/v1/students/me` para estudiantes autenticados.
La Fase 2.2 incorpora cursos, secciones y matrículas con permisos académicos;
consulta [COURSES.md](COURSES.md). No existen `/register` ni operaciones de tickets.
Errores: `400` validación/JSON, `401` credenciales o sesión, `403` permisos/CSRF,
`404` usuario inexistente, `409` duplicados/conflictos y `429` límite de login.
Los errores usan JSON `application/problem+json` con `status` y `code`.

## Primer administrador: procedimiento controlado

No hay cuentas ni contraseñas predeterminadas. El perfil `bootstrap-admin` crea
una sola cuenta administradora **solo si la base no contiene usuarios**. Se ejecuta
sin servidor HTTP y termina al completar la operación. Si ya existen usuarios,
rechaza la operación sin modificar sus credenciales. El perfil normal no crea
cuentas automáticamente. Nunca utilices credenciales reales de estudiantes en pruebas.

Primero ejecuta las instrucciones de [README.md](README.md) para iniciar Compose y
transferir `DB_*` a la terminal. Luego, desde `backend/`, introduce datos de una
cuenta local de desarrollo (los ejemplos de prueba deben ser ficticios):

```powershell
$env:LANYAN_ADMIN_NAME = Read-Host 'Nombre del administrador local'
$env:LANYAN_ADMIN_EMAIL = Read-Host 'Correo del administrador local'
$lanyanAdminSecret = Read-Host 'Contraseña nueva (mínimo 12 caracteres)' -AsSecureString
$env:LANYAN_ADMIN_PASSWORD = [System.Net.NetworkCredential]::new('', $lanyanAdminSecret).Password
try {
    .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=bootstrap-admin'
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo provisionar; revisa el motivo sin repetir contra otra base.' }
} finally {
    Remove-Item Env:LANYAN_ADMIN_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:LANYAN_ADMIN_NAME,Env:LANYAN_ADMIN_EMAIL -ErrorAction SilentlyContinue
    Remove-Variable lanyanAdminSecret -ErrorAction SilentlyContinue
}
.\mvnw.cmd spring-boot:run
```

También puedes usar el JAR verificado:

```powershell
java -jar .\target\lanyan-ti-0.0.1-SNAPSHOT.jar --spring.profiles.active=bootstrap-admin
```

Este último comando requiere definir primero las mismas variables `LANYAN_ADMIN_*`.
No guardes esas contraseñas en Git, argumentos de Java ni ejemplos de Postman.

## Probar desde Windows PowerShell

Con Java ejecutándose, abre otra terminal. El contenedor de desarrollo usa el
puerto local 15432 según `.env`; la API sigue usando 8080.

```powershell
$lanyanApi = 'http://localhost:8080'
$lanyanSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$lanyanCsrf = Invoke-RestMethod "$lanyanApi/api/v1/auth/csrf" -WebSession $lanyanSession
$lanyanHeaders = @{}
$lanyanHeaders[$lanyanCsrf.headerName] = $lanyanCsrf.token
$lanyanEmail = Read-Host 'Correo de la cuenta local'
$lanyanSecret = Read-Host 'Contraseña' -AsSecureString
$lanyanLoginBody = @{
    email = $lanyanEmail
    password = [System.Net.NetworkCredential]::new('', $lanyanSecret).Password
} | ConvertTo-Json
try {
    $lanyanMe = Invoke-RestMethod "$lanyanApi/api/v1/auth/login" -Method Post -ContentType 'application/json' -Body $lanyanLoginBody -Headers $lanyanHeaders -WebSession $lanyanSession
    $lanyanMe | Select-Object id,fullName,email,roles,permissions
} finally {
    Remove-Variable lanyanLoginBody,lanyanSecret -ErrorAction SilentlyContinue
}

# Renovar CSRF tras el login.
$lanyanCsrf = Invoke-RestMethod "$lanyanApi/api/v1/auth/csrf" -WebSession $lanyanSession
$lanyanHeaders = @{}
$lanyanHeaders[$lanyanCsrf.headerName] = $lanyanCsrf.token
Invoke-RestMethod "$lanyanApi/api/v1/auth/me" -WebSession $lanyanSession

# Solo para una sesión administradora.
Invoke-RestMethod "$lanyanApi/api/v1/users?page=0&size=20" -WebSession $lanyanSession
Invoke-RestMethod "$lanyanApi/api/v1/roles" -WebSession $lanyanSession
```

Para crear una cuenta ficticia de estudiante y usuario TI desde una sesión ADMIN:

```powershell
$lanyanNewSecret = Read-Host 'Contraseña de la nueva cuenta ficticia' -AsSecureString
$lanyanNewBody = @{
    fullName = 'Estudiante FICTICIO de desarrollo'
    email = 'student@example.test'
    password = [System.Net.NetworkCredential]::new('', $lanyanNewSecret).Password
    roles = @('ROLE_STUDENT','ROLE_USER')
} | ConvertTo-Json
try {
    $lanyanCreated = Invoke-RestMethod "$lanyanApi/api/v1/users" -Method Post -ContentType 'application/json' -Body $lanyanNewBody -Headers $lanyanHeaders -WebSession $lanyanSession
} finally {
    Remove-Variable lanyanNewBody,lanyanNewSecret -ErrorAction SilentlyContinue
}
$lanyanRolesBody = @{ roles = @('ROLE_STUDENT') } | ConvertTo-Json
Invoke-RestMethod "$lanyanApi/api/v1/users/$($lanyanCreated.id)/roles" -Method Put -ContentType 'application/json' -Body $lanyanRolesBody -Headers $lanyanHeaders -WebSession $lanyanSession

# Cerrar la sesión; /auth/me deberá responder 401 después.
Invoke-RestMethod "$lanyanApi/api/v1/auth/logout" -Method Post -Headers $lanyanHeaders -WebSession $lanyanSession
```

Los comandos de creación cambian datos: ejecútalos solo con cuentas ficticias en
tu base de desarrollo. Para comprobar permisos de estudiante, abre otra sesión
`WebRequestSession`, inicia sesión con esa cuenta y consulta `/api/v1/users`:
debe responder `403`. Sin sesión debe responder `401`.
Windows PowerShell lanza una excepción ante respuestas 4xx; revisa el estado HTTP
en el error. No imprimas variables que contengan contraseñas ni cookies.

## Probar con Postman

1. Desactiva la sincronización de secretos y utiliza variables locales para las
   credenciales ficticias; no compartas colecciones con contraseñas o cookies.
2. Solicita `GET /api/v1/auth/csrf`. Conserva la cookie mediante el Cookie Jar.
3. Copia `token` al encabezado cuyo nombre indica `headerName` (normalmente
   `X-CSRF-TOKEN`). Envía `POST /api/v1/auth/login` con JSON `email` y `password`.
4. Solicita de nuevo CSRF y usa el token nuevo en POST/PUT posteriores.
5. Consulta `/api/v1/auth/me` y prueba `/api/v1/users` como ADMIN (200) y como
   STUDENT/USER/TECHNICIAN (403).
6. Comprueba contraseña incorrecta, correo inexistente o cuenta desactivada (401),
   un POST sin CSRF (403), y finalmente `POST /api/v1/auth/logout` (204).
7. Tras logout, `/api/v1/auth/me` devuelve 401. No necesitas un Bearer token.

## Pruebas automatizadas y límites de esta entrega

`BackendPreparationTest` conserva salud y protección de rutas, validando ahora
V1–V5 y las doce tablas desde Fase 2.3. `IdentitySecurityTest` usa HTTP real, cookies y PostgreSQL
temporal para comprobar los cuatro roles, credenciales, CSRF, sesiones, usuarios,
asignación y revocación de permisos. `IdentityRulesTest` cubre la política de
contraseñas, el limitador y la regresión con `Set.of(...)`.
Todos sus datos son ficticios y no se conectan a la base de Compose.

No hay registro público, recuperación de contraseña, edición de perfiles ni una
API de activación/desactivación en esta entrega. El estado `active` sí se respeta
en login y sesiones; su administración institucional se ampliará posteriormente.
Antes de producción deben configurarse HTTPS, cookies Secure, proxy de confianza,
secretos y privilegios mínimos de base de datos. No se afirma validación de
producción ni integración con React: ambas quedan fuera de esta Fase 1.

Referencias: [sesiones de Spring Security](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html),
[CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).

## Inventario de archivos de Fase 1

Modificados, relativos a `backend/`:

```text
.env.example
README.md
src/main/java/pe/edu/unp/lanyan/LanyanTiApplication.java
src/main/java/pe/edu/unp/lanyan/configuration/SecurityConfiguration.java
src/main/java/pe/edu/unp/lanyan/identity/package-info.java
src/main/java/pe/edu/unp/lanyan/identity/api/IdentityDtos.java
src/main/java/pe/edu/unp/lanyan/identity/application/UserService.java
src/main/resources/application.yml
src/test/java/pe/edu/unp/lanyan/BackendPreparationTest.java
```

Creados:

```text
AUTHENTICATION.md
src/main/java/pe/edu/unp/lanyan/configuration/IdentityConfiguration.java
src/main/java/pe/edu/unp/lanyan/identity/api/AuthController.java
src/main/java/pe/edu/unp/lanyan/identity/api/UserController.java
src/main/java/pe/edu/unp/lanyan/identity/api/ApiExceptionHandler.java
src/main/java/pe/edu/unp/lanyan/identity/application/AuthenticationService.java
src/main/java/pe/edu/unp/lanyan/identity/application/LoginAttemptLimiter.java
src/main/java/pe/edu/unp/lanyan/identity/application/BootstrapAdminProvisioner.java
src/main/java/pe/edu/unp/lanyan/identity/infrastructure/AccountPrincipal.java
src/main/java/pe/edu/unp/lanyan/identity/infrastructure/AccountDetailsService.java
src/main/java/pe/edu/unp/lanyan/identity/infrastructure/CurrentAccountFilter.java
src/main/java/pe/edu/unp/lanyan/identity/infrastructure/SecurityResponses.java
src/main/java/pe/edu/unp/lanyan/identity/infrastructure/BootstrapAdminRunner.java
src/main/resources/application-bootstrap-admin.yml
src/test/java/pe/edu/unp/lanyan/IdentitySecurityTest.java
src/test/java/pe/edu/unp/lanyan/IdentityRulesTest.java
```

V1 y V2 se conservan sin cambios. Compose y el frontend no se modifican.

## Resultado de verificación del 6 de noviembre de 2026

- Maven `verify`: BUILD SUCCESS, 33 pruebas aprobadas, sin errores ni omisiones.
- Infraestructura: 8 pruebas; reglas unitarias: 7; seguridad HTTP: 18.
- Bootstrap con JAR y PostgreSQL temporal: primer intento termina con código 0;
  segundo intento termina con código 1 y no permite crear otro administrador.
- Desarrollo en PostgreSQL de Compose: V1 y V2 aplicadas, checksum de V1 conservado,
  seis tablas, cuatro roles, cinco permisos y cero usuarios.
- JAR contra desarrollo: `GET /actuator/health` devuelve HTTP 200 y estado UP;
  CSRF y rechazo de credenciales ficticias se verificaron desde PowerShell.

Los reportes están en `target/surefire-reports/` y los registros de verificación
en `target/phase1-security-verify.log` y `target/phase1-development.log`.
El proceso Java de verificación se detiene al terminar; PostgreSQL permanece activo.
El primer administrador de desarrollo debe provisionarse explícitamente con los
pasos anteriores. Las cuentas ficticias de las pruebas no se copian a desarrollo.
