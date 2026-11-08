# Módulo académico local — Fase 2.1

Este documento describe la Fase 2.1: consulta del perfil estudiantil propio.
La Fase 2.2 posterior incorpora cursos, secciones y matrículas; consulta
[COURSES.md](COURSES.md) para su contrato y operación local.
La Fase 2.3 añade horarios y referencias de materiales por sección:
[SCHEDULES_MATERIALS.md](SCHEDULES_MATERIALS.md).
Reutiliza usuarios, roles, sesiones, BCrypt y CSRF de Fase 1. No modifica React.
Todo funciona en la computadora local: API `http://localhost:8080`, PostgreSQL
de Docker `localhost:15432` y frontend mediante Vite. No se incorporan servicios
externos, cloud, servidores remotos, hosting ni publicación del repositorio.

## Arquitectura implementada

Paquete `pe.edu.unp.lanyan.academic`:

```text
api/
  StudentProfileController.java
  StudentProfileView.java
  AcademicExceptionHandler.java
application/
  StudentProfileService.java
  StudentProfileNotFoundException.java
domain/
  StudentProfile.java
infrastructure/
  StudentProfileRepository.java
```

El perfil referencia `identity.UserAccount` sin duplicar usuarios ni credenciales.
Nombre y correo se consultan desde la cuenta existente. Las credenciales, roles
y permisos no se incluyen en la respuesta del perfil académico.

## Tabla y migración

V3, `V3__academic_student_profiles.sql`, añade `academic_student_profiles`:

| Campo | Uso |
| --- | --- |
| `id` | UUID del perfil. |
| `user_id` | Cuenta existente; obligatorio, único y con clave foránea. |
| `student_code` | Código único de hasta 32 caracteres, en mayúsculas y sin espacios exteriores. |
| `admission_date` | Fecha de ingreso opcional; no se inventa si no está disponible. |
| `created_at`, `updated_at` | Marcas de tiempo de creación del perfil. |
| `version` | Control de concurrencia JPA, preparado para operaciones futuras. |

No se asume un formato oficial de códigos del Instituto: debe confirmarse antes
de una importación institucional. Los ejemplos `TEST-*` son exclusivamente ficticios.
No hay borrados en cascada ni cambios en las migraciones V1 y V2.
V3 no inserta estudiantes, no crea usuarios y no asigna roles automáticamente.

## Endpoint y privacidad

`GET http://localhost:8080/api/v1/students/me`

- `200`: devuelve exclusivamente el perfil de la cuenta autenticada.
- `401`: falta sesión válida o la cuenta ha sido desactivada.
- `403`: no tiene `ROLE_STUDENT` o el permiso `portal:academic:access`.
- `404`: la cuenta es estudiante, pero aún no tiene perfil. Código
  `STUDENT_PROFILE_NOT_FOUND`, con respuesta `application/problem+json`.

El controlador toma el identificador desde `AccountPrincipal`. No existe un
parámetro para seleccionar otra cuenta ni un endpoint público por ID.
Los parámetros de consulta adicionales no cambian la identidad utilizada.
El servicio comprueba también que el ID solicitado sea el de la sesión, además
del rol y el permiso. Esto protege llamadas internas que intenten consultar otro ID.
ADMIN sin STUDENT no puede utilizar este endpoint; ADMIN + STUDENT solo consulta
su propio perfil. Tener una fila de perfil no concede permisos de acceso.
Los cambios de rol y el estado de la cuenta se revalidan con el mecanismo de Fase 1.

Ejemplo **ficticio**, no correspondiente a información institucional real:

```json
{
  "id": "11111111-1111-4111-8111-111111111111",
  "userId": "22222222-2222-4222-8222-222222222222",
  "studentCode": "TEST-STU-001",
  "fullName": "Estudiante FICTICIO de desarrollo",
  "email": "student@example.test",
  "admissionDate": null,
  "createdAt": "2026-01-01T00:00:00Z",
  "updatedAt": "2026-01-01T00:00:00Z"
}
```

No se exponen POST, PUT, PATCH ni DELETE de perfiles en Fase 2.1. Un GET no crea
un perfil automáticamente ni modifica los datos de la cuenta.

## Ejecutar desde VS Code y PowerShell

1. Abre la carpeta raíz `LANYAN-TI` en VS Code y una terminal integrada PowerShell.
2. Comprueba Docker Desktop y el PostgreSQL existente. Si está detenido, inicia
   solo el servicio del proyecto con `docker compose up -d --wait`.
