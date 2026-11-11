import { useEffect, useState, type FormEvent } from 'react'
import { Activity, BellRing, CalendarClock, Check, Plus, TicketCheck, X } from 'lucide-react'
import { Badge, Button, Card, Input, Modal, PageHeader, Progress, Select, Textarea } from '../components/ui'
import { formatMinutes, listPublishedServices, type CatalogService } from '../services/catalogService'
import { formatDateTime } from '../services/ticketService'
import {
  acknowledgeEvent, cancelMaintenance, closeEvent, eventStatusLabels, eventToIncident, impactLabels, listAvailability, listEvents,
  listMaintenance, registerEvent, scheduleMaintenance, severityLabels,
  type Availability, type Impact, type Maintenance, type OperationalEvent, type Severity,
} from '../services/operationsService'

const errorText = (error: unknown) => (error instanceof Error ? error.message : 'No se pudo completar la operación.')
const dayMonth = (iso: string) => {
  const date = new Date(iso)
  return { day: date.toLocaleDateString('es-PE', { day: '2-digit', timeZone: 'America/Lima' }), month: date.toLocaleDateString('es-PE', { month: 'short', timeZone: 'America/Lima' }).replace('.', '').toUpperCase() }
}
const hour = (iso: string) => new Date(iso).toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit', timeZone: 'America/Lima' })

