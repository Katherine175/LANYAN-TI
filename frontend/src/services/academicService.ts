import { api } from './api'
import type { Page } from './identityService'

export type Weekday = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY'
export type Modality = 'IN_PERSON' | 'ONLINE' | 'HYBRID'
export type CourseStatus = 'ACTIVE' | 'INACTIVE'
export type SectionStatus = 'OPEN' | 'CLOSED' | 'CANCELLED'

export interface CatalogSchedule {
  dayOfWeek: Weekday
  startTime: string
  endTime: string
  modality: Modality
  classroom: string | null
}

export interface CatalogSection {
  id: string
  code: string
  academicPeriod: string
  startDate: string
  endDate: string
  capacity: number
  availableSeats: number
  schedules: CatalogSchedule[]
}

export interface CatalogCourse {
  id: string
  code: string
  name: string
  language: string
  level: string | null
  description: string | null
  sections: CatalogSection[]
}

export interface Course {
  id: string
  code: string
  name: string
  language: string
  level: string | null
  description: string | null
  status: CourseStatus
  createdAt: string
}

export interface Section {
  id: string
  courseId: string
  code: string
  academicPeriod: string
  startDate: string
  endDate: string
  capacity: number
  status: SectionStatus
}

export interface Schedule {
  id: string
  dayOfWeek: Weekday
  startTime: string
  endTime: string
  modality: Modality
  classroom: string | null
}

export const weekdayLabels: Record<Weekday, string> = {
  MONDAY: 'Lunes', TUESDAY: 'Martes', WEDNESDAY: 'Miércoles', THURSDAY: 'Jueves', FRIDAY: 'Viernes', SATURDAY: 'Sábado', SUNDAY: 'Domingo',
}
export const modalityLabels: Record<Modality, string> = { IN_PERSON: 'Presencial', ONLINE: 'Virtual', HYBRID: 'Híbrida' }
export const courseStatusLabels: Record<CourseStatus, string> = { ACTIVE: 'Activo', INACTIVE: 'Inactivo' }
export const sectionStatusLabels: Record<SectionStatus, string> = { OPEN: 'Abierta', CLOSED: 'Cerrada', CANCELLED: 'Cancelada' }

/** "18:00:00" -> "18:00" */
export const shortTime = (time: string) => time.slice(0, 5)
const dateFormat = new Intl.DateTimeFormat('es-PE', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' })
export const formatDate = (date: string) => dateFormat.format(new Date(`${date}T00:00:00Z`))

export const getCourseCatalog = () => api<CatalogCourse[]>('/courses/catalog')

export const listCourses = () => api<Page<Course>>('/academic/courses?page=0&size=100')
export const createCourse = (body: { code: string; name: string; language: string; level?: string; description?: string; status: CourseStatus }) =>
  api<Course>('/academic/courses', { method: 'POST', body })
export const listSections = (courseId: string) => api<Page<Section>>(`/academic/courses/${courseId}/sections?page=0&size=100`)
export const createSection = (courseId: string, body: { code: string; academicPeriod: string; startDate: string; endDate: string; capacity: number; status: SectionStatus }) =>
  api<Section>(`/academic/courses/${courseId}/sections`, { method: 'POST', body })
export const listSchedules = (sectionId: string) => api<Page<Schedule>>(`/academic/sections/${sectionId}/schedules?page=0&size=100`)
export const createSchedule = (sectionId: string, body: { dayOfWeek: Weekday; startTime: string; endTime: string; modality: Modality; classroom?: string }) =>
  api<Schedule>(`/academic/sections/${sectionId}/schedules`, { method: 'POST', body })
