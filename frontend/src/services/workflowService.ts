import { api } from './api'
import type { Page } from './identityService'

type Tone = 'lavender' | 'blue' | 'mint' | 'pink' | 'neutral' | 'danger'

export type RequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'IN_PROGRESS' | 'FULFILLED' | 'CANCELLED'

export interface ServiceRequest {
  id: string
  code: string
  serviceId: string
  serviceName: string
  title: string
  justification: string
  neededBy: string | null
  overdue: boolean
  requesterName: string
  requesterEmail: string
  status: RequestStatus
  decidedByName: string | null
  decidedAt: string | null
  decisionNote: string | null
  assigneeName: string | null
  fulfilledAt: string | null
  fulfillmentNote: string | null
  createdAt: string
}

export const requestStatusLabels: Record<RequestStatus, { text: string; tone: Tone }> = {
  PENDING: { text: 'Pendiente', tone: 'blue' },
  APPROVED: { text: 'Aprobada', tone: 'lavender' },
  IN_PROGRESS: { text: 'En atención', tone: 'pink' },
  FULFILLED: { text: 'Atendida', tone: 'mint' },
  REJECTED: { text: 'Rechazada', tone: 'danger' },
  CANCELLED: { text: 'Cancelada', tone: 'neutral' },
}

export type AccessLevel = 'READ' | 'EDIT' | 'ADMIN'
export type AccessStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'REVOKED' | 'CANCELLED'

export interface AccessResource {
  id: string
  name: string
  description: string
  owner: string
}

export interface AccessRequest {
  id: string
  code: string
  resourceId: string
  resourceName: string
  resourceOwner: string
  level: AccessLevel
  justification: string
  expiresOn: string | null
  requesterName: string
  requesterEmail: string
  status: AccessStatus
  active: boolean
  expired: boolean
  decidedByName: string | null
  decidedAt: string | null
  decisionNote: string | null
  revokedByName: string | null
  revokedAt: string | null
  revokeReason: string | null
  createdAt: string
}

export const levelLabels: Record<AccessLevel, string> = { READ: 'Solo lectura', EDIT: 'Edición', ADMIN: 'Administración' }

/** El estado que se muestra distingue un acceso aprobado vigente de uno ya vencido. */
export function accessStatus(request: AccessRequest): { text: string; tone: Tone } {
  if (request.status === 'APPROVED') return request.expired ? { text: 'Vencido', tone: 'neutral' } : { text: 'Vigente', tone: 'mint' }
  return {
    PENDING: { text: 'Pendiente', tone: 'blue' as Tone },
    REJECTED: { text: 'Rechazado', tone: 'danger' as Tone },
    REVOKED: { text: 'Revocado', tone: 'danger' as Tone },
    CANCELLED: { text: 'Cancelado', tone: 'neutral' as Tone },
  }[request.status]
}

export const createServiceRequest = (body: { serviceId: string; title: string; justification: string; neededBy?: string }) =>
  api<ServiceRequest>('/service-requests', { method: 'POST', body })
export const myServiceRequests = () => api<Page<ServiceRequest>>('/service-requests/mine?size=100')
export const allServiceRequests = () => api<Page<ServiceRequest>>('/service-requests?size=100')
export const cancelServiceRequest = (id: string) => api<ServiceRequest>(`/service-requests/${id}/cancel`, { method: 'POST' })
export const decideServiceRequest = (id: string, approved: boolean, note?: string) =>
  api<ServiceRequest>(`/service-requests/${id}/decision`, { method: 'POST', body: { approved, note } })
export const takeServiceRequest = (id: string) => api<ServiceRequest>(`/service-requests/${id}/take`, { method: 'POST' })
export const fulfillServiceRequest = (id: string, note: string) => api<ServiceRequest>(`/service-requests/${id}/fulfill`, { method: 'POST', body: { note } })

export const listAccessResources = () => api<AccessResource[]>('/access/resources')
export const requestAccess = (body: { resourceId: string; level: AccessLevel; justification: string; expiresOn?: string }) =>
  api<AccessRequest>('/access/requests', { method: 'POST', body })
export const myAccessRequests = () => api<Page<AccessRequest>>('/access/requests/mine?size=100')
export const allAccessRequests = () => api<Page<AccessRequest>>('/access/requests?size=100')
export const cancelAccessRequest = (id: string) => api<AccessRequest>(`/access/requests/${id}/cancel`, { method: 'POST' })
export const decideAccessRequest = (id: string, approved: boolean, note?: string) =>
  api<AccessRequest>(`/access/requests/${id}/decision`, { method: 'POST', body: { approved, note } })
export const revokeAccess = (id: string, reason: string) => api<AccessRequest>(`/access/requests/${id}/revoke`, { method: 'POST', body: { reason } })
