# Fase 2.2 — cursos, secciones y matrículas locales

La Fase 2.3 incorpora horarios y referencias de materiales sin duplicar este
modelo. Consulta [SCHEDULES_MATERIALS.md](SCHEDULES_MATERIALS.md) para su contrato
y los resultados actuales; el resultado de 99 pruebas al final es histórico de 2.2.

Todo se ejecuta en la misma computadora: Spring Boot en `localhost:8080`, PostgreSQL
de Docker en `localhost:15432` y el frontend existente mediante Vite. No se añade
cloud, hosting, publicación, integración externa ni cambios en React.

## Modelo y tablas de V4

| Entidad / tabla | Campos y relaciones |
| --- | --- |
| `Course` / `academic_courses` | Código único, nombre, idioma, nivel opcional, descripción opcional, estado y fechas de auditoría. |
| `CourseSection` / `academic_course_sections` | Curso obligatorio, código de sección, periodo académico, fechas de inicio/fin, cupo positivo y estado. |
| `Enrollment` / `academic_enrollments` | Perfil estudiantil obligatorio, sección obligatoria, estado y fecha de matrícula. El curso se obtiene de la sección. |

Se reutilizan `StudentProfile` y `UserAccount`. No se duplican credenciales ni
perfiles. Todas las entidades usan UUID y control de versión JPA. Las claves
foráneas no tienen borrados en cascada. Las migraciones V1, V2 y V3 no cambian.

Los códigos se normalizan a mayúsculas sin espacios exteriores. No se presupone
un formato oficial de códigos, idiomas, niveles o periodos del Instituto.
Una sección es única por `(course_id, academic_period, code)`: el mismo código puede
reutilizarse en otro curso o periodo. Las fechas deben existir y fin >= inicio.
Son fechas del periodo de la sección, no horarios de clase de Fase 2.3.

Una matrícula es única por `(student_profile_id, section_id)`, incluso si en el
futuro cambia su estado. Esta fase no impone una prohibición adicional entre
secciones distintas de un mismo curso; una regla institucional diferente deberá
definirse explícitamente antes de incorporarla.

## Estados y reglas de matrícula

- Curso: `ACTIVE` (predeterminado) o `INACTIVE`.
- Sección: `OPEN` (predeterminado), `CLOSED` o `CANCELLED`.
- Matrícula nueva: siempre `ENROLLED`. El modelo reserva `CANCELLED` y `COMPLETED`
  para el seguimiento posterior, sin calcular notas ni progreso.

El alta permite indicar el estado inicial de cursos y secciones. En esta entrega
hay altas y consultas: no se exponen eliminación, edición general, cancelación,
reactivación ni cambios de estado de matrículas.

Para matricular deben existir perfil y sección. La cuenta del perfil debe estar
activa y conservar `ROLE_STUDENT`; el curso debe estar `ACTIVE` y la sección `OPEN`.
Una sección puede prepararse administrativamente bajo un curso inactivo, pero
no recibirá matrículas mientras el curso no esté activo.
Las fechas por sí solas no abren ni cierran inscripciones automáticamente.

Los cupos se calculan contando matrículas `ENROLLED`. Se bloquean primero el perfil
y después la sección durante la transacción para impedir duplicados o sobrecupos
entre peticiones simultáneas. El control agregado de cupos se aplica en la API:
utiliza el endpoint de matrícula y no insertes matrículas directamente con SQL.
La base protege además las referencias, estados, fechas y unicidad.
Desactivar una cuenta o retirar su rol no elimina perfiles ni matrículas existentes.

## Permisos nuevos

V4 inserta únicamente metadatos de permisos, nunca cursos o matrículas de ejemplo:

| Permiso | Rol asignado |
| --- | --- |
| `academic:read` | ADMIN |
| `academic:courses:manage` | ADMIN |
| `academic:sections:manage` | ADMIN |
| `academic:enrollments:manage` | ADMIN |
| `academic:enrollments:read:self` | STUDENT |

