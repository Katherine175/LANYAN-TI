import type {
  KnowledgeArticle,
  Problem,
  Service,
  Ticket,
  User,
  WorkflowRequest,
} from '../types'

export const tickets: Ticket[] = [
  { id: 'INC-1048', title: 'No puedo ingresar al aula virtual', service: 'Plataforma virtual', requester: 'María Torres', technician: 'Carlos Vega', priority: 'Alta', status: 'En progreso', createdAt: 'Hoy, 08:42', sla: 62, comments: 4 },
  { id: 'INC-1047', title: 'Pago no reflejado en matrícula', service: 'Pagos', requester: 'José Ruiz', technician: 'Ana Peña', priority: 'Crítica', status: 'Nuevo', createdAt: 'Hoy, 08:15', sla: 28, comments: 2 },
  { id: 'REQ-0832', title: 'Configuración de correo institucional', service: 'Correo', requester: 'Lucía Ramos', technician: 'Carlos Vega', priority: 'Media', status: 'En espera', createdAt: 'Ayer, 16:20', sla: 79, comments: 6 },
  { id: 'INC-1044', title: 'Internet inestable en laboratorio 2', service: 'Internet', requester: 'Pedro León', technician: 'Miguel Silva', priority: 'Alta', status: 'En progreso', createdAt: 'Ayer, 13:05', sla: 45, comments: 8 },
  { id: 'REQ-0829', title: 'Préstamo de proyector para taller', service: 'Proyectores', requester: 'Rosa Díaz', technician: 'Ana Peña', priority: 'Baja', status: 'Resuelto', createdAt: '12 Jun, 10:30', sla: 100, comments: 3 },
  { id: 'INC-1041', title: 'Error al generar constancia', service: 'Plataforma virtual', requester: 'Daniel Chunga', technician: 'Miguel Silva', priority: 'Media', status: 'Resuelto', createdAt: '11 Jun, 09:12', sla: 100, comments: 5 },
]

export const services: Service[] = [
  { id: 'SRV-01', name: 'Plataforma virtual', description: 'Aulas, evaluaciones y recursos de aprendizaje.', category: 'Académico', availability: 99.8, responseTime: '30 min', icon: 'monitor' },
  { id: 'SRV-02', name: 'Matrícula', description: 'Inscripción, cambios de horario y constancias.', category: 'Académico', availability: 99.5, responseTime: '2 h', icon: 'graduation' },
  { id: 'SRV-03', name: 'Pagos', description: 'Validación de pagos, recibos y devoluciones.', category: 'Administrativo', availability: 98.9, responseTime: '1 h', icon: 'credit' },
  { id: 'SRV-04', name: 'Correo institucional', description: 'Cuentas, recuperación y configuración.', category: 'Comunicación', availability: 99.9, responseTime: '30 min', icon: 'mail' },
  { id: 'SRV-05', name: 'Internet y Wi-Fi', description: 'Conectividad en aulas y laboratorios.', category: 'Infraestructura', availability: 97.6, responseTime: '20 min', icon: 'wifi' },
  { id: 'SRV-06', name: 'Proyectores', description: 'Reserva, instalación y soporte audiovisual.', category: 'Equipamiento', availability: 96.4, responseTime: '1 h', icon: 'projector' },
  { id: 'SRV-07', name: 'Soporte técnico', description: 'Asistencia para equipos y aplicaciones.', category: 'Soporte', availability: 99.2, responseTime: '15 min', icon: 'headset' },
]

export const requests: WorkflowRequest[] = [
  { id: 'ACC-204', title: 'Acceso a reportes de matrícula', requester: 'Elena Mena', type: 'Acceso', status: 'Pendiente', date: '14 Jun 2025', approver: 'Dirección Académica' },
  { id: 'ACC-203', title: 'Permiso de editor en aula B2', requester: 'Marco Ríos', type: 'Acceso', status: 'Aprobado', date: '13 Jun 2025', approver: 'Coordinación B2' },
  { id: 'SOL-391', title: 'Instalación de software de idiomas', requester: 'Julia Cruz', type: 'Servicio', status: 'En proceso', date: '13 Jun 2025', approver: 'Jefatura TI' },
  { id: 'SOL-388', title: 'Habilitación de laboratorio 3', requester: 'Luis Zapata', type: 'Servicio', status: 'Cerrado', date: '11 Jun 2025', approver: 'Administración' },
  { id: 'ACC-197', title: 'Acceso temporal a calificaciones', requester: 'Sara Pinto', type: 'Acceso', status: 'Revocado', date: '10 Jun 2025', approver: 'Dirección Académica' },
]

export const articles: KnowledgeArticle[] = [
  { id: 'KB-019', title: 'Cómo recuperar el acceso al aula virtual', category: 'Plataforma virtual', summary: 'Pasos para restablecer la contraseña y validar el correo institucional.', views: 1248, helpful: 94, updatedAt: '12 Jun' },
  { id: 'KB-018', title: 'Configurar el correo en un dispositivo móvil', category: 'Correo', summary: 'Guía rápida para Android y iOS con configuración segura.', views: 834, helpful: 91, updatedAt: '10 Jun' },
  { id: 'KB-014', title: 'Verificar el estado de un pago', category: 'Pagos', summary: 'Dónde encontrar el comprobante y qué hacer si aún no aparece.', views: 721, helpful: 88, updatedAt: '06 Jun' },
  { id: 'KB-011', title: 'Conectarse a la red Wi-Fi institucional', category: 'Internet', summary: 'Credenciales, cobertura y solución de errores frecuentes.', views: 689, helpful: 96, updatedAt: '01 Jun' },
]

export const problems: Problem[] = [
  { id: 'PRB-042', title: 'Intermitencia recurrente en laboratorio 2', incidents: 12, cause: 'Saturación del punto de acceso', owner: 'Miguel Silva', status: 'Causa identificada' },
  { id: 'PRB-039', title: 'Duplicidad de pagos en hora punta', incidents: 7, cause: 'En investigación', owner: 'Ana Peña', status: 'Investigando' },
  { id: 'PRB-035', title: 'Sesiones expiran antes de tiempo', incidents: 18, cause: 'Configuración de caché', owner: 'Carlos Vega', status: 'Resuelto' },
]

export const users: User[] = [
  { id: 'USR-01', name: 'Andrea Salazar', email: 'asalazar@unp.edu.pe', role: 'administrador', active: true },
  { id: 'USR-02', name: 'Carlos Vega', email: 'cvega@unp.edu.pe', role: 'tecnico', active: true },
  { id: 'USR-03', name: 'María Torres', email: 'mtorres@unp.edu.pe', role: 'usuario', active: true },
  { id: 'USR-04', name: 'Miguel Silva', email: 'msilva@unp.edu.pe', role: 'tecnico', active: false },
]

export const monthlyData = [
  { month: 'Ene', opened: 52, resolved: 45 },
  { month: 'Feb', opened: 62, resolved: 57 },
  { month: 'Mar', opened: 49, resolved: 52 },
  { month: 'Abr', opened: 71, resolved: 63 },
  { month: 'May', opened: 58, resolved: 61 },
  { month: 'Jun', opened: 66, resolved: 59 },
]
