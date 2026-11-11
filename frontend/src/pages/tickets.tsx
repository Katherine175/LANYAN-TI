import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { ArrowLeft, ArrowRight, ChevronRight, CircleAlert, Clock3, Hand, Plus, Search, ShieldAlert, Timer } from 'lucide-react'
import { Badge, Button, Card, Input, Modal, PageHeader, Progress, Select, Textarea } from '../components/ui'
import type { Role } from '../types'
import { formatMinutes, listPublishedServices, type CatalogService } from '../services/catalogService'
import {
  ago, allTickets, changeTicketPriority, changeTicketStatus, createTicket, formatDateTime, listSlaPolicies, myTickets, priorityLabels,
  SLA_HOURS_TEXT, slaLabels, statusLabels, statusOrder, takeTicket, updateSlaPolicy,
  type Priority, type SlaPolicy, type Ticket, type TicketStatus,
} from '../services/ticketService'

const errorText = (error: unknown) => (error instanceof Error ? error.message : 'No se pudo completar la operación.')
export const isStaff = (role: Role) => role === 'tecnico' || role === 'administrador'

/** Tickets visibles para el rol: los propios o todos si atiende. */
export function useTickets(role: Role) {
  const [tickets, setTickets] = useState<Ticket[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const reload = () => (isStaff(role) ? allTickets() : myTickets())
    .then((page) => { setTickets(page.content); setError('') })
    .catch((e) => setError(errorText(e)))
    .finally(() => setLoading(false))
  useEffect(() => { void reload() }, [role])
  const replace = (ticket: Ticket) => setTickets((list) => list.map((item) => (item.id === ticket.id ? ticket : item)))
  return { tickets, loading, error, reload, replace }
}

function SlaBar({ ticket }: { ticket: Ticket }) {
  const state = ticket.resolutionSla
  const tone = state === 'BREACHED' || state === 'MISSED' ? 'pink' : state === 'AT_RISK' ? 'lavender' : 'mint'
  const label = ticket.status === 'RESOLVED' ? slaLabels[state].text : `${Math.min(ticket.resolutionElapsedPercent, 100)}% usado`
  return <div className="sla-cell"><Progress value={ticket.resolutionElapsedPercent} tone={tone} /><small className={`sla-${state.toLowerCase()}`}>{label}</small></div>
}

export function TicketTable({ tickets, onSelect, showRequester = false }: { tickets: Ticket[]; onSelect?: (ticket: Ticket) => void; showRequester?: boolean }) {
  return (
    <div className="table-scroll">
      <table>
        <thead><tr><th>Ticket</th><th>Servicio</th>{showRequester && <th>Solicitante</th>}<th>Prioridad</th><th>Estado</th><th>Técnico</th><th>SLA de resolución</th><th /></tr></thead>
        <tbody>
          {tickets.map((ticket) => (
            <tr key={ticket.id} onClick={() => onSelect?.(ticket)} className={onSelect ? 'clickable' : ''}>
              <td><strong>{ticket.code}</strong><span>{ticket.title}</span></td>
              <td>{ticket.service.name}<span>{ago(ticket.createdAt)}</span></td>
              {showRequester && <td>{ticket.requesterName}</td>}
              <td><Badge tone={priorityLabels[ticket.priority].tone}>{priorityLabels[ticket.priority].text}</Badge></td>
              <td><Badge tone={statusLabels[ticket.status].tone}>{statusLabels[ticket.status].text}</Badge></td>
              <td>{ticket.assigneeName ?? <span className="muted">Sin asignar</span>}</td>
              <td><SlaBar ticket={ticket} /></td>
              <td><ChevronRight size={17} /></td>
            </tr>
          ))}
        </tbody>
      </table>
      {tickets.length === 0 && <div className="empty-state"><Search size={24} /><strong>Sin tickets</strong><span>No hay tickets que coincidan.</span></div>}
    </div>
  )
}

export function TicketsPage({ role, notify }: { role: Role; notify: (message: string) => void }) {
  const staff = isStaff(role)
  const { tickets, loading, error, reload, replace } = useTickets(role)
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState<'ALL' | TicketStatus>('ALL')
  const [creating, setCreating] = useState(false)
  const [selected, setSelected] = useState<Ticket | null>(null)

  const filtered = tickets.filter((ticket) => (status === 'ALL' || ticket.status === status)
    && `${ticket.code} ${ticket.title} ${ticket.service.name} ${ticket.requesterName}`.toLowerCase().includes(query.trim().toLowerCase()))

  return (
    <>
      <PageHeader eyebrow="Gestión de atención" title={staff ? 'Tickets' : 'Mis tickets'} description={staff ? 'Atiende incidentes y pedidos según su prioridad y plazo.' : 'Reporta un problema y sigue su atención hasta que quede resuelto.'} action={<Button onClick={() => setCreating(true)}><Plus size={17} />Registrar ticket</Button>} />
      <Card className="toolbar"><div className="table-search"><Search size={17} /><Input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Buscar por código, asunto o servicio" /></div><div className="toolbar-right"><Select value={status} onChange={(e) => setStatus(e.target.value as 'ALL' | TicketStatus)}><option value="ALL">Todos</option>{statusOrder.map((item) => <option key={item} value={item}>{statusLabels[item].text}</option>)}</Select></div></Card>
      {error && <div className="error-note">{error}</div>}
      <Card className="table-card"><div className="result-count"><strong>{filtered.length} {filtered.length === 1 ? 'ticket' : 'tickets'}</strong><span>{SLA_HOURS_TEXT}</span></div>{!loading && <TicketTable tickets={filtered} onSelect={setSelected} showRequester={staff} />}</Card>
      {creating && <CreateTicketModal onClose={() => setCreating(false)} onCreated={(ticket) => { notify(`${ticket.code} registrado · prioridad ${priorityLabels[ticket.priority].text.toLowerCase()}`); setSelected(ticket); void reload() }} />}
      {selected && <TicketDetail ticket={selected} staff={staff} onClose={() => setSelected(null)} onChanged={(ticket, message) => { replace(ticket); setSelected(ticket); notify(message) }} />}
    </>
  )
}

function CreateTicketModal({ onClose, onCreated }: { onClose: () => void; onCreated: (ticket: Ticket) => void }) {
  const [services, setServices] = useState<CatalogService[]>([])
  const [policies, setPolicies] = useState<SlaPolicy[]>([])
  const [form, setForm] = useState({ serviceId: '', title: '', description: '' })
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    Promise.all([listPublishedServices(), listSlaPolicies()])
      .then(([page, list]) => { setServices(page.content); setPolicies(list); setForm((f) => ({ ...f, serviceId: page.content[0]?.id ?? '' })) })
      .catch((e) => setError(errorText(e)))
  }, [])

  const service = services.find((item) => item.id === form.serviceId)
  const policy = service && policies.find((item) => item.priority === service.defaultPriority)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setError('')
    try {
      const ticket = await createTicket({ serviceId: form.serviceId, title: form.title, description: form.description || undefined })
      onCreated(ticket)
      onClose()
    } catch (e) {
      setError(errorText(e))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal open onClose={onClose} title="Registrar nuevo ticket" footer={<><Button variant="secondary" onClick={onClose}>Cancelar</Button><Button type="submit" form="ticket-form" disabled={saving || !form.serviceId}>{saving ? 'Registrando…' : 'Registrar ticket'}</Button></>}>
      <form id="ticket-form" className="account-form" onSubmit={submit}>
        <label className="field-label">Asunto<Input required maxLength={160} value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} placeholder="Resume el problema en una frase" /></label>
        <label className="field-label">Servicio<Select required value={form.serviceId} onChange={(e) => setForm({ ...form, serviceId: e.target.value })}>{services.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</Select></label>
        {service && policy && (
          <div className="priority-preview">
            <div><span>Prioridad asignada</span><Badge tone={priorityLabels[service.defaultPriority].tone}>{priorityLabels[service.defaultPriority].text}</Badge></div>
            <div><span>Primera respuesta</span><strong>{formatMinutes(policy.firstResponseMinutes)}</strong></div>
            <div><span>Resolución</span><strong>{formatMinutes(policy.resolutionMinutes)}</strong></div>
            <p>La prioridad depende del servicio y la ajusta el equipo de TI si el caso lo requiere. Los plazos cuentan en horario de atención (lunes a sábado, 7:00 a 21:00).</p>
          </div>
        )}
        <label className="field-label">Descripción<Textarea rows={5} maxLength={4000} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} placeholder="Incluye los pasos, mensajes de error y cuándo comenzó..." /></label>
        {error && <div className="error-note">{error}</div>}
      </form>
    </Modal>
  )
}

