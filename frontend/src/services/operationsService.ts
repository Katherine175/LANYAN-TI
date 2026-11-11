import { api } from './api'

export type Severity = 'INFO' | 'WARNING' | 'EXCEPTION'
export type EventStatus = 'OPEN' | 'ACKNOWLEDGED' | 'CLOSED'
export type Impact = 'LOW' | 'MEDIUM' | 'HIGH'

export interface OperationalEvent {
  id: string
  code: string
  serviceId: string | null
  serviceName: string | null
  severity: Severity
  source: 'AUTOMATIC' | 'MANUAL'
  title: string
  detail: string | null
  status: EventStatus
  ticketId: string | null
  ticketCode: string | null
  createdBy: string | null
  createdAt: string
  acknowledgedBy: string | null
  acknowledgedAt: string | null
  closedAt: string | null
}

export interface Availability {
  serviceId: string
  serviceName: string
  currentStatus: 'OPERATIONAL' | 'DEGRADED' | 'UNAVAILABLE'
  target: number
  availability: number
  meetsTarget: boolean
  outages: number
  unavailableMinutes: number
  degradedMinutes: number
  mtrsMinutes: number | null
}

export interface Maintenance {
  id: string
  serviceId: string
  serviceName: string
  title: string
  startsAt: string
  endsAt: string
  impact: Impact
  cancelled: boolean
  createdBy: string | null
}

type Tone = 'lavender' | 'blue' | 'mint' | 'pink' | 'neutral' | 'danger'

export const severityLabels: Record<Severity, { text: string; tone: Tone }> = {
  INFO: { text: 'Informativo', tone: 'blue' },
  WARNING: { text: 'Advertencia', tone: 'pink' },
  EXCEPTION: { text: 'Excepción', tone: 'danger' },
}
export const eventStatusLabels: Record<EventStatus, { text: string; tone: Tone }> = {
  OPEN: { text: 'Abierto', tone: 'blue' },
  ACKNOWLEDGED: { text: 'Revisado', tone: 'lavender' },
  CLOSED: { text: 'Cerrado', tone: 'neutral' },
}
export const impactLabels: Record<Impact, string> = { LOW: 'Bajo', MEDIUM: 'Medio', HIGH: 'Alto' }

export const listEvents = () => api<OperationalEvent[]>('/operations/events')
export const registerEvent = (body: { serviceId?: string; severity: Severity; title: string; detail?: string }) =>
  api<OperationalEvent>('/operations/events', { method: 'POST', body })
export const acknowledgeEvent = (id: string) => api<OperationalEvent>(`/operations/events/${id}/acknowledge`, { method: 'POST' })
export const closeEvent = (id: string) => api<OperationalEvent>(`/operations/events/${id}/close`, { method: 'POST' })
export const eventToIncident = (id: string) => api<OperationalEvent>(`/operations/events/${id}/incident`, { method: 'POST' })
export const listAvailability = () => api<Availability[]>('/operations/availability')
export const listMaintenance = () => api<Maintenance[]>('/operations/maintenance')
export const scheduleMaintenance = (body: { serviceId: string; title: string; startsAt: string; endsAt: string; impact: Impact }) =>
  api<Maintenance>('/operations/maintenance', { method: 'POST', body })
export const cancelMaintenance = (id: string) => api<void>(`/operations/maintenance/${id}/cancel`, { method: 'POST' })
