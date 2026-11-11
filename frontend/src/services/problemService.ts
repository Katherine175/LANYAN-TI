import { api } from './api'
import type { Priority, TicketStatus } from './ticketService'

export type ProblemStatus = 'INVESTIGATING' | 'KNOWN_ERROR' | 'RESOLVED'

export interface LinkedIncident {
  id: string
  code: string
  title: string
  status: TicketStatus
  createdAt: string
}

export interface Problem {
  id: string
  code: string
  title: string
  description: string
  serviceId: string
  serviceName: string
  priority: Priority
  status: ProblemStatus
  ownerName: string
  rootCause: string | null
  workaround: string | null
  permanentSolution: string | null
  createdAt: string
  knownErrorAt: string | null
  resolvedAt: string | null
  incidents: LinkedIncident[]
}

export interface Candidate {
  serviceId: string
  serviceName: string
  incidents: number
  lastIncidentAt: string
}

export interface KnownError {
  id: string
  code: string
  title: string
  rootCause: string
  workaround: string
  status: ProblemStatus
}

export const problemStatusLabels: Record<ProblemStatus, { text: string; tone: 'blue' | 'pink' | 'mint' }> = {
  INVESTIGATING: { text: 'Investigando', tone: 'blue' },
  KNOWN_ERROR: { text: 'Error conocido', tone: 'pink' },
  RESOLVED: { text: 'Resuelto', tone: 'mint' },
}

export const listProblems = () => api<Problem[]>('/problems')
export const problemCandidates = () => api<Candidate[]>('/problems/candidates')
export const knownErrors = (serviceId: string) => api<KnownError[]>(`/problems/known-errors?serviceId=${serviceId}`)
export const createProblem = (body: { title: string; description: string; serviceId: string; priority: Priority; ticketIds: string[] }) =>
  api<Problem>('/problems', { method: 'POST', body })
export const linkIncident = (id: string, ticketId: string) => api<Problem>(`/problems/${id}/incidents`, { method: 'POST', body: { ticketId } })
export const unlinkIncident = (id: string, ticketId: string) => api<Problem>(`/problems/${id}/incidents/${ticketId}`, { method: 'DELETE' })
export const documentKnownError = (id: string, body: { rootCause: string; workaround: string }) =>
  api<Problem>(`/problems/${id}/known-error`, { method: 'PUT', body })
export const resolveProblem = (id: string, body: { permanentSolution: string; resolveLinkedTickets: boolean }) =>
  api<Problem>(`/problems/${id}/resolve`, { method: 'POST', body })
