export type Role = 'usuario' | 'tecnico' | 'administrador'

export type Priority = 'Crítica' | 'Alta' | 'Media' | 'Baja'
export type TicketStatus = 'Nuevo' | 'En progreso' | 'En espera' | 'Resuelto'

export interface Ticket {
  id: string
  title: string
  service: string
  requester: string
  technician: string
  priority: Priority
  status: TicketStatus
  createdAt: string
  sla: number
  description?: string
  comments: number
}

export interface Service {
  id: string
  name: string
  description: string
  category: string
  availability: number
  responseTime: string
  icon: string
}

export interface WorkflowRequest {
  id: string
  title: string
  requester: string
  type: 'Acceso' | 'Servicio'
  status: 'Pendiente' | 'Aprobado' | 'En proceso' | 'Cerrado' | 'Revocado'
  date: string
  approver: string
}

export interface KnowledgeArticle {
  id: string
  title: string
  category: string
  summary: string
  views: number
  helpful: number
  updatedAt: string
}

export interface Problem {
  id: string
  title: string
  incidents: number
  cause: string
  owner: string
  status: 'Investigando' | 'Causa identificada' | 'Resuelto'
}

export interface User {
  id: string
  name: string
  email: string
  role: Role
  active: boolean
}

export type RouteKey =
  | 'dashboard'
  | 'catalogo'
  | 'tickets'
  | 'service-desk'
  | 'problemas'
  | 'accesos'
  | 'solicitudes'
  | 'operaciones'
  | 'conocimiento'
  | 'reportes'
  | 'mejora'
  | 'administracion'
