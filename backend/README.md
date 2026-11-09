# LANYAN TI — backend

Backend independiente para el Instituto de Idiomas de la Universidad Nacional de Piura.
Incluye la infraestructura de Fase 0, la identidad compartida de Fase 1 y
los perfiles, cursos, secciones, matrículas, horarios y referencias de materiales
de las Fases 2.1 a 2.3.
Los demás módulos permanecen reservados mediante `package-info.java`.
El frontend existente se mantiene independiente y sin modificaciones.

Consulta [AUTHENTICATION.md](AUTHENTICATION.md) para los endpoints, el primer
administrador y las pruebas manuales con PowerShell o Postman.
Consulta [ACADEMIC.md](ACADEMIC.md) para el perfil estudiantil, el alta local de
datos ficticios y la planificación de las siguientes fases académicas.
Consulta [COURSES.md](COURSES.md) para registrar cursos/secciones/matrículas
desde administración y consultar los cursos propios del estudiante.
Consulta [SCHEDULES_MATERIALS.md](SCHEDULES_MATERIALS.md) para horarios semanales,
materiales por sección, CRUD, versiones y consultas privadas del estudiante.

El entorno de esta entrega es exclusivamente local: API en `localhost:8080`,
PostgreSQL de Docker en `localhost:15432` y frontend en Vite. No se configura
hosting, cloud, CI/CD, publicación ni conexiones a bases de datos remotas.

## Tecnologías y requisitos

- JDK 21; `java` debe estar en `PATH`. Si defines `JAVA_HOME`, debe apuntar al JDK 21.
- Spring Boot 4.1.2 y Maven mediante los scripts oficiales de Maven Wrapper.
- PostgreSQL 17 mediante Docker Compose para desarrollo y Testcontainers para pruebas.
- Docker Desktop en modo de contenedores Linux, con Docker Compose disponible.
- Acceso a Maven Central y Docker Hub durante la primera descarga.

No es necesario instalar Maven globalmente. El Wrapper descarga la versión fijada
en `.mvn/wrapper/maven-wrapper.properties` al primer uso.

Dependencias: Spring Web MVC, Data JPA, Security, Validation, PostgreSQL Driver,
Flyway con soporte PostgreSQL, DevTools y Actuator. Las versiones de las
dependencias las administra Spring Boot. DevTools es opcional y no se incluye
en el JAR ejecutable de producción.

## Estructura modular

Paquete raíz: `pe.edu.unp.lanyan`.

| Paquete | Responsabilidad futura |
| --- | --- |
| `configuration` | Configuración transversal; contiene la política de seguridad inicial. |
| `identity` | Identidad, usuarios, roles y permisos; implementado en Fase 1. |
| `academic` | Perfil, cursos, secciones, matrículas, horarios y referencias de materiales con acceso propio (2.1–2.3). |
| `catalog` | Catálogo y categorías de servicios. |
| `tickets` | Tickets, Service Desk y seguimiento de atención/SLA. |
| `requests` | Solicitudes de servicio y aprobaciones. |
| `access` | Solicitudes y concesiones de acceso a recursos. |
| `problems` | Problemas y análisis de causas raíz. |
| `operations` | Operaciones, disponibilidad y mantenimientos. |
| `knowledge` | Artículos y valoraciones. |
| `reporting` | Reportes e indicadores. |
| `improvement` | Iniciativas y acciones de mejora continua. |

`identity` y `academic` separan `api`, `application`, `domain` e `infrastructure`. Los módulos
restantes podrán adoptar la misma estructura cuando se autorice su implementación.
No existen todavía notas, asistencia, progreso ni operaciones de tickets.
Los materiales actuales son referencias: no se suben ni se descargan archivos.

## Configuración desde Windows PowerShell

Todos los comandos de Docker Compose de esta guía se ejecutan desde la raíz
`LANYAN-TI`, donde está `compose.yaml`. No necesitas PostgreSQL ni `psql`
instalados directamente en Windows.

```powershell
java -version
docker version
docker compose version
.\backend\mvnw.cmd --version
```

Variables reconocidas:

