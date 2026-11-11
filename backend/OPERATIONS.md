# Operaciones TI: eventos, disponibilidad y mantenimientos

Cubre del sílabo la gestión de eventos (semana 11) y la gestión de la disponibilidad (semana 7).
En ISO/IEC 20000-1 corresponde a la cláusula 8.7.1 (disponibilidad del servicio) y, como entrada
de incidentes, a la 8.6.1.

Solo técnicos y administradores (`/api/v1/operations/**`, permiso `operations:manage`).

## Eventos

Un evento es cualquier cambio de estado que importa para un servicio. Hay dos orígenes:

| Origen | Cuándo se crea |
| --- | --- |
| Automático | Cada vez que alguien cambia el estado operativo de un servicio en el Catálogo TI. |
| Manual | Un técnico registra algo que vio (por ejemplo, "disco del servidor al 85 %"). |

Tipos, según ITIL:

| Tipo | Significado | Ejemplo automático |
| --- | --- | --- |
| Informativo | Algo normal que conviene dejar registrado | "Plataforma virtual restablecido" (nace cerrado) |
| Advertencia | Algo puede fallar pronto | "Correo institucional funciona con fallas" |
| Excepción | Algo ya falló | "Internet y Wi-Fi no disponible" |

Estados: **Abierto → Revisado → Cerrado**. Cuando un servicio vuelve a estar operativo, sus eventos
automáticos abiertos se cierran solos.

Una advertencia o excepción ligada a un servicio se puede **convertir en incidente**: se crea un
ticket INC con la prioridad de ese servicio y el código del evento en el título. Un evento solo
puede generar un incidente.

## Disponibilidad

Se mide sobre los últimos 30 días, las 24 horas:

```
disponibilidad = (minutos de la ventana - minutos "no disponible") / minutos de la ventana × 100
```

- El tiempo "con fallas" (degradado) se informa aparte, pero cuenta como disponible.
- Un servicio creado hace menos de 30 días se mide desde su creación.
- Una caída que empezó antes de la ventana solo cuenta la parte que cae dentro.
- **Tiempo medio de restauración (MTRS)**: promedio de duración de las caídas que ya terminaron.
- Se compara con la meta de disponibilidad que tiene cada servicio en el catálogo.

Ejemplo: 2 horas sin servicio en 30 días → 43 080 / 43 200 = 99,72 %.

## Mantenimientos

Interrupciones planificadas por servicio, con inicio, fin e impacto (bajo, medio o alto). Solo se
programan en el futuro y se pueden cancelar mientras no hayan empezado.

## Endpoints

| Método | Ruta | Uso |
| --- | --- | --- |
| GET | `/api/v1/operations/events` | Últimos 100 eventos |
| POST | `/api/v1/operations/events` | Registrar evento manual |
| POST | `/api/v1/operations/events/{id}/acknowledge` | Marcar como revisado |
| POST | `/api/v1/operations/events/{id}/close` | Cerrar |
| POST | `/api/v1/operations/events/{id}/incident` | Crear incidente desde el evento |
| GET | `/api/v1/operations/availability` | Disponibilidad por servicio |
| GET | `/api/v1/operations/maintenance` | Mantenimientos recientes y próximos |
| POST | `/api/v1/operations/maintenance` | Programar mantenimiento |
| POST | `/api/v1/operations/maintenance/{id}/cancel` | Cancelar |

Todo queda en la auditoría: EVENT_REGISTERED, EVENT_CLOSED, EVENT_INCIDENT_CREATED,
MAINTENANCE_SCHEDULED y MAINTENANCE_CANCELLED.

Tablas (V15): `service_status_changes`, `operational_events`, `maintenance_windows`.