function DeadlineRow({ label, due, doneAt, state }: { label: string; due: string; doneAt: string | null; state: Ticket['firstResponseSla'] }) {
  return (
    <div className="deadline-row">
      <Timer size={16} />
      <div><strong>{label}</strong><span>{doneAt ? `Atendido el ${formatDateTime(doneAt)}` : `Vence el ${formatDateTime(due)}`}</span></div>
      <Badge tone={slaLabels[state].tone}>{slaLabels[state].text}</Badge>
    </div>
  )
}

function TicketDetail({ ticket, staff, onClose, onChanged }: { ticket: Ticket; staff: boolean; onClose: () => void; onChanged: (ticket: Ticket, message: string) => void }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [reprioritizing, setReprioritizing] = useState(false)
  const [priority, setPriority] = useState<Priority>(ticket.priority)
  const [reason, setReason] = useState('')

  const run = async (action: () => Promise<Ticket>, message: string) => {
    setBusy(true)
    setError('')
    try {
      onChanged(await action(), message)
      setReprioritizing(false)
      setReason('')
    } catch (e) {
      setError(errorText(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open onClose={onClose} title={`${ticket.code} · ${ticket.title}`} footer={<Button variant="secondary" onClick={onClose}>Cerrar</Button>}>
      <div className="detail-grid">
        <div><span>Estado</span><Badge tone={statusLabels[ticket.status].tone}>{statusLabels[ticket.status].text}</Badge></div>
        <div><span>Prioridad</span><Badge tone={priorityLabels[ticket.priority].tone}>{priorityLabels[ticket.priority].text}</Badge></div>
        <div><span>Servicio</span><strong>{ticket.service.name}</strong></div>
        <div><span>Técnico</span><strong>{ticket.assigneeName ?? 'Sin asignar'}</strong></div>
        <div><span>Solicitante</span><strong>{ticket.requesterName}</strong></div>
        <div><span>Registrado</span><strong>{formatDateTime(ticket.createdAt)}</strong></div>
      </div>
      {ticket.description && <p className="course-description">{ticket.description}</p>}
      <div className="deadlines">
        <DeadlineRow label="Primera respuesta" due={ticket.firstResponseDue} doneAt={ticket.firstRespondedAt} state={ticket.firstResponseSla} />
        <DeadlineRow label="Resolución" due={ticket.resolutionDue} doneAt={ticket.resolvedAt} state={ticket.resolutionSla} />
        <p className="muted">{SLA_HOURS_TEXT}, hora de Lima. Fuera de ese horario el plazo no corre.</p>
      </div>
      {staff && (
        <div className="staff-actions">
          {!ticket.assigneeName && ticket.status !== 'RESOLVED' && <Button onClick={() => void run(() => takeTicket(ticket.id), `${ticket.code} asignado a ti`)} disabled={busy}><Hand size={16} />Tomar ticket</Button>}
          <div className="status-actions">{statusOrder.filter((item) => item !== 'NEW').map((item) => (
            <Button key={item} variant={ticket.status === item ? 'primary' : 'secondary'} disabled={busy || ticket.status === item}
              onClick={() => void run(() => changeTicketStatus(ticket.id, item), `${ticket.code}: ${statusLabels[item].text.toLowerCase()}`)}>{statusLabels[item].text}</Button>
          ))}</div>
          {reprioritizing
            ? <div className="inline-form">
                <div className="form-grid">
                  <label className="field-label">Nueva prioridad<Select value={priority} onChange={(e) => setPriority(e.target.value as Priority)}>{(Object.keys(priorityLabels) as Priority[]).map((item) => <option key={item} value={item}>{priorityLabels[item].text}</option>)}</Select></label>
                  <label className="field-label">Motivo<Input value={reason} maxLength={300} onChange={(e) => setReason(e.target.value)} placeholder="Ej. Afecta a todas las aulas" /></label>
                </div>
                <p className="muted">Los plazos se recalculan desde que se registró el ticket. El motivo queda en la auditoría.</p>
                <div className="row-actions"><Button variant="secondary" onClick={() => setReprioritizing(false)}>Cancelar</Button><Button disabled={busy || reason.trim().length < 5 || priority === ticket.priority} onClick={() => void run(() => changeTicketPriority(ticket.id, priority, reason), `${ticket.code}: prioridad ${priorityLabels[priority].text.toLowerCase()}`)}>Guardar prioridad</Button></div>
              </div>
            : <Button variant="ghost" onClick={() => setReprioritizing(true)}><ShieldAlert size={16} />Cambiar prioridad</Button>}
        </div>
      )}
      {error && <div className="error-note">{error}</div>}
    </Modal>
  )
}

export function ServiceDesk({ notify }: { notify: (message: string) => void }) {
  const { tickets, error, replace } = useTickets('tecnico')
  const [selected, setSelected] = useState<Ticket | null>(null)
  const [failure, setFailure] = useState('')

  const move = async (ticket: Ticket, direction: number) => {
    const next = statusOrder[Math.max(1, Math.min(statusOrder.length - 1, statusOrder.indexOf(ticket.status) + direction))]
    try {
      const updated = await changeTicketStatus(ticket.id, next)
      replace(updated)
      setFailure('')
      notify(`${ticket.code} movido a ${statusLabels[next].text}`)
    } catch (e) {
      setFailure(errorText(e))
    }
  }

  const waiting = tickets.filter((item) => item.status === 'NEW').length
  const breached = tickets.filter((item) => item.status !== 'RESOLVED' && (item.firstResponseSla === 'BREACHED' || item.resolutionSla === 'BREACHED')).length
  const critical = tickets.filter((item) => item.status !== 'RESOLVED' && item.priority === 'CRITICAL').length

  return (
    <>
      <PageHeader eyebrow="Centro de atención" title="Service Desk" description="Prioriza y resuelve el trabajo del equipo; los tickets más urgentes van primero." />
      <div className="desk-summary"><span><i className="dot blue" />{waiting} sin atender</span><span><i className="dot pink" />{critical} críticos</span><span><i className="dot lavender" />{breached} con plazo vencido</span></div>
      {(error || failure) && <div className="error-note">{error || failure}</div>}
      <div className="kanban">{statusOrder.map((status) => {
        const column = tickets.filter((item) => item.status === status)
          .sort((a, b) => Object.keys(priorityLabels).indexOf(a.priority) - Object.keys(priorityLabels).indexOf(b.priority) || a.resolutionDue.localeCompare(b.resolutionDue))
        return (
          <div className="kanban-column" key={status}>
            <div className="kanban-head"><div><i className={`dot ${status === 'NEW' ? 'blue' : status === 'RESOLVED' ? 'mint' : status === 'ON_HOLD' ? 'pink' : 'lavender'}`} /><strong>{statusLabels[status].text}</strong><span>{column.length}</span></div></div>
            <div className="kanban-list">{column.map((ticket) => (
              <Card className="kanban-card" key={ticket.id} onClick={() => setSelected(ticket)}>
                <div className="kanban-card-top"><Badge tone={priorityLabels[ticket.priority].tone}>{priorityLabels[ticket.priority].text}</Badge><span>{ticket.code}</span></div>
                <h3>{ticket.title}</h3><p>{ticket.service.name}</p>
                <div className="kanban-footer">
                  <span className={`sla-${ticket.resolutionSla.toLowerCase()}`}><Clock3 size={13} />{ticket.status === 'RESOLVED' ? slaLabels[ticket.resolutionSla].text : `Vence ${formatDateTime(ticket.resolutionDue)}`}</span>
                  <div onClick={(event) => event.stopPropagation()}>
                    <Button variant="ghost" className="icon-btn" aria-label="Mover a la izquierda" onClick={() => void move(ticket, -1)} disabled={status === 'NEW' || status === 'IN_PROGRESS'}><ArrowLeft size={14} /></Button>
                    <Button variant="ghost" className="icon-btn" aria-label="Mover a la derecha" onClick={() => void move(ticket, 1)} disabled={status === 'RESOLVED'}><ArrowRight size={14} /></Button>
                  </div>
                </div>
              </Card>
            ))}</div>
          </div>
        )
      })}</div>
      {selected && <TicketDetail ticket={selected} staff onClose={() => setSelected(null)} onChanged={(ticket, message) => { replace(ticket); setSelected(ticket); notify(message) }} />}
    </>
  )
}

/** Indicadores del Resumen calculados con los tickets reales. */
export function useTicketStats(tickets: Ticket[]) {
  return useMemo(() => {
    const open = tickets.filter((item) => item.status !== 'RESOLVED')
    const resolved = tickets.filter((item) => item.status === 'RESOLVED')
    const met = resolved.filter((item) => item.resolutionSla === 'MET').length
    const months = Array.from({ length: 6 }, (_, index) => {
      const date = new Date()
      date.setDate(1)
      date.setMonth(date.getMonth() - (5 - index))
      const key = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
      return {
        month: date.toLocaleDateString('es-PE', { month: 'short' }).replace('.', ''),
        opened: tickets.filter((item) => item.createdAt.startsWith(key)).length,
        resolved: resolved.filter((item) => item.resolvedAt?.startsWith(key)).length,
      }
    })
    return {
      open: open.length,
      resolved: resolved.length,
      waiting: tickets.filter((item) => item.status === 'NEW').length,
      atRisk: open.filter((item) => item.resolutionSla === 'AT_RISK' || item.resolutionSla === 'BREACHED').length,
      breachedOpen: open.filter((item) => item.resolutionSla === 'BREACHED').length,
      compliance: resolved.length === 0 ? null : Math.round((met / resolved.length) * 1000) / 10,
      met,
      missed: resolved.length - met,
      months,
    }
  }, [tickets])
}

export function SlaConfigPanel({ notify }: { notify: (message: string) => void }) {
  const [policies, setPolicies] = useState<SlaPolicy[]>([])
  const [drafts, setDrafts] = useState<Record<string, { first: string; resolution: string }>>({})
  const [error, setError] = useState('')

  const load = () => listSlaPolicies().then((list) => {
    setPolicies(list)
    setDrafts(Object.fromEntries(list.map((item) => [item.priority, { first: String(item.firstResponseMinutes), resolution: String(item.resolutionMinutes) }])))
  }).catch((e) => setError(errorText(e)))
  useEffect(() => { void load() }, [])

  const save = async (policy: SlaPolicy) => {
    const draft = drafts[policy.priority]
    try {
      await updateSlaPolicy(policy.priority, { firstResponseMinutes: Number(draft.first), resolutionMinutes: Number(draft.resolution), version: policy.version })
      setError('')
      notify(`SLA de prioridad ${priorityLabels[policy.priority].text.toLowerCase()} actualizado`)
      await load()
    } catch (e) {
      setError(errorText(e))
    }
  }

  return (
    <>
      <div className="notice-note"><CircleAlert size={17} />Minutos de horario de atención (lunes a sábado, 7:00 a 21:00). Un cambio se aplica a los tickets nuevos; los registrados conservan sus plazos.</div>
      {error && <div className="error-note">{error}</div>}
      <div className="sla-config">{policies.map((policy) => {
        const draft = drafts[policy.priority] ?? { first: '', resolution: '' }
        return (
          <Card key={policy.priority}>
            <div><Badge tone={priorityLabels[policy.priority].tone}>{priorityLabels[policy.priority].text}</Badge><h3>Prioridad {priorityLabels[policy.priority].text.toLowerCase()}</h3></div>
            <label className="field-label">Primera respuesta (min)<Input type="number" min={1} value={draft.first} onChange={(e) => setDrafts({ ...drafts, [policy.priority]: { ...draft, first: e.target.value } })} /></label>
            <label className="field-label">Resolución (min)<Input type="number" min={1} value={draft.resolution} onChange={(e) => setDrafts({ ...drafts, [policy.priority]: { ...draft, resolution: e.target.value } })} /></label>
            <span className="muted">{formatMinutes(Number(draft.first) || 0)} · {formatMinutes(Number(draft.resolution) || 0)}</span>
            <Button variant="secondary" onClick={() => void save(policy)}>Guardar</Button>
          </Card>
        )
      })}</div>
    </>
  )
}

export function DashboardTickets({ tickets }: { tickets: Ticket[] }) {
  return <TicketTable tickets={tickets.slice(0, 5)} />
}