| Variable | Obligatoria | Valor de ejemplo |
| --- | --- | --- |
| `DB_URL` | Sí | `jdbc:postgresql://localhost:5432/lanyan_ti` |
| `DB_USERNAME` | Sí | `lanyan_app` |
| `DB_PASSWORD` | Sí | Contraseña local del usuario de PostgreSQL. |
| `SERVER_PORT` | No | `8080` por defecto. |
| `SERVER_ADDRESS` | No | `127.0.0.1` por defecto: solo esta computadora. |

Hay dos referencias con propósitos diferentes:

- `../.env.example`: configuración de Compose. Copiar como `.env` en la raíz.
- `.env.example` de este directorio: nombres `DB_*` que reconoce Spring Boot.

Docker Compose lee automáticamente el `.env` de la raíz. Spring Boot **no carga
automáticamente** ninguno de estos archivos. Más abajo se transfieren las
credenciales resueltas por Compose al proceso Java, sin duplicarlas en otro archivo.
Las variables de PowerShell tienen precedencia sobre `.env`; evita valores `DB_*`
de otros proyectos en esa terminal. No publiques la salida completa de
`docker compose config`: puede contener la contraseña. Usa `--quiet` para validar.

### PostgreSQL persistente con Docker Compose

Comprueba primero los puertos y los contenedores existentes:

```powershell
docker ps -a
Get-NetTCPConnection -State Listen -LocalPort 5432,8080 -ErrorAction SilentlyContinue |
    Select-Object LocalAddress,LocalPort,OwningProcess
```

El puerto predeterminado es `127.0.0.1:5432`. Si está ocupado, configura
`POSTGRES_PORT=15432` en `.env` y comprueba también ese puerto. No detengas servicios
ajenos para liberar el puerto. Durante la configuración inicial de este repositorio
se detectó PostgreSQL de Windows en el 5432, por lo que la instancia local puede
usar el 15432; el `.env` local define el puerto efectivo.

Prepara el archivo local sin sobrescribir uno existente:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
code .env
```

Completa `DB_PASSWORD` con una contraseña local y conserva `POSTGRES_DB=lanyan_ti`
y `DB_USERNAME=lanyan_app`. El ejemplo deja la contraseña vacía; Compose rechaza el
arranque si no la completas. El `.env` real está excluido de Git y debe conservarse
localmente. Si ya fue generado durante la configuración, reutilízalo.

La imagen inicializa la base y el usuario solo cuando el volumen está vacío.
Cambiar el archivo `.env` después no cambia las credenciales de una base existente;
no borres el volumen para resolver una contraseña. `POSTGRES_USER` es el rol de
administración inicial de la imagen; esta configuración es exclusivamente local.

Inicia PostgreSQL y espera hasta que esté saludable:

```powershell
docker compose config --quiet
docker compose up -d
docker compose up -d --wait --wait-timeout 90
docker compose ps
docker ps
```

El proyecto aparece como `lanyan-ti`, con servicio `postgres` y contenedor
`lanyan-ti-postgres-1`. Solo se publica en la interfaz local. Los datos se guardan en
el volumen `lanyan-ti_postgres_data`, montado en `/var/lib/postgresql/data`.
La política `unless-stopped` reinicia el servicio tras fallos o reinicios del motor,
respetando una detención manual. El healthcheck usa `pg_isready` cada 5 segundos.

Operaciones habituales desde la raíz:

```powershell
# Ver registros; Ctrl+C termina el seguimiento, no el contenedor.
docker compose logs --tail 100 postgres
docker compose logs -f postgres

# Reiniciar y esperar disponibilidad.
docker compose restart postgres
docker compose up -d --wait --wait-timeout 90

# Detener y retirar contenedor/red; conserva el volumen y sus datos.
docker compose down