export function OperationsPage({ notify }: { notify: (message: string) => void }) {
  const [availability, setAvailability] = useState<Availability[]>([])
  const [events, setEvents] = useState<OperationalEvent[]>([])
  const [maintenance, setMaintenance] = useState<Maintenance[]>([])
  const [filter, setFilter] = useState<'ACTIVE' | 'ALL'>('ACTIVE')
  const [error, setError] = useState('')
  const [registering, setRegistering] = useState(false)
  const [scheduling, setScheduling] = useState(false)

  const load = () => Promise.all([listAvailability(), listEvents(), listMaintenance()])
    .then(([a, e, m]) => { setAvailability(a); setEvents(e); setMaintenance(m); setError('') })
    .catch((e) => setError(errorText(e)))
  useEffect(() => { void load() }, [])

  const act = async (action: () => Promise<unknown>, message: string) => {
    try { await action(); notify(message); await load() } catch (e) { setError(errorText(e)) }
  }

  const down = availability.filter((item) => item.currentStatus !== 'OPERATIONAL')
  const average = availability.length === 0 ? 100 : availability.reduce((total, item) => total + Number(item.availability), 0) / availability.length
  const shown = events.filter((item) => filter === 'ALL' || item.status !== 'CLOSED')
  const upcoming = maintenance.filter((item) => !item.cancelled && new Date(item.endsAt) > new Date())

  return (
    <>
      <PageHeader eyebrow="Monitoreo de servicios" title="Operaciones TI" description="Eventos, disponibilidad real de los últimos 30 días y mantenimientos programados." action={<div className="header-actions"><Button variant="secondary" onClick={() => setRegistering(true)}><BellRing size={16} />Registrar evento</Button><Button onClick={() => setScheduling(true)}><Plus size={17} />Programar mantenimiento</Button></div>} />
      {error && <div className="error-note">{error}</div>}
      <div className={`status-banner ${down.length ? 'status-banner-alert' : ''}`}>
        <div><span className="pulse" /><div><strong>{down.length === 0 ? 'Todos los servicios operan con normalidad' : `${down.length} ${down.length === 1 ? 'servicio con fallas' : 'servicios con fallas'}: ${down.map((item) => item.serviceName).join(', ')}`}</strong><p>El estado se cambia desde el Catálogo TI; cada cambio queda como evento.</p></div></div>
        <Badge tone={down.some((item) => item.currentStatus === 'UNAVAILABLE') ? 'danger' : down.length ? 'pink' : 'mint'}>{down.length ? 'Atención' : 'Operativo'}</Badge>
      </div>
      <div className="operations-grid">
        <Card className="availability-card">
          <div className="card-title"><div><h2>Disponibilidad de servicios</h2><p>Últimos 30 días frente a la meta del catálogo</p></div><strong className="big-number">{average.toFixed(2)}%</strong></div>
          {availability.map((item) => (
            <div className="availability-row availability-detail" key={item.serviceId}>
              <div><span className={`pulse small ${item.currentStatus.toLowerCase()}`} /><strong>{item.serviceName}</strong></div>
              <Progress value={Number(item.availability)} tone={item.meetsTarget ? 'mint' : 'pink'} />
              <span className={item.meetsTarget ? '' : 'sla-breached'}>{Number(item.availability).toFixed(2)}%</span>
              <small>Meta {Number(item.target).toFixed(2)}% · {item.outages} {item.outages === 1 ? 'caída' : 'caídas'}{item.mtrsMinutes !== null ? ` · restauración media ${formatMinutes(item.mtrsMinutes)}` : ''}{item.degradedMinutes > 0 ? ` · ${formatMinutes(item.degradedMinutes)} con fallas` : ''}</small>
            </div>
          ))}
        </Card>
        <Card>
          <div className="card-title"><div><h2>Próximos mantenimientos</h2><p>Interrupciones planificadas</p></div></div>
          {upcoming.length === 0 && <p className="muted">No hay mantenimientos programados.</p>}
          <div className="timeline">{upcoming.map((item) => {
            const date = dayMonth(item.startsAt)
            return (
              <div key={item.id}>
                <span>{date.day}</span><small>{date.month}</small>
                <p><strong>{item.title}</strong><small>{item.serviceName} · {hour(item.startsAt)} a {hour(item.endsAt)} · impacto {impactLabels[item.impact].toLowerCase()}</small></p>
                {new Date(item.startsAt) > new Date() && <Button variant="ghost" className="icon-btn" aria-label={`Cancelar ${item.title}`} onClick={() => void act(() => cancelMaintenance(item.id), 'Mantenimiento cancelado')}><X size={14} /></Button>}
              </div>
            )
          })}</div>
        </Card>
      </div>
      <Card className="table-card events-card">
        <div className="card-title"><div><h2><Activity size={17} />Eventos</h2><p>Automáticos (cambios de estado) y registrados por TI. Una excepción que afecta a los usuarios se convierte en incidente.</p></div><Select value={filter} onChange={(e) => setFilter(e.target.value as 'ACTIVE' | 'ALL')}><option value="ACTIVE">Abiertos y revisados</option><option value="ALL">Todos</option></Select></div>
        <div className="table-scroll"><table>
          <thead><tr><th>Evento</th><th>Servicio</th><th>Tipo</th><th>Estado</th><th>Incidente</th><th>Acciones</th></tr></thead>
          <tbody>{shown.map((item) => (
            <tr key={item.id}>
              <td><strong>{item.code} · {item.title}</strong><span>{formatDateTime(item.createdAt)} · {item.source === 'AUTOMATIC' ? 'automático' : `registrado por ${item.createdBy ?? 'TI'}`}</span></td>
              <td>{item.serviceName ?? '—'}</td>
              <td><Badge tone={severityLabels[item.severity].tone}>{severityLabels[item.severity].text}</Badge></td>
              <td><Badge tone={eventStatusLabels[item.status].tone}>{eventStatusLabels[item.status].text}</Badge></td>
              <td>{item.ticketCode ?? '—'}</td>
              <td><div className="row-actions">
                {item.status === 'OPEN' && <Button variant="ghost" onClick={() => void act(() => acknowledgeEvent(item.id), `${item.code} revisado`)}><Check size={15} />Revisado</Button>}
                {item.severity !== 'INFO' && item.status !== 'CLOSED' && !item.ticketCode && item.serviceId && <Button variant="ghost" onClick={() => void act(() => eventToIncident(item.id), `Incidente creado desde ${item.code}`)}><TicketCheck size={15} />Crear incidente</Button>}
                {item.status !== 'CLOSED' && <Button variant="ghost" onClick={() => void act(() => closeEvent(item.id), `${item.code} cerrado`)}>Cerrar</Button>}
              </div></td>
            </tr>
          ))}</tbody>
        </table>{shown.length === 0 && <div className="empty-state"><Activity size={24} /><strong>Sin eventos pendientes</strong><span>Los cambios de estado de los servicios aparecen aquí automáticamente.</span></div>}</div>
      </Card>
      {registering && <RegisterEventModal onClose={() => setRegistering(false)} onSaved={() => { notify('Evento registrado'); void load() }} />}
      {scheduling && <MaintenanceModal onClose={() => setScheduling(false)} onSaved={() => { notify('Mantenimiento programado'); void load() }} />}
    </>
  )
}

