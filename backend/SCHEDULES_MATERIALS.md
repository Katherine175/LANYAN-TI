# Horarios y materiales — Fase 2.3 (solo desarrollo local)

Este módulo reutiliza usuarios, sesiones, permisos, perfiles y matrículas. No modifica
React ni implementa notas, asistencia, progreso, descargas o subida de archivos.
Los materiales son **referencias**, no archivos almacenados por esta API.

## Modelo y migración

V5 añade dos tablas con UUID, relación obligatoria a `academic_course_sections`,
`created_at`, `updated_at`, `deleted_at` y `version`:

| Tabla | Datos y restricciones |
| --- | --- |
| `academic_class_schedules` | Día ISO 1–7, inicio, fin, modalidad y aula. Fin posterior al inicio, precisión de minutos y aula requerida para presencial/híbrido. Índice único de franja activa. |
| `academic_course_materials` | Título, descripción, tipo, tipo de referencia, referencia normalizada y estado. Referencia activa única por sección; enums, título y vínculo HTTPS para LINK validados. |

Las claves foráneas no tienen borrado en cascada. V1–V4 se conservan intactas.
V5 solo inserta cuatro permisos y sus asignaciones; no añade alumnos, cursos,
horarios ni materiales ficticios a desarrollo.

Días del JSON: `MONDAY`, `TUESDAY`, `WEDNESDAY`, `THURSDAY`, `FRIDAY`,
`SATURDAY`, `SUNDAY`. Horas locales en formato `HH:mm`, zona `America/Lima`.
Cada horario se repite semanalmente durante las fechas de la sección, que se
incluyen en la respuesta. La consulta devuelve la planificación, no una agenda
de eventos generados ni un filtro automático por la fecha actual.

Modalidad: `IN_PERSON`, `ONLINE`, `HYBRID`. Aula opcional para ONLINE.
No se permiten intervalos nocturnos que crucen medianoche ni segundos/fracciones.
Se rechazan cruces dentro de la misma sección y día (incluyendo intervalos
contenidos); franjas contiguas sí se permiten. No se comprueban cruces de aulas,
docentes o alumnos entre secciones diferentes: esa política no está implementada.

## Referencias seguras

- Tipo de material: `DOCUMENT`, `GUIDE` o `LINK`.
- Tipo de referencia: `LOCAL_REFERENCE` o `HTTPS_URL`.
- Estado: `DRAFT` (predeterminado) o `PUBLISHED`.
- LINK exige HTTPS_URL; documentos y guías admiten ambos tipos.
- LOCAL_REFERENCE es un identificador relativo lógico de máximo 1024 caracteres,
  como `cursos/ingles/guia.pdf`. Se normaliza a minúsculas. Solo admite segmentos
  alfanuméricos con guion, punto y guion bajo. Rechaza rutas absolutas, espacios,
  barras inversas, dos puntos, porcentajes, segmentos vacíos y cualquier `..`.
  No se resuelve contra Windows ni se lee el archivo.
- HTTPS_URL admite hasta 2048 caracteres, host DNS, HTTPS y puerto predeterminado
  o 443. Rechaza credenciales en URL, IP literales, localhost, dominios .local,
  .localhost y .internal, controles y barras inversas. Normaliza host/esquema,
  puerto 443 y segmentos de ruta. No resuelve DNS, sigue redirecciones ni realiza
  solicitudes a la URL.

La validación es sintáctica: **no garantiza** disponibilidad, contenido seguro o
privacidad del destino. La API protege quién puede consultar los metadatos; un
enlace externo compartido puede ser accesible fuera del sistema. No registres
credenciales, tokens de acceso o enlaces con secretos. Para documentos privados
físicos se requiere una futura entrega con almacenamiento y descarga autorizada.
Los enlaces pueden apuntar a material educativo existente; el servidor sigue
siendo exclusivamente local y no integra ningún servicio externo.

## Acceso y endpoints

ADMIN requiere `academic:read` para consultas y el permiso de gestión
correspondiente para escrituras. STUDENT requiere `portal:academic:access` y el
permiso de lectura propia específico. TECHNICIAN y USER carecen de acceso a estas
funciones. Los permisos se revalidan mediante la seguridad existente en cada petición.