# Iniciar nuevamente usando los mismos datos.
docker compose up -d --wait --wait-timeout 90
```

No agregues `--volumes`/`-v` a `down`; esa opción elimina los datos. Estos comandos
afectan solo al proyecto `lanyan-ti`. No se utilizan operaciones de limpieza global.

### Consultar la base sin instalar herramientas en Windows

```powershell
docker compose exec postgres pg_isready -U lanyan_app -d lanyan_ti
docker compose exec postgres psql -U lanyan_app -d lanyan_ti
```

Dentro de `psql`:

```sql
\conninfo
\l
\dt
SELECT current_database(), current_user, version();
SELECT installed_rank, version, description, success FROM flyway_schema_history;
\q
```

La tabla `flyway_schema_history` aparece después del primer arranque del backend.
Las migraciones actuales crean tablas de identidad y académicas. Desde un cliente local usa `localhost`, el
`POSTGRES_PORT` de `.env`, base `lanyan_ti` y usuario `lanyan_app`.

### Transferir la configuración de Compose a Spring Boot

Con PostgreSQL saludable, ejecuta desde la raíz y en la misma terminal que usará Java:

```powershell
$lanyanComposeJson = docker compose config --format json
if ($LASTEXITCODE -ne 0) { throw 'Revisa la configuración de Compose.' }
$lanyanCompose = $lanyanComposeJson | ConvertFrom-Json
$lanyanDatabase = $lanyanCompose.services.postgres.environment
$lanyanDatabasePort = $lanyanCompose.services.postgres.ports[0].published
$env:DB_URL = 'jdbc:postgresql://localhost:{0}/{1}' -f $lanyanDatabasePort, $lanyanDatabase.POSTGRES_DB
$env:DB_USERNAME = $lanyanDatabase.POSTGRES_USER
$env:DB_PASSWORD = $lanyanDatabase.POSTGRES_PASSWORD
$env:SERVER_PORT = '8080'
```

Las asignaciones no imprimen la contraseña. Si usas el puerto 15432, el resultado
será `jdbc:postgresql://localhost:15432/lanyan_ti`. No necesitas modificar
`application.yml`: conserva Flyway habilitado y `spring.jpa.hibernate.ddl-auto=validate`.

### Visualizar el proyecto en Docker Desktop

Abre Docker Desktop, entra en **Containers**, busca el grupo **lanyan-ti** y
despliégalo para ver **postgres-1**. Abre el contenedor para consultar **Logs**,
**Inspect** y el estado del healthcheck. También puedes detenerlo o reiniciarlo
desde sus controles. En **Volumes** aparece **lanyan-ti_postgres_data**; consérvalo.
No uses las acciones de eliminación de volúmenes.

## Compilar y ejecutar las pruebas

Inicia Docker Desktop y comprueba que el motor de contenedores Linux responde:

```powershell
# Desde la raíz del repositorio:
Set-Location .\backend
docker info
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
```

Las pruebas arrancan una instancia temporal `postgres:17-alpine` con Testcontainers
y un servidor HTTP en un puerto aleatorio. No necesitan las variables `DB_*` ni
utilizan tu base local. El contenedor se elimina al terminar.

Se comprueba:

- Arranque de Spring Boot y respuesta HTTP de salud sin detalles internos.
- Ejecución y validación de V1 a V5 en PostgreSQL real, aislado de desarrollo.
- Tablas de identidad, perfiles, cursos, secciones y matrículas; sin tablas de tickets.
- Login, CSRF, sesiones, logout, cuentas desactivadas y permisos de los cuatro roles.
- Creación administrativa de usuarios, hashing, asignación de roles y errores.
- Rechazo de operaciones anónimas y funciones administrativas desde otros perfiles.
- Perfil propio: aislamiento entre estudiantes, permisos, datos ausentes y restricciones SQL.
- Matrículas: cupos y duplicados, solicitudes concurrentes, relaciones y consultas propias paginadas.

Con las dependencias ya disponibles localmente también puedes ejecutar
`.\mvnw.cmd --offline --batch-mode --no-transfer-progress verify`, sin consultar
repositorios Maven externos. Docker utiliza la imagen PostgreSQL local de pruebas.

Las pruebas fallan si Docker no está disponible; no se omiten silenciosamente.

Resultado de Fase 2.3: `--offline verify` aprobó 173 pruebas (99 previas y 74
nuevas), sin fallos, errores ni omisiones. El detalle y las comprobaciones de V5
en PostgreSQL local están en [SCHEDULES_MATERIALS.md](SCHEDULES_MATERIALS.md).
`ApiErrorResponsesTest` añade 6 pruebas sobre los códigos HTTP de error.
Los resultados quedan en `target/surefire-reports/` y el JAR en
`target/lanyan-ti-0.0.1-SNAPSHOT.jar`.