Las operaciones administrativas requieren `ROLE_ADMIN` y su permiso específico.
La lectura propia requiere STUDENT, `portal:academic:access` y el permiso de
matrículas propias. La sesión y CSRF son los mismos de Fase 1.

## Endpoints

Prefijo local `http://localhost:8080`:

| Método y ruta | Acceso | Resultado |
| --- | --- | --- |
| `POST /api/v1/academic/courses` | ADMIN + courses:manage + CSRF | 201, curso registrado. |
| `GET /api/v1/academic/courses` | ADMIN + academic:read | Página de cursos. |
| `GET /api/v1/academic/courses/{id}` | ADMIN + academic:read | Curso o 404. |
| `POST /api/v1/academic/courses/{courseId}/sections` | ADMIN + sections:manage + CSRF | 201, sección del curso. |
| `GET /api/v1/academic/courses/{courseId}/sections` | ADMIN + academic:read | Página de secciones del curso. |
| `GET /api/v1/academic/sections/{id}` | ADMIN + academic:read | Sección o 404. |
| `POST /api/v1/academic/enrollments` | ADMIN + enrollments:manage + CSRF | 201, matrícula registrada. |
| `GET /api/v1/academic/enrollments` | ADMIN + academic:read | Página de matrículas. |
| `GET /api/v1/academic/enrollments/{id}` | ADMIN + academic:read | Matrícula o 404. |
| `GET /api/v1/students/me/courses` | STUDENT + permisos de lectura propia | Página exclusivamente propia. |

Los nombres abreviados `courses:manage`, `sections:manage` y `enrollments:manage`
en la tabla llevan el prefijo completo `academic:` mostrado arriba.
Los listados admiten `page` (desde 0) y `size` (1 a 100, predeterminado 20).
El listado administrativo de matrículas admite filtros opcionales
`studentProfileId` y `sectionId`.

Los errores conservan el formato JSON de la aplicación: 400 por campos, fechas,
paginación o JSON inválidos; 401 sin sesión; 403 por rol, permiso o CSRF; 404 por
relaciones inexistentes; 409 por duplicados, cupos o estados que impiden matricular.
Ejemplos de códigos: `COURSE_CODE_EXISTS`, `SECTION_EXISTS`, `ENROLLMENT_EXISTS`,
`SECTION_FULL`, `COURSE_INACTIVE`, `SECTION_NOT_OPEN` y `STUDENT_NOT_ELIGIBLE`.

## Consulta del estudiante y privacidad

`GET /api/v1/students/me/courses` no acepta una identidad seleccionable por el
cliente: el ID se toma de la sesión. El servicio también verifica propiedad y
permisos. Añadir `userId` o `studentProfileId` a la consulta no cambia el propietario.
No existe una ruta estudiantil por ID para leer matrículas ajenas.

Respuesta paginada: `content`, `page`, `size`, `totalElements`, `totalPages`.
Cada elemento contiene `enrollmentId`, `enrollmentStatus`, `enrolledAt`, `course`
y `section`. No incluye listas de compañeros, correos, credenciales ni perfiles
ajenos. El curso y la sección corresponden a la matrícula guardada en PostgreSQL.
Se conserva el estado de cada matrícula para futuras consultas históricas.

Un perfil existente sin matrículas devuelve 200 con `content: []`. Un estudiante
sin perfil devuelve 404 `STUDENT_PROFILE_NOT_FOUND`. ADMIN sin STUDENT no puede
usar el endpoint de estudiante; los administradores consultan mediante sus rutas.

## Ejecutar desde VS Code

Abre `LANYAN-TI` y usa la terminal integrada PowerShell. Sigue [README.md](README.md)
para verificar Compose y cargar `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` desde el
archivo local `.env`, sin imprimir su contraseña. Después, desde la raíz:

```powershell
Set-Location .\backend
.\mvnw.cmd --offline --batch-mode --no-transfer-progress verify
.\mvnw.cmd --offline spring-boot:run
```

