import { api } from './api'
import type { Page } from './identityService'

export type ServiceIcon = 'MONITOR' | 'GRADUATION' | 'CREDIT' | 'MAIL' | 'WIFI' | 'PROJECTOR' | 'HEADSET'
export type ServiceLifecycle = 'DRAFT' | 'PUBLISHED' | 'RETIRED'
export type OperationalStatus = 'OPERATIONAL' | 'DEGRADED' | 'UNAVAILABLE'

export interface Category {
  id: string
  name: string
}

export interface CatalogService {
  id: string
  code: string
  name: string
  description: string
  category: Category
  owner: string
  icon: ServiceIcon
  lifecycleStatus: ServiceLifecycle
  operationalStatus: OperationalStatus
  availabilityTarget: number
  responseTargetMinutes: number
  version: number
}

export interface ServiceInput {
  name: string
  description: string
  categoryId: string
  owner: string
  icon: ServiceIcon
  lifecycleStatus: ServiceLifecycle
  availabilityTarget: number
  responseTargetMinutes: number
  version?: number
}

export const operationalLabels: Record<OperationalStatus, { text: string; tone: 'mint' | 'pink' | 'danger' }> = {
  OPERATIONAL: { text: 'Operativo', tone: 'mint' },
  DEGRADED: { text: 'Degradado', tone: 'pink' },
  UNAVAILABLE: { text: 'No disponible', tone: 'danger' },
}

export const lifecycleLabels: Record<ServiceLifecycle, { text: string; tone: 'neutral' | 'mint' | 'danger' }> = {
  DRAFT: { text: 'Borrador', tone: 'neutral' },
  PUBLISHED: { text: 'Publicado', tone: 'mint' },
  RETIRED: { text: 'Retirado', tone: 'danger' },
}

export function formatMinutes(minutes: number): string {
  if (minutes < 60) return `${minutes} min`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest ? `${hours} h ${rest} min` : `${hours} h`
}

export const listPublishedServices = () => api<Page<CatalogService>>('/catalog/services')
export const listAllServices = () => api<Page<CatalogService>>('/catalog/admin/services')
export const listCategories = () => api<Category[]>('/catalog/categories')
export const createCategory = (name: string) => api<Category>('/catalog/admin/categories', { method: 'POST', body: { name } })
export const createService = (body: ServiceInput) => api<CatalogService>('/catalog/admin/services', { method: 'POST', body })
export const updateService = (id: string, body: ServiceInput) => api<CatalogService>(`/catalog/admin/services/${id}`, { method: 'PUT', body })
export const changeOperationalStatus = (id: string, status: OperationalStatus, version: number) =>
  api<CatalogService>(`/catalog/services/${id}/operational-status`, { method: 'PUT', body: { status, version } })