## Ejecutar Spring Boot desde VS Code

Abre la carpeta raíz `LANYAN-TI` en VS Code y una terminal integrada PowerShell.
Inicia Compose y transfiere las variables con los pasos anteriores. Comprueba que
8080 esté libre; si está ocupado, identifica su propietario y usa otro
`SERVER_PORT` sin detener procesos ajenos. Desde la raíz:

```powershell
Set-Location .\backend
.\mvnw.cmd spring-boot:run
```

DevTools está disponible durante el desarrollo. Detén Java con `Ctrl+C`; PostgreSQL
permanece activo. Para desarrollar basta con VS Code, JDK 21 y Maven Wrapper.
Las extensiones **Extension Pack for Java** y **Spring Boot Extension Pack** son
opcionales. Los botones Run/Debug de las extensiones pueden usar un proceso con
otro entorno; para este flujo usa la terminal integrada donde definiste `DB_*`.

Para ejecutar el JAR generado por `clean verify`:

```powershell
java -jar .\target\lanyan-ti-0.0.1-SNAPSHOT.jar
```

## Comprobar salud

En otra terminal de PowerShell, con el backend en ejecución:

```powershell
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/actuator/health' -Headers @{ Accept = 'application/json' }
```

Respuesta HTTP esperada: `200`, con cuerpo `{"status":"UP"}`. Cambia el puerto si
configuraste `SERVER_PORT`. El indicador incluye la conectividad de la base de
datos; una caída de PostgreSQL produce estado `DOWN` y HTTP `503` cuando la
comprobación concluye.

## Inicio rápido

Con Docker Desktop abierto, desde la raíz y cada comando en su propia terminal:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\crear-admin.ps1      # solo la primera vez
powershell -ExecutionPolicy Bypass -File scripts\iniciar-todo.ps1
```

`iniciar-todo.ps1` abre el backend y la pantalla en dos ventanas y espera a que
estén listos. También pueden iniciarse por separado con `iniciar-backend.ps1` e
`iniciar-frontend.ps1`.

La aplicación queda en `http://127.0.0.1:8443`. Las demás cuentas se crean desde
Administración → Usuarios.

## Auditoría

`audit_events` (V6) guarda inicios de sesión, intentos fallidos, cierres de sesión
y cambios de cuentas y roles. Triggers de PostgreSQL rechazan `UPDATE`, `DELETE` y
`TRUNCATE`, de modo que ni la aplicación puede alterar un registro. Se consulta en
Administración → Auditoría o en `GET /api/v1/audit/events` (ADMIN + `audit:read`).
Los módulos nuevos registran sus acciones con `AuditService.record`.

## Catálogo de servicios

V7 crea `catalog_categories` y `catalog_services` con el catálogo inicial del diseño.
Todos los roles del portal consultan los servicios `PUBLISHED`; ADMIN crea, edita y
retira en `/api/v1/catalog/admin/**`. ADMIN y TECHNICIAN actualizan el estado
operativo en `PUT /api/v1/catalog/services/{id}/operational-status`. Los servicios no
se borran: se retiran, y cada cambio queda en la auditoría.

## Respaldo de la base local

Desde la raíz del repositorio, con PostgreSQL activo:

```powershell
powershell -File scripts\respaldar-bd.ps1
powershell -File scripts\comprobar-respaldo.ps1 respaldos\ARCHIVO.dump
```

El primero guarda una copia en `respaldos/`, carpeta excluida de Git. El segundo
la restaura en un contenedor desechable e informa tablas, migraciones y usuarios,
sin tocar la base de desarrollo. Conviene respaldar antes de aplicar una migración
nueva. Los respaldos contienen datos personales: no los compartas.

## Errores de la API

Todas las respuestas de error usan `application/problem+json` con `status` y `code`.
El manejo común está en `shared.api.ApiExceptionHandler`; cada módulo define sus
errores de negocio extendiendo `shared.application.ApiException`, y sus listados
devuelven `shared.api.PageView`. `INTERNAL_ERROR` (500) se registra en el log y
no revela detalles al cliente.

## Migraciones y seguridad