El modo offline usa las dependencias ya descargadas. PostgreSQL mantiene su
volumen persistente. No uses `docker compose down -v` ni restablezcas la base.
El arranque valida el esquema JPA y ejecuta las migraciones pendientes con Flyway.

## Registrar datos reales desde PowerShell como ADMIN

No hay semillas académicas. Estos comandos escriben exactamente los valores que
introduzcas: usa únicamente cursos y matrículas que quieras conservar localmente.
No ejecutes ejemplos ficticios contra tu base persistente.

Primero inicia sesión ADMIN y renueva CSRF como explica
[AUTHENTICATION.md](AUTHENTICATION.md). Se reutilizan sus variables `$lanyanApi`,
`$lanyanSession` y `$lanyanHeaders` (con el CSRF renovado).

```powershell
$lanyanCourseBody = @{
    code = Read-Host 'Código real del curso'
    name = Read-Host 'Nombre real del curso'
    language = Read-Host 'Idioma'
    level = Read-Host 'Nivel (opcional)'
    description = Read-Host 'Descripción (opcional)'
    status = 'ACTIVE'
} | ConvertTo-Json
$lanyanCourse = Invoke-RestMethod "$lanyanApi/api/v1/academic/courses" -Method Post -ContentType 'application/json; charset=utf-8' -Body $lanyanCourseBody -Headers $lanyanHeaders -WebSession $lanyanSession

$lanyanSectionBody = @{
    code = Read-Host 'Código de sección'
    academicPeriod = Read-Host 'Periodo académico'
    startDate = Read-Host 'Fecha de inicio (yyyy-MM-dd)'
    endDate = Read-Host 'Fecha de fin (yyyy-MM-dd)'
    capacity = [int](Read-Host 'Cupo de la sección')
    status = 'OPEN'
} | ConvertTo-Json
$lanyanSection = Invoke-RestMethod "$lanyanApi/api/v1/academic/courses/$($lanyanCourse.id)/sections" -Method Post -ContentType 'application/json; charset=utf-8' -Body $lanyanSectionBody -Headers $lanyanHeaders -WebSession $lanyanSession
```

Para obtener el identificador de un perfil existente, puedes consultarlo localmente
desde la raíz del repositorio, en otra terminal (consulta de solo lectura):

```powershell
docker compose exec postgres psql -X -U lanyan_app -d lanyan_ti -c "SELECT p.id AS student_profile_id, p.student_code, u.email FROM academic_student_profiles p JOIN identity_users u ON u.id=p.user_id JOIN identity_user_roles r ON r.user_id=u.id WHERE u.active=true AND r.role_name='ROLE_STUDENT' ORDER BY p.student_code;"
```

`studentProfileId` es el `id` de `StudentProfile`, no el ID de `UserAccount`.
Si todavía no existe un perfil, sigue el procedimiento local de [ACADEMIC.md](ACADEMIC.md)
para una cuenta autorizada; no se crea ni duplica automáticamente al matricular.
Con el perfil correcto, en la terminal de la sesión ADMIN:

```powershell
$lanyanStudentProfileId = Read-Host 'UUID del perfil estudiantil que deseas matricular'
$lanyanEnrollmentBody = @{
    studentProfileId = $lanyanStudentProfileId
    sectionId = $lanyanSection.id
} | ConvertTo-Json
$lanyanEnrollment = Invoke-RestMethod "$lanyanApi/api/v1/academic/enrollments" -Method Post -ContentType 'application/json; charset=utf-8' -Body $lanyanEnrollmentBody -Headers $lanyanHeaders -WebSession $lanyanSession
Invoke-RestMethod "$lanyanApi/api/v1/academic/enrollments?studentProfileId=$lanyanStudentProfileId" -WebSession $lanyanSession
```

## Comprobar las matrículas como estudiante

Utiliza otra sesión de cookies con las credenciales de la cuenta STUDENT vinculada
al perfil anterior. No reutilices la sesión administradora para esta comprobación.