| Método | Ruta | Acceso / resultado |
| --- | --- | --- |
| GET | `/api/v1/students/me/schedule` | STUDENT, horarios propios, 200 |
| GET | `/api/v1/students/me/materials` | STUDENT, materiales publicados propios, 200 |
| GET / POST | `/api/v1/academic/sections/{sectionId}/schedules` | ADMIN, listar / crear (200 / 201) |
| GET / PUT / DELETE | `/api/v1/academic/schedules/{id}` | ADMIN, consultar / sustituir / retirar (200 / 200 / 204) |
| GET / POST | `/api/v1/academic/sections/{sectionId}/materials` | ADMIN, listar / crear (200 / 201) |
| GET / PUT / DELETE | `/api/v1/academic/materials/{id}` | ADMIN, consultar / sustituir / retirar (200 / 200 / 204) |

Nuevos permisos: `academic:schedules:manage`, `academic:materials:manage` (ADMIN);
`academic:schedules:read:self`, `academic:materials:read:self` (STUDENT).

Todas las listas admiten `page=0&size=20`; size entre 1 y 100. Respuesta:
`content`, `page`, `size`, `totalElements`, `totalPages`.
Horarios ordenados por día ISO, inicio e ID; materiales por creación descendente e ID.
Cada elemento incluye `section` (ID, curso, periodo y fechas), sus datos y versión.

El estudiante se obtiene de la sesión; no se acepta seleccionar otra identidad.
Solo se muestran registros no retirados en secciones no CANCELLED con matrícula
ENROLLED del perfil propio; materiales además PUBLISHED. CLOSED permite lectura.
CANCELLED/COMPLETED en la matrícula impiden acceso al contenido. La inactividad
del curso por sí sola no cancela matrículas existentes. Sin perfil: 404
STUDENT_PROFILE_NOT_FOUND; sin matrículas/contenido elegible: página vacía 200.

POST/PUT/DELETE exigen cookie de sesión y CSRF válido. PUT es sustitución completa
de los campos editables y exige `version` actual en JSON; la sección es inmutable.
DELETE exige `?version=N` y realiza **baja lógica**: conserva la fila, oculta el
registro y permite recrear la franja/referencia. Consultarlo o retirarlo de nuevo
devuelve 404. Versión ausente: 400 VERSION_REQUIRED; desactualizada: 409
STALE_RESOURCE. Consulta primero y usa la nueva versión devuelta tras cada cambio.

401: falta sesión o cuenta desactivada; 403: rol/permiso o CSRF insuficiente;
400: datos, JSON, UUID o paginación inválidos; 404: registro/sección inexistente;
409 SCHEDULE_OVERLAP / MATERIAL_EXISTS: cruce o duplicado.
Las restricciones de BD/concurrencia pueden devolver 409 DATA_CONFLICT.
Las mutaciones bloquean la sección antes de validar; dos POST conflictivos no
insertan dos registros. La versión también detecta ediciones concurrentes.

## Iniciar desde VS Code / Windows PowerShell

Abre la carpeta LANYAN-TI y una terminal PowerShell. Docker Desktop debe usar
contenedores Linux. Desde la raíz, conserva el .env y el volumen existentes:

```powershell
docker version
docker compose up -d --wait --wait-timeout 90
docker compose ps
$lanyanConfigJson = docker compose config --format json
if ($LASTEXITCODE -ne 0) { throw 'Configuracion Compose invalida.' }
$lanyanConfig = $lanyanConfigJson | ConvertFrom-Json
$lanyanDb = $lanyanConfig.services.postgres.environment
$lanyanPort = $lanyanConfig.services.postgres.ports[0].published
$env:DB_URL = 'jdbc:postgresql://localhost:{0}/{1}' -f $lanyanPort,$lanyanDb.POSTGRES_DB
$env:DB_USERNAME = $lanyanDb.POSTGRES_USER
$env:DB_PASSWORD = $lanyanDb.POSTGRES_PASSWORD
$env:SERVER_PORT = '8080'
Set-Location backend
.\mvnw.cmd --offline verify
.\mvnw.cmd --offline spring-boot:run
```

El entorno actual usa 15432 para PostgreSQL. No imprimas `$lanyanConfigJson`,
`$lanyanDb` ni las contraseñas. Offline requiere dependencias/imagen en caché;
la compilación de esta entrega usa esa modalidad. Spring Boot valida Flyway y
aplica V5 al arrancar; Hibernate mantiene `ddl-auto=validate`.
En otra terminal:

```powershell
Invoke-RestMethod 'http://localhost:8080/actuator/health'
```

Resultado esperado: UP. Ctrl+C detiene solo Java; PostgreSQL conserva su volumen.
No uses operaciones de limpieza de Docker ni `down -v`.

## PowerShell: sesión y consulta del estudiante

Esta función mantiene cookie y renueva CSRF después del login. Usa credenciales
de una cuenta local autorizada ya existente; no crea usuarios ni datos de prueba.

```powershell
$lanyanApi = 'http://localhost:8080'
function Connect-LanyanLocal {
    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $csrf = Invoke-RestMethod "$lanyanApi/api/v1/auth/csrf" -WebSession $session
    $headers = @{Accept='application/json'}
    $headers[$csrf.headerName] = $csrf.token
    $email = Read-Host 'Correo local autorizado'
    $secret = Read-Host 'Contrasena' -AsSecureString
    $body = @{
        email=$email
        password=[System.Net.NetworkCredential]::new('', $secret).Password
    } | ConvertTo-Json
    try {
        Invoke-RestMethod "$lanyanApi/api/v1/auth/login" -Method Post -WebSession $session -Headers $headers -ContentType 'application/json; charset=utf-8' -Body $body | Out-Null
    } finally {
        Remove-Variable body,secret -ErrorAction SilentlyContinue
    }
    $csrf = Invoke-RestMethod "$lanyanApi/api/v1/auth/csrf" -WebSession $session
    $headers[$csrf.headerName] = $csrf.token
    [PSCustomObject]@{Session=$session;Headers=$headers}
}
# Inicia sesion con el estudiante matriculado.
$lanyanStudent = Connect-LanyanLocal
Invoke-RestMethod "$lanyanApi/api/v1/auth/me" -WebSession $lanyanStudent.Session
$lanyanSchedule = Invoke-RestMethod "$lanyanApi/api/v1/students/me/schedule?page=0&size=20" -WebSession $lanyanStudent.Session
$lanyanMaterials = Invoke-RestMethod "$lanyanApi/api/v1/students/me/materials?page=0&size=20" -WebSession $lanyanStudent.Session
$lanyanSchedule.content | Format-List
$lanyanMaterials.content | Format-List
```

Si están vacíos, comprueba el perfil y matrícula ENROLLED con [COURSES.md](COURSES.md),
los horarios registrados y que el material esté publicado. No se precargan
contenidos ficticios. Los GET no requieren CSRF.

## PowerShell: CRUD administrativo con datos autorizados

Inicia otra sesión como ADMIN. Estos ejemplos escriben los datos que introduces;
utiliza secciones e información académica válidas, sin añadir muestras permanentes.

```powershell
$lanyanAdmin = Connect-LanyanLocal
$lanyanSectionId = Read-Host 'UUID de la seccion existente'
$lanyanScheduleBody = @{
    dayOfWeek=(Read-Host 'Dia: MONDAY, TUESDAY, etc.')
    startTime=(Read-Host 'Inicio HH:mm')
    endTime=(Read-Host 'Fin HH:mm')
    modality=(Read-Host 'IN_PERSON, ONLINE o HYBRID')
    classroom=(Read-Host 'Aula; vacio solo si ONLINE')
} | ConvertTo-Json
$lanyanSchedule = Invoke-RestMethod "$lanyanApi/api/v1/academic/sections/$lanyanSectionId/schedules" -Method Post -WebSession $lanyanAdmin.Session -Headers $lanyanAdmin.Headers -ContentType 'application/json; charset=utf-8' -Body $lanyanScheduleBody

$lanyanMaterialBody = @{
    title=(Read-Host 'Titulo')
    description=(Read-Host 'Descripcion opcional')
    type=(Read-Host 'DOCUMENT, GUIDE o LINK')
    referenceType=(Read-Host 'LOCAL_REFERENCE o HTTPS_URL')
    reference=(Read-Host 'Referencia relativa o URL HTTPS sin secretos')
    status='DRAFT'
} | ConvertTo-Json
$lanyanMaterial = Invoke-RestMethod "$lanyanApi/api/v1/academic/sections/$lanyanSectionId/materials" -Method Post -WebSession $lanyanAdmin.Session -Headers $lanyanAdmin.Headers -ContentType 'application/json; charset=utf-8' -Body $lanyanMaterialBody
Invoke-RestMethod "$lanyanApi/api/v1/academic/sections/$lanyanSectionId/schedules" -WebSession $lanyanAdmin.Session
Invoke-RestMethod "$lanyanApi/api/v1/academic/sections/$lanyanSectionId/materials" -WebSession $lanyanAdmin.Session
```

