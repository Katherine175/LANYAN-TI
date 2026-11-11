# Solicitudes de servicio y gestión de accesos (V13)

Operación del servicio del sílabo: **Cumplimiento de solicitudes** (semana 14) y
**Gestión de accesos** (semana 13). ISO/IEC 20000-1, cláusulas 8.6.2 y 8.7.3.

## Diferencia con los tickets

| Pantalla | Para qué | Ejemplo |
| --- | --- | --- |
| Tickets | Algo dejó de funcionar (incidente) | "No puedo entrar al aula virtual" |
| Solicitudes | Pedido nuevo y estándar de un servicio del catálogo | "Instalar un software en el laboratorio 3" |
| Accesos | Permiso sobre un sistema, que se concede y se revoca | "Necesito editar el aula virtual del curso B1" |

## Solicitudes de servicio

Flujo: **Pendiente → Aprobada o Rechazada → En atención → Atendida**. El solicitante
puede cancelarla mientras esté pendiente.

- Aprueba o rechaza la Jefatura de TI (rol Administrador); rechazar exige motivo.
- La toma y la atiende el equipo técnico; al cerrarla registra qué se entregó.
- La fecha requerida es opcional; si pasa y la solicitud sigue abierta, se marca vencida.

## Gestión de accesos

Flujo: **Pendiente → Vigente o Rechazado → Revocado**. El solicitante cancela mientras
esté pendiente. Un acceso aprobado con fecha de vencimiento deja de estar vigente
solo al pasar esa fecha.

- Concede, rechaza y revoca la Jefatura de TI (rol Administrador); el equipo técnico consulta.
- **Verificación de identidad:** no se concede acceso a una cuenta desactivada o eliminada.
- No se admite una segunda solicitud para el mismo recurso mientras haya otra pendiente
  o un acceso vigente.
- Revocar exige motivo, que queda en la auditoría.
- Recursos iniciales: Aula virtual, Sistema de matrícula, Reportes académicos,
  Sistema de pagos y Carpeta compartida de docentes. Niveles: solo lectura, edición
  y administración.

Valor asumido (avisado a Katherine): aprueba siempre el rol Administrador como Jefatura de TI.
Los accesos son un registro de la decisión; LANYAN TI no crea las cuentas en esos sistemas.

## Endpoints

| Método y ruta | Acceso |
| --- | --- |
| `POST /api/v1/service-requests` | `requests:create` (todos los roles) |
| `GET /api/v1/service-requests/mine` | Las propias |
| `GET /api/v1/service-requests` | TECHNICIAN y ADMIN |
| `POST /api/v1/service-requests/{id}/cancel` | El solicitante, si está pendiente |
| `POST /api/v1/service-requests/{id}/decision` con `approved` y `note` | ADMIN (`requests:approve`) |
| `POST /api/v1/service-requests/{id}/take` y `/fulfill` con `note` | TECHNICIAN y ADMIN (`requests:fulfill`) |
| `GET /api/v1/access/resources` | `access:request` (todos los roles) |
| `POST /api/v1/access/requests` con `resourceId`, `level`, `justification`, `expiresOn` | `access:request` |
| `GET /api/v1/access/requests/mine` y `GET /api/v1/access/requests` | Propias / TECHNICIAN y ADMIN |
| `POST /api/v1/access/requests/{id}/cancel`, `/decision`, `/revoke` con `reason` | Solicitante / ADMIN (`access:approve`) |

Auditoría: `SERVICE_REQUEST_*` (creada, aprobada, rechazada, cancelada, asignada, atendida)
y `ACCESS_*` (solicitado, concedido, rechazado, revocado, cancelado).
