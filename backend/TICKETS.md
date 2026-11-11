# Tickets y niveles de servicio (V12)

Gestión de incidentes del sílabo (Operación del servicio) e ISO/IEC 20000-1,
cláusulas 8.3.3 (gestión del nivel de servicio) y 8.6.1 (gestión de incidentes).

## Decisiones del Instituto (2026-11-11)

- **El solicitante no elige la prioridad.** La fija el servicio del catálogo.
  Solo un técnico o la administración la cambia, con un motivo que queda en auditoría.
- **Prioridad base por servicio** (editable en Administración › Servicios):

  | Prioridad | Servicios |
  | --- | --- |
  | Alta | Plataforma virtual, Internet y Wi-Fi |
  | Media | Matrícula, Pagos, Correo institucional, Proyectores |
  | Baja | Soporte técnico (un equipo individual) |
  | Crítica | Nunca automática: la asigna un técnico cuando el servicio cae para todos |

- **Tiempos (SLA)**, los del informe del proyecto (editables en Configuración SLA):

  | Prioridad | Primera respuesta | Resolución |
  | --- | --- | --- |
  | Crítica | 10 min | 30 min |
  | Alta | 15 min | 1 h |
  | Media | 30 min | 4 h |
  | Baja | 1 h | 8 h |

- **El reloj solo corre en horario de atención:** lunes a sábado de 7:00 a 21:00,
  hora de Lima. Un ticket Bajo registrado a las 20:00 vence al día siguiente a las 14:00
  (1 hora el mismo día y 7 horas al día siguiente). No se consideran feriados. El horario
  se ajusta con `lanyan.sla.opens`, `lanyan.sla.closes` y `lanyan.sla.days`.

## Funcionamiento

Al registrar un ticket se calculan y guardan dos fechas límite: primera respuesta y
resolución. La primera acción de un técnico (tomarlo o cambiar su estado) cuenta como
primera respuesta. Resolver registra la hora de resolución; reabrir la borra.
Cambiar la prioridad recalcula ambas fechas desde la creación del ticket.
Un cambio en Configuración SLA rige para los tickets nuevos; los registrados conservan
sus plazos. El estado "En espera" no detiene el reloj.

Cada respuesta indica el estado de cada plazo: `ON_TIME`, `AT_RISK` (75 % consumido),
`BREACHED` (vencido sin atender), `MET` o `MISSED`, y el porcentaje usado del plazo de resolución.

## Endpoints

| Método y ruta | Acceso |
| --- | --- |
| `POST /api/v1/tickets` con `serviceId`, `title`, `description` | Cualquier rol autenticado (`tickets:create`) |
| `GET /api/v1/tickets/mine` | Los tickets propios |
| `GET /api/v1/tickets/{id}` | El solicitante o TI; un ticket ajeno responde `404` |
| `GET /api/v1/tickets` | TECHNICIAN y ADMIN (`tickets:manage`) |
| `POST /api/v1/tickets/{id}/take` | TECHNICIAN y ADMIN |
| `PUT /api/v1/tickets/{id}/status` con `status` | TECHNICIAN y ADMIN; no vuelve a `NEW` |
| `PUT /api/v1/tickets/{id}/priority` con `priority` y `reason` (5 a 300 caracteres) | TECHNICIAN y ADMIN |
| `GET /api/v1/sla/policies` | Cualquier rol autenticado |
| `PUT /api/v1/sla/policies/{priority}` con minutos y `version` | ADMIN (`sla:manage`) |

Auditoría: `TICKET_CREATED`, `TICKET_ASSIGNED`, `TICKET_STATUS_CHANGED`,
`TICKET_PRIORITY_CHANGED` (con el motivo) y `SLA_POLICY_UPDATED`.

## Pendiente

Comentarios en el ticket, adjuntos y avisos por correo todavía no están implementados.
Accesos y Solicitudes de servicio siguen siendo una vista de demostración sin guardar datos.