Para publicar el material después de revisar su referencia, consulta su versión
actual y realiza PUT:

```powershell
$lanyanMaterialId = $lanyanMaterial.id
$lanyanCurrent = Invoke-RestMethod "$lanyanApi/api/v1/academic/materials/$lanyanMaterialId" -WebSession $lanyanAdmin.Session
$lanyanPublishBody = @{
    title=$lanyanCurrent.title
    description=$lanyanCurrent.description
    type=$lanyanCurrent.type
    referenceType=$lanyanCurrent.referenceType
    reference=$lanyanCurrent.reference
    status='PUBLISHED'
    version=$lanyanCurrent.version
} | ConvertTo-Json
$lanyanMaterial = Invoke-RestMethod "$lanyanApi/api/v1/academic/materials/$lanyanMaterialId" -Method Put -WebSession $lanyanAdmin.Session -Headers $lanyanAdmin.Headers -ContentType 'application/json; charset=utf-8' -Body $lanyanPublishBody
```

PUT de horario usa `dayOfWeek`, `startTime`, `endTime`, `modality`, `classroom`
y la `version` actual. Para retirar voluntariamente un registro, consulta su
versión y ejecuta DELETE; esta operación oculta el contenido a sus estudiantes:

```powershell
# Cambia a schedules si deseas retirar un horario.
$lanyanResource = 'materials'
$lanyanId = Read-Host 'UUID del registro que deseas retirar'
$lanyanCurrent = Invoke-RestMethod "$lanyanApi/api/v1/academic/$lanyanResource/$lanyanId" -WebSession $lanyanAdmin.Session
Invoke-RestMethod "$lanyanApi/api/v1/academic/$lanyanResource/${lanyanId}?version=$($lanyanCurrent.version)" -Method Delete -WebSession $lanyanAdmin.Session -Headers $lanyanAdmin.Headers
```

Windows PowerShell lanza excepciones ante 4xx; revisa el estado HTTP. Tras caducar
la sesión, inicia otra vez; no reutilices CSRF de una sesión distinta.

## Postman local

1. Utiliza la aplicación local y Cookie Jar; no sincronices credenciales ni uses
   agentes cloud. Base `http://localhost:8080`.
2. GET `/api/v1/auth/csrf`, guarda cookie y `token`/`headerName`.
3. POST `/api/v1/auth/login` con correo/contraseña de ADMIN y encabezado CSRF.
   Renueva token con otro GET csrf después del login.
4. GET `/api/v1/academic/courses` y `/api/v1/academic/courses/{id}/sections`
   para obtener una sección existente. POST a sus schedules/materials con JSON
   del contrato. Envía CSRF en POST, PUT y DELETE.
5. Guarda `id` y `version`. GET individual, PUT con todos los campos editables
   y versión actual; DELETE con `?version=N`. Omitir CSRF debe producir 403,
   versión antigua 409; no hagas estas pruebas sobre datos ajenos.
6. POST logout con CSRF, limpia cookies e inicia sesión STUDENT con su propio
   CSRF. GET `/api/v1/students/me/schedule` y `/api/v1/students/me/materials`.
   No envíes IDs de estudiantes. Comprueba que los borradores no aparecen.
7. Las rutas administrativas responden 403 al estudiante. Sin sesión, las nuevas
   consultas privadas responden 401. Evita compartir Cookie Jar entre perfiles.

## Consultar PostgreSQL sin alterar datos

Desde la raíz:

```powershell
docker compose exec postgres psql -U lanyan_app -d lanyan_ti
```

Dentro de psql:

```sql
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
SELECT id, section_id, day_of_week, start_time, end_time, modality, classroom, version
FROM academic_class_schedules WHERE deleted_at IS NULL ORDER BY section_id, day_of_week, start_time;
SELECT id, section_id, title, type, reference_type, status, version
FROM academic_course_materials WHERE deleted_at IS NULL ORDER BY created_at DESC;
\q
```