function useServices() {
  const [services, setServices] = useState<CatalogService[]>([])
  useEffect(() => { listPublishedServices().then((page) => setServices(page.content)).catch(() => setServices([])) }, [])
  return services
}

function RegisterEventModal({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const services = useServices()
  const [form, setForm] = useState({ serviceId: '', severity: 'WARNING' as Severity, title: '', detail: '' })
  const [error, setError] = useState('')
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    try {
      await registerEvent({ serviceId: form.serviceId || undefined, severity: form.severity, title: form.title, detail: form.detail || undefined })
      onSaved()
      onClose()
    } catch (e) { setError(errorText(e)) }
  }
  return (
    <Modal open onClose={onClose} title="Registrar evento" footer={<><Button variant="secondary" onClick={onClose}>Cancelar</Button><Button type="submit" form="event-form">Registrar</Button></>}>
      <form id="event-form" className="account-form" onSubmit={submit}>
        <div className="form-grid">
          <label className="field-label">Tipo<Select value={form.severity} onChange={(e) => setForm({ ...form, severity: e.target.value as Severity })}>{(Object.keys(severityLabels) as Severity[]).map((item) => <option key={item} value={item}>{severityLabels[item].text}</option>)}</Select></label>
          <label className="field-label">Servicio (opcional)<Select value={form.serviceId} onChange={(e) => setForm({ ...form, serviceId: e.target.value })}><option value="">Ninguno en particular</option>{services.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</Select></label>
        </div>
        <label className="field-label">Qué se detectó<Input required maxLength={160} value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} placeholder="Ej. El disco del servidor llegó al 85 %" /></label>
        <label className="field-label">Detalle<Textarea rows={3} maxLength={2000} value={form.detail} onChange={(e) => setForm({ ...form, detail: e.target.value })} placeholder="Fuente, valores observados, a quién afecta" /></label>
        <p className="muted">Informativo: algo normal que conviene registrar. Advertencia: algo que puede fallar pronto. Excepción: algo ya falló.</p>
        {error && <div className="error-note">{error}</div>}
      </form>
    </Modal>
  )
}

function MaintenanceModal({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const services = useServices()
  const [form, setForm] = useState({ serviceId: '', title: '', start: '', end: '', impact: 'LOW' as Impact })
  const [error, setError] = useState('')
  useEffect(() => { if (!form.serviceId && services[0]) setForm((f) => ({ ...f, serviceId: services[0].id })) }, [services])
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (!form.start || !form.end || form.end <= form.start) { setError('La hora de fin debe ser posterior al inicio.'); return }
    try {
      // datetime-local está en hora del equipo; se envía como instante.
      await scheduleMaintenance({ serviceId: form.serviceId, title: form.title, startsAt: new Date(form.start).toISOString(), endsAt: new Date(form.end).toISOString(), impact: form.impact })
      onSaved()
      onClose()
    } catch (e) { setError(errorText(e)) }
  }
  return (
    <Modal open onClose={onClose} title="Programar mantenimiento" footer={<><Button variant="secondary" onClick={onClose}>Cancelar</Button><Button type="submit" form="maintenance-form"><CalendarClock size={16} />Programar</Button></>}>
      <form id="maintenance-form" className="account-form" onSubmit={submit}>
        <label className="field-label">Servicio<Select value={form.serviceId} onChange={(e) => setForm({ ...form, serviceId: e.target.value })}>{services.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</Select></label>
        <label className="field-label">Descripción<Input required maxLength={160} value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} placeholder="Ej. Actualización de seguridad del aula virtual" /></label>
        <div className="form-grid"><label className="field-label">Inicio<Input required type="datetime-local" value={form.start} onChange={(e) => setForm({ ...form, start: e.target.value })} /></label><label className="field-label">Fin<Input required type="datetime-local" value={form.end} onChange={(e) => setForm({ ...form, end: e.target.value })} /></label></div>
        <label className="field-label">Impacto<Select value={form.impact} onChange={(e) => setForm({ ...form, impact: e.target.value as Impact })}>{(Object.keys(impactLabels) as Impact[]).map((item) => <option key={item} value={item}>{impactLabels[item]}</option>)}</Select></label>
        {error && <div className="error-note">{error}</div>}
      </form>
    </Modal>
  )
}