`V1__verify_database_connection.sql` ejecuta `SELECT 1`. Flyway crea su historial y
registra V1 al iniciar. Los arranques siguientes validan el historial sin repetir
la migración aplicada. No edites una migración ya ejecutada: agrega otra versión.

V2 crea `identity_users`, `identity_roles`, `identity_permissions`,
`identity_user_roles` e `identity_role_permissions`, junto con los cuatro roles y
sus permisos. No crea usuarios ni contiene contraseñas iniciales. Las contraseñas
se procesan con BCrypt (coste 12) desde Java.

V3 añade únicamente `academic_student_profiles`, vinculada uno a uno a
`identity_users`. No crea cuentas ni perfiles ficticios y no modifica V1/V2.
`GET /api/v1/students/me` requiere STUDENT y el permiso académico, y consulta
exclusivamente el usuario de la sesión. El alta local se explica en `ACADEMIC.md`.

V4 añade `academic_courses`, `academic_course_sections` y `academic_enrollments`,
además de permisos de administración y lectura propia. No inserta información
académica de ejemplo. Las altas administrativas y `GET /api/v1/students/me/courses`
se documentan en `COURSES.md`. V1, V2 y V3 permanecen intactas.

V5 añade `academic_class_schedules` y `academic_course_materials` con claves
foráneas a las secciones, baja lógica, versión, validaciones y cuatro permisos.
No altera V1–V4 ni inserta información académica de ejemplo. Los endpoints
`GET /api/v1/students/me/schedule` y `GET /api/v1/students/me/materials` filtran
por matrícula ENROLLED de la sesión; solo se muestran materiales PUBLISHED.
El contrato, las restricciones y los ejemplos están en `SCHEDULES_MATERIALS.md`.

Hibernate usa `ddl-auto=validate`, `open-in-view=false` y fechas JDBC en UTC.
Flyway tiene deshabilitada la operación `clean`.

Son públicos `GET /actuator/health`, `GET /api/v1/auth/csrf` y la presentación de
credenciales mediante `POST /api/v1/auth/login`. Los POST, incluido login y logout,
requieren CSRF. La identidad se conserva en una sesión del servidor con cookie
`LANYAN_SESSION`, `HttpOnly`, `SameSite=Lax` y 30 minutos de inactividad.
En HTTPS configura `SESSION_COOKIE_SECURE=true`; el valor predeterminado `false`
permite el desarrollo local con HTTP. Las funciones administrativas requieren
`ROLE_ADMIN` y la autoridad específica del caso de uso. No hay registro público.
Se devuelve `401` ante ausencia de autenticación y `403` ante permisos o CSRF
insuficientes, sin redirecciones a formularios ni HTTP Basic.

No se habilita CORS: Vite reenvía `/api` a `127.0.0.1:8080`, de modo que el
navegador ve un solo origen y la cookie de sesión funciona sin abrir el backend.

## Problemas frecuentes

- **Java incorrecto:** revisa `java -version`, `JAVA_HOME` y `PATH`.
- **Docker no disponible:** inicia Docker Desktop y selecciona contenedores Linux.
- **Descarga fallida:** comprueba acceso a Maven Central y Docker Hub.
- **Credenciales o variables ausentes:** establece `DB_*` en la terminal que ejecuta Java.
- **Base o credenciales incorrectas:** revisa `.env` y los registros de Compose;
  las variables de inicialización no alteran una base existente.
- **5432 ocupado:** configura `POSTGRES_PORT=15432` y vuelve a transferir las variables.
- **Contenedor unhealthy:** consulta `docker compose logs --tail 100 postgres`;
  conserva el volumen y no ejecutes operaciones de borrado.
- **Puerto ocupado:** configura otro `$env:SERVER_PORT` antes de ejecutar.

Al terminar una sesión puedes retirar la contraseña del entorno:

```powershell
Remove-Item Env:DB_PASSWORD
Remove-Variable lanyanComposeJson,lanyanCompose,lanyanDatabase -ErrorAction SilentlyContinue
```

## Referencias

- [Imagen oficial PostgreSQL: inicialización y persistencia](https://hub.docker.com/_/postgres).
- [Variables y precedencia en Compose](https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/).
- [Healthchecks y disponibilidad de servicios](https://docs.docker.com/compose/how-tos/startup-order/).