En Docker Desktop, Containers → lanyan-ti → postgres muestra el servicio existente,
estado saludable y logs. El volumen persistente permanece asociado al proyecto.

## Archivos y pruebas

Nuevos: entidades `ClassSchedule`/`CourseMaterial`, cuatro enums y
`MaterialReferences`; repositorios de ambas entidades; servicios
`ClassScheduleService`/`CourseMaterialService`, mapeador `AcademicContentViews`
y control de versión `AcademicContentVersions`; DTO `AcademicContentDtos`,
controladores `AcademicContentAdminController`/`StudentAcademicContentController`;
V5, `AcademicContentRulesTest`, `AcademicContentSecurityTest` y esta guía.

Ajustados: `CourseSectionRepository` (bloqueo de sección), `SecurityConfiguration`
(dos GET de estudiante), `AcademicExceptionHandler`, `BackendPreparationTest`,
descripción del paquete académico y documentación. Se conservan las 99 pruebas
previas; únicamente la prueba de infraestructura actualiza V5 y las doce tablas.

Las nuevas pruebas comprueban validaciones, normalización, propiedad de datos,
CRUD, borradores/publicación, bajas lógicas, versiones, permisos, CSRF,
revocación/desactivación, estados de matrículas, restricciones SQL y concurrencia.
Se ejecutan con PostgreSQL temporal de Testcontainers, HTTP real y datos ficticios
que nunca se copian a desarrollo. Los resultados observados se registran al final
de esta guía; reportes en `target/surefire-reports/`.

## Resultado observado de esta entrega

- `mvnw.cmd --offline --batch-mode --no-transfer-progress verify`: BUILD SUCCESS,
  **173 pruebas aprobadas**, cero fallos, errores u omisiones. Se conservan las
  99 anteriores; se añaden 47 unitarias y 27 de integración.
- Dos POST concurrentes conflictivos producen 201/409 y una sola fila activa;
  dos PUT con la misma versión producen 200/409, sin sobrescribir silenciosamente.
- La primera ejecución completa tuvo cuatro errores de conexión/timeout en
  pruebas previas de matrículas. Hikari registró pausas del entorno de varios
  minutos. La repetición aislada aprobó las 24 pruebas de matrículas y la
  ejecución completa final aprobó las 173, sin aumentar timeouts ni eliminar pruebas.
- Desarrollo: V1–V5 aplicadas correctamente, doce tablas; Hibernate validó el
  esquema al arrancar. Se compararon huellas antes/después de usuarios, perfiles,
  cursos, secciones, matrículas, roles, asignaciones y permisos previos: sin cambios.
  También se conservó la huella del historial V1–V4.
- `http://localhost:8080/actuator/health`: HTTP 200, UP. Los dos GET nuevos del
  estudiante sin sesión devolvieron 401. Las respuestas positivas y el aislamiento
  entre estudiantes se verificaron en Testcontainers, sin escribir muestras en
  desarrollo.
- Las tablas nuevas de desarrollo contienen cero horarios y cero materiales.
  Para ver contenido inicia Java, registra datos autorizados mediante ADMIN y
  consulta desde la cuenta STUDENT con su matrícula ENROLLED.
- PostgreSQL de Compose sigue saludable en 15432 con su volumen existente. Solo
  se detuvo el Java creado para verificar; debes iniciarlo para usar Postman.
- Se verificó por hashes que el frontend y V1–V4 no cambiaron. No se eliminaron
  archivos. `.env`, `backend/.env`, logs y artefactos están ignorados por Git.
  No se hicieron commits ni publicaciones.
- Los siete bloques PowerShell de esta guía pasan el análisis de sintaxis.

Registros de compilación/pruebas (6 de noviembre de 2026):
`target/phase23-compile.log`, `target/phase23-verify.log` (primer intento),
`target/phase23-enrollment-recheck.log` y `target/phase23-verify-final.log`.
Arranque local y V5 (7 de noviembre de 2026): `target/phase23-development.log`
y `target/phase23-development-error.log`. No publiques registros con datos privados.

Inventario de cambios de esta fase: **20 archivos nuevos y 9 modificados**;
todos bajo `backend/`. Los artefactos de `target/` no se incluyen en ese inventario.
La Fase 2.3 termina aquí; calificaciones, asistencia y progreso siguen pendientes.