```powershell
$lanyanStudentSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$lanyanStudentCsrf = Invoke-RestMethod "$lanyanApi/api/v1/auth/csrf" -WebSession $lanyanStudentSession -Headers @{Accept='application/json'}
$lanyanStudentHeaders = @{Accept='application/json'}
$lanyanStudentHeaders[$lanyanStudentCsrf.headerName] = $lanyanStudentCsrf.token
$lanyanStudentEmail = Read-Host 'Correo de la cuenta STUDENT'
$lanyanStudentSecret = Read-Host 'Contraseña' -AsSecureString
$lanyanStudentLoginBody = @{
    email = $lanyanStudentEmail
    password = [System.Net.NetworkCredential]::new('', $lanyanStudentSecret).Password
} | ConvertTo-Json
try {
    Invoke-RestMethod "$lanyanApi/api/v1/auth/login" -Method Post -ContentType 'application/json; charset=utf-8' -Body $lanyanStudentLoginBody -Headers $lanyanStudentHeaders -WebSession $lanyanStudentSession | Out-Null
} finally {
    Remove-Variable lanyanStudentLoginBody,lanyanStudentSecret -ErrorAction SilentlyContinue
}
$lanyanMyCourses = Invoke-RestMethod "$lanyanApi/api/v1/students/me/courses?page=0&size=20" -WebSession $lanyanStudentSession -Headers @{Accept='application/json'}
$lanyanMyCourses.content | Select-Object enrollmentId,enrollmentStatus,course,section
```

Los GET no necesitan encabezado CSRF. Windows PowerShell genera una excepción ante
4xx; revisa el código HTTP sin imprimir contraseñas ni cookies.

## Postman exclusivamente local

Usa la aplicación local con Cookie Jar; no utilices agentes cloud ni sincronices
contraseñas. Base URL: `http://localhost:8080`.

1. Obtén CSRF, inicia sesión ADMIN y renueva CSRF después del login.
2. Registra un curso en `/api/v1/academic/courses` con JSON `code`, `name`, `language` y, opcionalmente, `level`,
   `description`, `status`. Guarda su `id` de la respuesta 201.
3. Registra la sección en `/api/v1/academic/courses/{courseId}/sections` con `code`,
   `academicPeriod`, `startDate`, `endDate`, `capacity` y estado opcional.
4. Matricula con JSON `studentProfileId` y `sectionId` en `/api/v1/academic/enrollments`.
   Envía el CSRF renovado en todos los POST.
5. Cierra la sesión o utiliza un Cookie Jar separado e inicia sesión como el
   estudiante. Consulta `/api/v1/students/me/courses` y `/api/v1/auth/me` para
   comprobar que estás usando la identidad correcta.
6. Otra cuenta solo debe ver sus propias matrículas. Las rutas administrativas
   responden 403 a estudiantes, usuarios y técnicos; sin sesión responden 401.

## Pruebas e inventario

`AcademicEnrollmentRulesTest` cubre reglas de dominio y de los servicios.
`AcademicEnrollmentSecurityTest` usa PostgreSQL temporal, HTTP real y sesiones:
altas, consultas, propiedad de datos, permisos, CSRF, errores, cupos y dos carreras
concurrentes (último cupo y matrícula duplicada). Sus datos son ficticios y nunca
se copian a desarrollo. Se conservan las 54 pruebas anteriores y sus comprobaciones.

Nuevos archivos: V4; entidades `Course`, `CourseSection`, `Enrollment`; enums
`CourseStatus`, `SectionStatus`, `EnrollmentStatus`; `AcademicRules`; repositorios
de las tres entidades; servicios `CourseService`, `CourseSectionService`,
`EnrollmentService`; `AcademicException`, `AcademicPages`, `AcademicViews`;
`AcademicDtos`, `AcademicAdminController`, `StudentCoursesController`; los dos
archivos de pruebas y este documento.

Actualizados: seguridad de rutas, `StudentProfileRepository` (bloqueo al matricular),
`AcademicExceptionHandler`, `academic/package-info.java`, `BackendPreparationTest`
y documentación. No cambian autenticación, usuarios, perfiles, V1/V2/V3, Compose
ni el frontend. En esta entrega 2.2 no se desarrollaron horarios, materiales,
notas ni asistencia; horarios y materiales se añaden posteriormente en 2.3.

