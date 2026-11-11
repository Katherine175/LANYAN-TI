import type { Role } from '../types'
import { api, forgetCsrf } from './api'

export type BackendRole = 'ROLE_ADMIN' | 'ROLE_TECHNICIAN' | 'ROLE_USER' | 'ROLE_STUDENT'

export interface Account {
  id: string
  fullName: string
  email: string
  active: boolean
  roles: BackendRole[]
  permissions: string[]
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface RoleSummary {
  name: BackendRole
  permissions: string[]
}

export interface AuditEvent {
  id: string
  occurredAt: string
  action: string
  actorEmail: string | null
  targetType: string | null
  targetId: string | null
  remoteAddress: string | null
  detail: string | null
}

export const backendRoleLabels: Record<BackendRole, string> = {
  ROLE_ADMIN: 'Administrador',
  ROLE_TECHNICIAN: 'Técnico TI',
  ROLE_USER: 'Usuario final',
  ROLE_STUDENT: 'Estudiante',
}

/** Vista del portal: si una cuenta tiene varios roles, manda el de mayor alcance. */
export function portalRole(account: Account): Role {
  if (account.roles.includes('ROLE_ADMIN')) return 'administrador'
  if (account.roles.includes('ROLE_TECHNICIAN')) return 'tecnico'
  return 'usuario'
}

export function mainRoleLabel(account: Account): string {
  const order: BackendRole[] = ['ROLE_ADMIN', 'ROLE_TECHNICIAN', 'ROLE_STUDENT', 'ROLE_USER']
  const main = order.find((role) => account.roles.includes(role))
  return main ? backendRoleLabels[main] : 'Sin rol'
}

export const initials = (name: string) => name.trim().split(/\s+/).map((word) => word[0]).join('').slice(0, 2).toUpperCase()

export const currentAccount = () => api<Account>('/auth/me')

export async function login(email: string, password: string): Promise<Account> {
  forgetCsrf()
  const account = await api<Account>('/auth/login', { method: 'POST', body: { email, password } })
  forgetCsrf() // La sesión cambia al autenticar y el token anterior deja de valer.
  return account
}

export async function logout(): Promise<void> {
  try {
    await api<void>('/auth/logout', { method: 'POST' })
  } finally {
    forgetCsrf()
  }
}

export const listAccounts = () => api<Page<Account>>('/users?page=0&size=100')
export const createAccount = (body: { fullName: string; email: string; password: string; roles: BackendRole[] }) =>
  api<Account>('/users', { method: 'POST', body })
export const updateAccount = (id: string, body: { fullName: string; email: string; active: boolean; roles: BackendRole[]; password?: string }) =>
  api<Account>(`/users/${id}`, { method: 'PUT', body })
export const deleteAccount = (id: string) => api<void>(`/users/${id}`, { method: 'DELETE' })
export const listRoles = () => api<RoleSummary[]>('/roles')

export const INSTITUTIONAL_DOMAIN = 'unp.edu.pe'
export const isInstitutionalEmail = (email: string) => {
  const value = email.trim().toLowerCase()
  const at = value.indexOf('@')
  return at > 0 && at === value.lastIndexOf('@') && value.slice(at + 1) === INSTITUTIONAL_DOMAIN
}
export const listAudit = (page: number) => api<Page<AuditEvent>>(`/audit/events?page=${page}&size=20`)
