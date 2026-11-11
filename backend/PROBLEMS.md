# Gestión de problemas y seguimiento de tickets (V14)

Operación del servicio del sílabo: **Gestión de problemas** (semana 13) y el seguimiento
de la **Gestión de incidentes** (semana 11). ISO/IEC 20000-1, cláusulas 8.6.1 y 8.6.3.

## Incidente frente a problema

Un **incidente** (ticket) se atiende para devolver el servicio cuanto antes. Un **problema**
busca la causa de uno o varios incidentes para que no se repitan.

## Flujo del problema

1. **Investigando:** se registra con su servicio, prioridad, síntoma y los incidentes vinculados.
   Un incidente pertenece a un solo problema y debe ser del mismo servicio.
2. **Error conocido:** con la causa raíz y la solución temporal documentadas. Desde ese
   momento, el técnico ve la solución temporal al abrir cualquier ticket abierto de ese servicio.
3. **Resuelto:** con la solución definitiva. Se puede resolver de una vez los incidentes
   vinculados abiertos; cada uno recibe un comentario que le explica al solicitante qué se hizo.

## Gestión proactiva

La página Problemas muestra "Incidentes repetidos sin analizar": servicios con **3 o más
incidentes en los últimos 30 días** que aún no están vinculados a un problema. Desde ahí se
abre el problema con esos incidentes ya marcados. (Umbral asumido; se ajusta en
`ProblemService.CANDIDATE_THRESHOLD`.)

## Seguimiento de tickets

- El solicitante y TI comentan en el ticket. TI puede escribir **notas internas** que el
  solicitante no ve.
- Un comentario de TI visible para el solicitante cuenta como **primera respuesta** del SLA;
  una nota interna no.
- El detalle del ticket muestra el problema asociado, si lo hay.

## Endpoints

| Método y ruta | Acceso |
| --- | --- |
| `GET /api/v1/problems`, `GET /api/v1/problems/{id}` | TECHNICIAN y ADMIN (`problems:manage`) |
| `POST /api/v1/problems` con `title`, `description`, `serviceId`, `priority`, `ticketIds` | TECHNICIAN y ADMIN |
| `POST /api/v1/problems/{id}/incidents` y `DELETE /api/v1/problems/{id}/incidents/{ticketId}` | TECHNICIAN y ADMIN |
| `PUT /api/v1/problems/{id}/known-error` con `rootCause` y `workaround` | TECHNICIAN y ADMIN |
| `POST /api/v1/problems/{id}/resolve` con `permanentSolution` y `resolveLinkedTickets` | TECHNICIAN y ADMIN |
| `GET /api/v1/problems/candidates`, `GET /api/v1/problems/known-errors?serviceId=` | TECHNICIAN y ADMIN |
| `GET` y `POST /api/v1/tickets/{id}/comments` con `body` e `internal` | Solicitante (sin notas internas) y TI |

Auditoría: `PROBLEM_CREATED`, `PROBLEM_LINKED`, `PROBLEM_UNLINKED`, `PROBLEM_KNOWN_ERROR`,
`PROBLEM_RESOLVED` y `TICKET_COMMENTED` (sin copiar el texto del comentario).