## Resultado de la verificación local

- Maven `--offline verify`: BUILD SUCCESS, 99 pruebas aprobadas, sin errores ni omisiones.
- Se conservan las 54 anteriores y se añaden 21 unitarias y 24 de integración.
- Se comprobaron matrículas duplicadas y la competencia por el último cupo con
  dos solicitudes HTTP concurrentes: una se acepta y la otra responde 409.
- Desarrollo: V1–V4 aplicadas y diez tablas. V1/V2/V3 mantienen sus checksums.
- Se compararon las huellas de los datos anteriores de usuarios, perfiles, roles,
  permisos y asignaciones: se conservaron sin cambios. V4 agrega cinco permisos.
- Salud local: HTTP 200 / UP; las nuevas consultas privadas sin sesión responden 401.
- Las tablas nuevas de desarrollo contienen cero cursos, secciones y matrículas.
  Los datos ficticios de pruebas se mantienen exclusivamente en Testcontainers.

Reportes: `target/surefire-reports/`. Registros: `target/phase22-verify.log` y
`target/phase22-development.log`. PostgreSQL permanece activo; Java de verificación
se detiene al terminar. Registra tus datos reales mediante las API documentadas
para que cada estudiante vea sus cursos al iniciar sesión.

## Catálogo de cursos (V9)

`GET /api/v1/courses/catalog` devuelve la oferta vigente para `ROLE_STUDENT` y
`ROLE_ADMIN` con el permiso `academic:catalog:read`: cursos `ACTIVE`, cada uno con
sus secciones `OPEN`, los cupos libres (cupo menos matrículas `ENROLLED`) y sus
horarios vigentes, ordenados por día y hora. Un curso activo sin secciones abiertas
aparece con la lista vacía. Es de solo lectura y no expone estudiantes ni matrículas.
Un estudiante sin perfil académico también puede consultarlo. USER y TECHNICIAN
reciben `403`; sin sesión, `401`.

La pantalla de React tiene dos partes: **Cursos** para el estudiante (catálogo con
buscador y filtro por idioma) y **Administración › Cursos** para crear cursos,
secciones y horarios con los endpoints de Fase 2.2 y 2.3. Matricularse desde el
catálogo queda pendiente de decidir quién realiza la matrícula.

Las horas de `academic_class_schedules` se guardan como hora local de Lima, sin
conversión a UTC. Antes, una clase de 18:00 a 20:00 se guardaba como 23:00 a 01:00
y la restricción `ck_schedule_times` la rechazaba; ninguna clase podía terminar
después de las 19:00.

## Automatrícula y perfil del estudiante (V10)

Decisión del 2026-11-11: **el estudiante se matricula solo** desde el catálogo.

- `PUT /api/v1/academic/students/{userId}/profile` (ADMIN + `academic:students:manage`)
  crea o corrige el código de estudiante de una cuenta con rol Estudiante. El código
  es único y se guarda en mayúsculas. `GET` en la misma ruta lo consulta (`404` si no
  existe). En React se completa desde el formulario de usuarios.
- `POST /api/v1/students/me/enrollments` con `{"sectionId": "…"}` (STUDENT +
  `academic:enrollments:create:self` + CSRF) aplica las mismas reglas y bloqueos que
  la matrícula administrativa: cuenta activa, curso activo, sección abierta, sin
  duplicado y con cupo. Además, desde el catálogo no se permite una segunda sección
  del mismo curso (`409 COURSE_ALREADY_ENROLLED`). Sin código de estudiante responde
  `404 STUDENT_PROFILE_NOT_FOUND`.
- Toda matrícula, del estudiante o de la administración, queda en auditoría como
  `ENROLLMENT_CREATED` con el código, curso, sección, periodo y origen.

La pantalla **Mis cursos** reúne los cursos matriculados con su horario y los
materiales publicados (enlaces HTTPS). Anular una matrícula todavía no está disponible.