3. Carga la configuración local desde Compose sin imprimir contraseñas:

```powershell
# Desde la raíz LANYAN-TI.
docker compose ps
$lanyanComposeJson = docker compose config --format json
if ($LASTEXITCODE -ne 0) { throw 'Revisa la configuración local de Compose.' }
$lanyanCompose = $lanyanComposeJson | ConvertFrom-Json
$lanyanDb = $lanyanCompose.services.postgres.environment
$env:DB_URL = 'jdbc:postgresql://localhost:{0}/{1}' -f $lanyanCompose.services.postgres.ports[0].published,$lanyanDb.POSTGRES_DB
$env:DB_USERNAME = $lanyanDb.POSTGRES_USER
$env:DB_PASSWORD = $lanyanDb.POSTGRES_PASSWORD
$env:SERVER_PORT = '8080'
Set-Location .\backend
.\mvnw.cmd --offline --batch-mode --no-transfer-progress verify
.\mvnw.cmd --offline spring-boot:run
```

El arranque aplica las migraciones pendientes mediante Flyway y mantiene Hibernate
en `ddl-auto=validate`. El modo offline utiliza las dependencias ya descargadas.
No se cambia la configuración de Vite ni se conecta todavía el frontend a esta API.

## Alta local de un perfil ficticio para probar

Primero crea una cuenta ficticia `student@example.test` con `ROLE_STUDENT` mediante
el procedimiento administrativo ya documentado en [AUTHENTICATION.md](AUTHENTICATION.md).
Si todavía no hay administrador, utiliza allí el procedimiento `bootstrap-admin`.
No uses credenciales ni datos reales de estudiantes o trabajadores.

El alta del perfil en esta subfase es un procedimiento SQL local explícito, no un
endpoint nuevo. Desde la raíz del repositorio:

```powershell
docker compose exec postgres psql -X -U lanyan_app -d lanyan_ti
```

Dentro de `psql`, solo en tu base local y para la cuenta ficticia seleccionada:

```sql
BEGIN;
INSERT INTO academic_student_profiles (id, user_id, student_code, admission_date)
SELECT gen_random_uuid(), u.id, 'TEST-STU-001', NULL
FROM identity_users u
JOIN identity_user_roles ur ON ur.user_id = u.id
WHERE u.email = 'student@example.test'
  AND u.active = true
  AND ur.role_name = 'ROLE_STUDENT';
COMMIT;
\q
```

Debe mostrar `INSERT 0 1`. Si muestra `INSERT 0 0`, falta la cuenta activa con rol
STUDENT. Si ya existe el perfil o el código, la restricción rechaza la operación;
ejecuta `ROLLBACK;` si la transacción quedó en error. No borres ni sobrescribas
perfiles para repetir el ejemplo: consulta primero el registro existente.
El SQL no cambia cuentas, contraseñas ni asignaciones de rol.

## Consultar desde PowerShell

Con el backend en ejecución, abre otra terminal PowerShell:

```powershell
$lanyanApi = 'http://localhost:8080'
$lanyanSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$lanyanCsrf = Invoke-RestMethod "$lanyanApi/api/v1/auth/csrf" -WebSession $lanyanSession -Headers @{Accept='application/json'}
$lanyanHeaders = @{Accept='application/json'}
$lanyanHeaders[$lanyanCsrf.headerName] = $lanyanCsrf.token
$lanyanEmail = Read-Host 'Correo de la cuenta ficticia STUDENT'
$lanyanSecret = Read-Host 'Contraseña de la cuenta ficticia' -AsSecureString
$lanyanBody = @{
    email = $lanyanEmail
    password = [System.Net.NetworkCredential]::new('', $lanyanSecret).Password
} | ConvertTo-Json
try {
    Invoke-RestMethod "$lanyanApi/api/v1/auth/login" -Method Post -ContentType 'application/json' -Body $lanyanBody -Headers $lanyanHeaders -WebSession $lanyanSession | Out-Null
} finally {
    Remove-Variable lanyanBody,lanyanSecret -ErrorAction SilentlyContinue
}
Invoke-RestMethod "$lanyanApi/api/v1/students/me" -WebSession $lanyanSession -Headers @{Accept='application/json'}
```

