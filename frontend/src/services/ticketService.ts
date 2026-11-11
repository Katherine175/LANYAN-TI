import { api } from './api'
import type { Page } from './identityService'

export type Priority = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'
export type TicketStatus = 'NEW' | 'IN_PROGRESS' | 'ON_HOLD' | 'RESOLVED'
export type SlaState = 'ON_TIME' | 'AT_RISK' | 'BREACHED' | 'MET' | 'MISSED'

export interface Ticket {
  id: string
  code: string
  title: string
  description: string | null
  service: { id: string; code: string; name: string }
  requesterName: string
  requesterEmail: string
  assigneeName: string | null
  priority: Priority
  status: TicketStatus
  createdAt: string
  firstResponseDue: string
  resolutionDue: string
  firstRespondedAt: string | null
  resolvedAt: string | null
  firstResponseSla: SlaState
  resolutionSla: SlaState
  resolutionElapsedPercent: number
  version: number
}

export interface SlaPolicy {
  priority: Priority
  firstResponseMinutes: number
  resolutionMinutes: number
  version: number
}

type Tone = 'lavender' | 'blue' | 'mint' | 'pink' | 'neutral' | 'danger'

export const priorityLabels: Record<Priority, { text: string; tone: Tone }> = {
  CRITICAL: { text: 'Crítica', tone: 'danger' },
  HIGH: { text: 'Alta', tone: 'pink' },
  MEDIUM: { text: 'Media', tone: 'blue' },
  LOW: { text: 'Baja', tone: 'neutral' },
}

export const statusLabels: Record<TicketStatus, { text: string; tone: Tone }> = {
  NEW: { text: 'Nuevo', tone: 'blue' },
  IN_PROGRESS: { text: 'En progreso', tone: 'lavender' },
  ON_HOLD: { text: 'En espera', tone: 'pink' },
  RESOLVED: { text: 'Resuelto', tone: 'mint' },
}

export const slaLabels: Record<SlaState, { text: string; tone: Tone }> = {
  ON_TIME: { text: 'A tiempo', tone: 'mint' },
  AT_RISK: { text: 'Por vencer', tone: 'pink' },
  BREACHED: { text: 'Vencido', tone: 'danger' },
  MET: { text: 'Cumplido', tone: 'mint' },
  MISSED: { text: 'Fuera de plazo', tone: 'danger' },
}

export const statusOrder: TicketStatus[] = ['NEW', 'IN_PROGRESS', 'ON_HOLD', 'RESOLVED']

const dateTime = new Intl.DateTimeFormat('es-PE', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', timeZone: 'America/Lima' })
export const formatDateTime = (iso: string) => dateTime.format(new Date(iso))

/** "hace 5 min", "hace 2 h", o la fecha si pasó más de un día. */
export function ago(iso: string, now = Date.now()) {
  const minutes = Math.round((now - new Date(iso).getTime()) / 60000)
  if (minutes < 1) return 'Ahora'
  if (minutes < 60) return `Hace ${minutes} min`
  if (minutes < 24 * 60) return `Hace ${Math.round(minutes / 60)} h`
  return formatDateTime(iso)
}

export const SLA_HOURS_TEXT = 'Horario de atención: lunes a sábado de 7:00 a 21:00'

export const createTicket = (body: { serviceId: string; title: string; description?: string }) => api<Ticket>('/tickets', { method: 'POST', body })
export const myTickets = () => api<Page<Ticket>>('/tickets/mine?size=100')
export const allTickets = () => api<Page<Ticket>>('/tickets?size=100')
export const takeTicket = (id: string) => api<Ticket>(`/tickets/${id}/take`, { method: 'POST' })
export const changeTicketStatus = (id: string, status: TicketStatus) => api<Ticket>(`/tickets/${id}/status`, { method: 'PUT', body: { status } })
export const changeTicketPriority = (id: string, priority: Priority, reason: string) =>
  api<Ticket>(`/tickets/${id}/priority`, { method: 'PUT', body: { priority, reason } })

export const listSlaPolicies = () => api<SlaPolicy[]>('/sla/policies')
export const updateSlaPolicy = (priority: Priority, body: { firstResponseMinutes: number; resolutionMinutes: number; version: number }) =>
  api<SlaPolicy>(`/sla/policies/${priority}`, { method: 'PUT', body })