Este GET no necesita encabezado CSRF. Para POST de logout u otras operaciones de
Fase 1 debes renovar CSRF tras login según `AUTHENTICATION.md`.
Sin perfil el resultado correcto es 404, no un perfil inventado. PowerShell lanza
una excepción ante respuestas 4xx; comprueba su código HTTP.

## Consultar desde Postman local

Utiliza la aplicación local y peticiones a `http://localhost:8080`; no utilices
agentes cloud, sincronización de credenciales ni servidores externos.

1. `GET /api/v1/auth/csrf`, conservando la cookie con el Cookie Jar local.
2. `POST /api/v1/auth/login` con JSON `email`/`password` y el token en el encabezado
   indicado por `headerName`, normalmente `X-CSRF-TOKEN`.
3. `GET /api/v1/students/me` con la misma cookie: debe devolver el perfil propio.
4. Otra cuenta STUDENT devuelve su propio perfil; USER/TECHNICIAN/ADMIN sin
   STUDENT reciben 403. Sin sesión, 401. STUDENT sin perfil recibe 404.
5. Cambiar un parámetro `userId` no permite seleccionar otro estudiante.

## Estado del plan académico

| Subfase | Entidades previstas | Relaciones principales |
| --- | --- | --- |
| 2.1 actual | `StudentProfile` | Uno por `UserAccount`; identidad compartida. |
| 2.2 implementada | `Course`, `CourseSection`, `Enrollment` | Altas y consultas administrativas, cupos y consulta privada de cursos. Ver `COURSES.md`. |
| 2.3 implementada | `ClassSchedule`, `CourseMaterial` | Horarios por sección y referencias validadas; CRUD administrativo y lectura según matrícula. Sin almacenamiento/descarga de archivos. |
| 2.4 pendiente | `Grade`, `Attendance` | Notas y asistencia vinculadas a matrícula y, cuando corresponda, sesión/evaluación. |
| 2.5 pendiente | Progreso académico | Consultas derivadas de matrículas, notas y asistencia; no un porcentaje ficticio en el perfil. |

Para docentes se prevé reutilizar `UserAccount` y su relación de múltiples roles,
añadiendo un rol docente y un perfil docente separado cuando se autorice.
`CourseSection` podrá referenciar a ese docente; los permisos deberán limitarse
a sus secciones. No se crea `ROLE_TEACHER` ni se insertan docentes o cursos de ejemplo.
El soporte TI utilizará los tickets centrales cuando se implemente esa fase.

## Pruebas y archivos

`StudentProfileRulesTest` cubre validación de código, mapeo a DTO y perfil ausente.
`StudentProfileSecurityTest` usa HTTP real, sesiones y PostgreSQL de Testcontainers:
propiedad de datos, roles, permisos, perfil inexistente, revocación, desactivación,
restricciones de base de datos y ausencia de operaciones de escritura por HTTP.
Todos los datos son ficticios. Las pruebas anteriores de Fase 1 se conservan;
`BackendPreparationTest` incorpora V3 y la séptima tabla.

Archivos nuevos: paquete `academic/` y sus siete clases, `academic/package-info.java`,
V3, los dos archivos de pruebas y este documento. Archivos ajustados:
`SecurityConfiguration.java`, `BackendPreparationTest.java`, `README.md` y
`AUTHENTICATION.md`. No cambian V1, V2, Compose ni el frontend.

## Verificación local de esta entrega

- Maven `--offline verify`: BUILD SUCCESS, 54 pruebas aprobadas, sin errores ni omisiones.
- Se conservan las 33 pruebas de Fase 1; se añaden 8 pruebas unitarias y 13 de
  integración para perfiles estudiantiles.
- Desarrollo local: V1, V2 y V3 aplicadas; siete tablas. V1/V2 conservan sus
  checksums y los recuentos de usuarios, roles, permisos y relaciones no cambian.
- Salud en `http://localhost:8080/actuator/health`: HTTP 200 y estado UP.
- Consulta académica sin sesión desde PowerShell: HTTP 401.
- No se insertaron usuarios ni perfiles de ejemplo en desarrollo. Las pruebas
  de acceso positivo y aislamiento entre estudiantes usan solo datos ficticios
  en PostgreSQL temporal de Testcontainers.

Los reportes están en `target/surefire-reports/`; registros de esta fase:
`target/phase21-verify.log` y `target/phase21-development.log`.
PostgreSQL se mantiene activo y el proceso Java de verificación se detiene;
inícialo desde la terminal de VS Code con las instrucciones anteriores.
