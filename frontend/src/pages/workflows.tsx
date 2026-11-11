import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { Check, CheckCircle2, ClipboardCheck, Hand, KeyRound, LockKeyhole, Plus, Search, X } from 'lucide-react'
import { Badge, Button, Card, Input, Modal, PageHeader, Select, Textarea } from '../components/ui'
import type { Role } from '../types'
import { listPublishedServices, type CatalogService } from '../services/catalogService'
import { formatDate } from '../services/academicService'
import { formatDateTime } from '../services/ticketService'
import {
  accessStatus, allAccessRequests, allServiceRequests, cancelAccessRequest, cancelServiceRequest, createServiceRequest,
  decideAccessRequest, decideServiceRequest, fulfillServiceRequest, levelLabels, listAccessResources, myAccessRequests,
  myServiceRequests, requestAccess, requestStatusLabels, revokeAccess, takeServiceRequest,
  type AccessLevel, type AccessRequest, type AccessResource, type RequestStatus, type ServiceRequest,
} from '../services/workflowService'

const errorText = (error: unknown) => (error instanceof Error ? error.message : 'No se pudo completar la operación.')
const isStaff = (role: Role) => role === 'tecnico' || role === 'administrador'

/** Pide un texto antes de confirmar (motivo de rechazo, nota de atención, motivo de revocación). */
function NoteModal({ title, label, hint, required, confirm, tone = 'primary', onClose, onConfirm }: {
  title: string; label: string; hint?: string; required: boolean; confirm: string; tone?: 'primary' | 'danger'
  onClose: () => void; onConfirm: (note: string) => Promise<void>
}) {
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const submit = async () => {
    if (required && note.trim().length < 5) { setError('Escribe al menos 5 caracteres.'); return }
    setBusy(true)
    try { await onConfirm(note.trim()); onClose() } catch (e) { setError(errorText(e)) } finally { setBusy(false) }
  }
  return (
    <Modal open onClose={onClose} title={title} footer={<><Button variant="secondary" onClick={onClose}>Volver</Button><Button variant={tone} onClick={() => void submit()} disabled={busy}>{busy ? 'Guardando…' : confirm}</Button></>}>
      <label className="field-label">{label}<Textarea rows={3} maxLength={1000} value={note} onChange={(e) => setNote(e.target.value)} />{hint && <span className="field-hint">{hint}</span>}</label>
      {error && <div className="error-note">{error}</div>}
    </Modal>
  )
}

function Detail({ label, children }: { label: string; children: ReactNode }) {
  return <div><span>{label}</span><strong>{children}</strong></div>
}

/* ---------------- Solicitudes de servicio ---------------- */

const requestFilters: { key: 'ALL' | RequestStatus; text: string }[] = [
  { key: 'ALL', text: 'Todas' }, { key: 'PENDING', text: 'Pendientes' }, { key: 'APPROVED', text: 'Aprobadas' },
  { key: 'IN_PROGRESS', text: 'En atención' }, { key: 'FULFILLED', text: 'Atendidas' }, { key: 'REJECTED', text: 'Rechazadas' },
]

export function ServiceRequestsPage({ role, email, notify }: { role: Role; email: string; notify: (message: string) => void }) {
  const staff = isStaff(role)
  const admin = role === 'administrador'
  const [items, setItems] = useState<ServiceRequest[]>([])
  const [filter, setFilter] = useState<'ALL' | RequestStatus>('ALL')
  const [error, setError] = useState('')
  const [creating, setCreating] = useState(false)
  const [selected, setSelected] = useState<ServiceRequest | null>(null)
  const [prompt, setPrompt] = useState<{ kind: 'reject' | 'fulfill'; item: ServiceRequest } | null>(null)

  const load = () => (staff ? allServiceRequests() : myServiceRequests()).then((page) => { setItems(page.content); setError('') }).catch((e) => setError(errorText(e)))
  useEffect(() => { void load() }, [role])

  const act = async (action: () => Promise<ServiceRequest>, message: string) => {
    try { const updated = await action(); setItems((list) => list.map((item) => (item.id === updated.id ? updated : item))); setSelected(null); notify(message) } catch (e) { setError(errorText(e)) }
  }

  const list = items.filter((item) => filter === 'ALL' || item.status === filter)

  return (
    <>
      <PageHeader eyebrow="Cumplimiento de solicitudes" title={staff ? 'Solicitudes de servicio' : 'Mis solicitudes'} description="Pide algo nuevo del catálogo (no una falla): la Jefatura de TI lo aprueba y el equipo técnico lo atiende." action={<Button onClick={() => setCreating(true)}><Plus size={17} />Nueva solicitud</Button>} />
      <div className="workflow-tabs">{requestFilters.map((item) => <Button key={item.key} variant={filter === item.key ? 'primary' : 'secondary'} onClick={() => setFilter(item.key)}>{item.text}</Button>)}</div>
      {error && <div className="error-note">{error}</div>}
      <Card className="table-card"><div className="table-scroll"><table>
        <thead><tr><th>Solicitud</th><th>Servicio</th>{staff && <th>Solicitante</th>}<th>Fecha requerida</th><th>Estado</th><th>Acciones</th></tr></thead>
        <tbody>{list.map((item) => {
          const own = item.requesterEmail === email
          return (
            <tr key={item.id} className="clickable" onClick={() => setSelected(item)}>
              <td><strong>{item.code}</strong><span>{item.title}</span></td>
              <td>{item.serviceName}</td>
              {staff && <td>{item.requesterName}</td>}
              <td>{item.neededBy ? formatDate(item.neededBy) : '—'}{item.overdue && <span className="email-warning">Fecha vencida</span>}</td>
              <td><Badge tone={requestStatusLabels[item.status].tone}>{requestStatusLabels[item.status].text}</Badge></td>
              <td onClick={(event) => event.stopPropagation()}><div className="row-actions">
                {admin && item.status === 'PENDING' && <><Button variant="ghost" onClick={() => void act(() => decideServiceRequest(item.id, true), `${item.code} aprobada`)}><Check size={16} />Aprobar</Button><Button variant="ghost" onClick={() => setPrompt({ kind: 'reject', item })}><X size={16} />Rechazar</Button></>}
                {staff && item.status === 'APPROVED' && <Button variant="ghost" onClick={() => void act(() => takeServiceRequest(item.id), `${item.code} asignada a ti`)}><Hand size={16} />Tomar</Button>}
                {staff && item.status === 'IN_PROGRESS' && <Button variant="ghost" onClick={() => setPrompt({ kind: 'fulfill', item })}><CheckCircle2 size={16} />Marcar atendida</Button>}
                {own && item.status === 'PENDING' && <Button variant="ghost" onClick={() => void act(() => cancelServiceRequest(item.id), `${item.code} cancelada`)}>Cancelar</Button>}
              </div></td>
            </tr>
          )
        })}</tbody>
      </table>{list.length === 0 && <div className="empty-state"><ClipboardCheck size={24} /><strong>Sin solicitudes</strong><span>{staff ? 'No hay solicitudes en este estado.' : 'Usa "Nueva solicitud" para pedir un servicio.'}</span></div>}</div></Card>
      {creating && <CreateRequestModal onClose={() => setCreating(false)} onCreated={(item) => { notify(`${item.code} registrada; queda pendiente de aprobación`); void load() }} />}
      {selected && (
        <Modal open onClose={() => setSelected(null)} title={`${selected.code} · ${selected.title}`} footer={<Button variant="secondary" onClick={() => setSelected(null)}>Cerrar</Button>}>
          <div className="detail-grid">
            <Detail label="Estado"><Badge tone={requestStatusLabels[selected.status].tone}>{requestStatusLabels[selected.status].text}</Badge></Detail>
            <Detail label="Servicio">{selected.serviceName}</Detail>
            <Detail label="Solicitante">{selected.requesterName}</Detail>
            <Detail label="Registrada">{formatDateTime(selected.createdAt)}</Detail>
            <Detail label="Fecha requerida">{selected.neededBy ? formatDate(selected.neededBy) : 'Sin fecha'}</Detail>
            <Detail label="Técnico">{selected.assigneeName ?? 'Sin asignar'}</Detail>
          </div>
          <p className="course-description"><strong>Justificación:</strong> {selected.justification}</p>
          {selected.decidedByName && <p className="course-description"><strong>{selected.status === 'REJECTED' ? 'Rechazada' : 'Aprobada'} por {selected.decidedByName}</strong>{selected.decidedAt ? ` el ${formatDateTime(selected.decidedAt)}` : ''}{selected.decisionNote ? `: ${selected.decisionNote}` : ''}</p>}
          {selected.fulfillmentNote && <p className="course-description"><strong>Atendida{selected.fulfilledAt ? ` el ${formatDateTime(selected.fulfilledAt)}` : ''}:</strong> {selected.fulfillmentNote}</p>}
        </Modal>
      )}
      {prompt?.kind === 'reject' && <NoteModal title={`Rechazar ${prompt.item.code}`} label="Motivo del rechazo" hint="El solicitante verá este motivo." required confirm="Rechazar" tone="danger" onClose={() => setPrompt(null)} onConfirm={(note) => act(() => decideServiceRequest(prompt.item.id, false, note), `${prompt.item.code} rechazada`)} />}
      {prompt?.kind === 'fulfill' && <NoteModal title={`Atender ${prompt.item.code}`} label="¿Qué se entregó o realizó?" required confirm="Marcar atendida" onClose={() => setPrompt(null)} onConfirm={(note) => act(() => fulfillServiceRequest(prompt.item.id, note), `${prompt.item.code} atendida`)} />}
    </>
  )
}

function CreateRequestModal({ onClose, onCreated }: { onClose: () => void; onCreated: (item: ServiceRequest) => void }) {
  const [services, setServices] = useState<CatalogService[]>([])
  const [form, setForm] = useState({ serviceId: '', title: '', justification: '', neededBy: '' })
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  useEffect(() => { listPublishedServices().then((page) => { setServices(page.content); setForm((f) => ({ ...f, serviceId: page.content[0]?.id ?? '' })) }).catch((e) => setError(errorText(e))) }, [])

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setError('')
    try {
      onCreated(await createServiceRequest({ serviceId: form.serviceId, title: form.title, justification: form.justification, neededBy: form.neededBy || undefined }))
      onClose()
    } catch (e) { setError(errorText(e)) } finally { setSaving(false) }
  }

  return (
    <Modal open onClose={onClose} title="Nueva solicitud de servicio" footer={<><Button variant="secondary" onClick={onClose}>Cancelar</Button><Button type="submit" form="request-form" disabled={saving}>{saving ? 'Enviando…' : 'Enviar solicitud'}</Button></>}>
      <form id="request-form" className="account-form" onSubmit={submit}>
        <label className="field-label">Servicio<Select value={form.serviceId} onChange={(e) => setForm({ ...form, serviceId: e.target.value })}>{services.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</Select></label>
        <label className="field-label">¿Qué necesitas?<Input required maxLength={160} value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} placeholder="Ej. Instalar software de pronunciación en el laboratorio 3" /></label>
        <label className="field-label">Justificación<Textarea required rows={4} maxLength={2000} value={form.justification} onChange={(e) => setForm({ ...form, justification: e.target.value })} placeholder="Para qué se usará y a quiénes beneficia" /></label>
        <label className="field-label">Fecha requerida (opcional)<Input type="date" value={form.neededBy} onChange={(e) => setForm({ ...form, neededBy: e.target.value })} /></label>
        <p className="muted">Si algo dejó de funcionar, registra un ticket: las solicitudes son para pedidos nuevos.</p>
        {error && <div className="error-note">{error}</div>}
      </form>
    </Modal>
  )
}

/* ---------------- Gestión de accesos ---------------- */

export function AccessPage({ role, email, notify }: { role: Role; email: string; notify: (message: string) => void }) {
  const staff = isStaff(role)
  const admin = role === 'administrador'
  const [items, setItems] = useState<AccessRequest[]>([])
  const [filter, setFilter] = useState('Todos')
  const [query, setQuery] = useState('')
  const [error, setError] = useState('')
  const [creating, setCreating] = useState(false)
  const [prompt, setPrompt] = useState<{ kind: 'reject' | 'revoke'; item: AccessRequest } | null>(null)

  const load = () => (staff ? allAccessRequests() : myAccessRequests()).then((page) => { setItems(page.content); setError('') }).catch((e) => setError(errorText(e)))
  useEffect(() => { void load() }, [role])

  const act = async (action: () => Promise<AccessRequest>, message: string) => {
    try { const updated = await action(); setItems((list) => list.map((item) => (item.id === updated.id ? updated : item))); notify(message) } catch (e) { setError(errorText(e)) }
  }

  const list = items.filter((item) => (filter === 'Todos' || accessStatus(item).text === filter)
    && `${item.code} ${item.resourceName} ${item.requesterName}`.toLowerCase().includes(query.trim().toLowerCase()))
  const active = items.filter((item) => item.active).length

  return (
    <>
      <PageHeader eyebrow="Seguridad y permisos" title={staff ? 'Gestión de accesos' : 'Mis accesos'} description="Solicita permiso a un sistema; la Jefatura de TI lo concede, y lo revoca cuando ya no corresponde." action={<Button onClick={() => setCreating(true)}><Plus size={17} />Solicitar acceso</Button>} />
      {staff && <div className="desk-summary"><span><i className="dot mint" />{active} accesos vigentes</span><span><i className="dot blue" />{items.filter((item) => item.status === 'PENDING').length} por aprobar</span></div>}
      <Card className="toolbar"><div className="table-search"><Search size={17} /><Input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Buscar por código, recurso o persona" /></div><div className="toolbar-right"><Select value={filter} onChange={(e) => setFilter(e.target.value)}>{['Todos', 'Pendiente', 'Vigente', 'Vencido', 'Revocado', 'Rechazado', 'Cancelado'].map((item) => <option key={item}>{item}</option>)}</Select></div></Card>
      {error && <div className="error-note">{error}</div>}
      <Card className="table-card"><div className="table-scroll"><table>
        <thead><tr><th>Solicitud</th><th>Recurso</th><th>Nivel</th>{staff && <th>Solicitante</th>}<th>Vence</th><th>Estado</th><th>Acciones</th></tr></thead>
        <tbody>{list.map((item) => {
          const status = accessStatus(item)
          const own = item.requesterEmail === email
          return (
            <tr key={item.id}>
              <td><strong>{item.code}</strong><span>{formatDateTime(item.createdAt)}</span></td>
              <td>{item.resourceName}<span>{item.resourceOwner}</span></td>
              <td>{levelLabels[item.level]}</td>
              {staff && <td>{item.requesterName}<span>{item.requesterEmail}</span></td>}
              <td>{item.expiresOn ? formatDate(item.expiresOn) : 'Sin vencimiento'}</td>
              <td><Badge tone={status.tone}>{status.text}</Badge>{item.revokeReason && <span>{item.revokeReason}</span>}{item.status === 'REJECTED' && item.decisionNote && <span>{item.decisionNote}</span>}</td>
              <td><div className="row-actions">
                {admin && item.status === 'PENDING' && <><Button variant="ghost" onClick={() => void act(() => decideAccessRequest(item.id, true), `Acceso ${item.code} concedido`)}><Check size={16} />Conceder</Button><Button variant="ghost" onClick={() => setPrompt({ kind: 'reject', item })}><X size={16} />Rechazar</Button></>}
                {admin && item.active && <Button variant="danger" onClick={() => setPrompt({ kind: 'revoke', item })}><LockKeyhole size={15} />Revocar</Button>}
                {own && item.status === 'PENDING' && <Button variant="ghost" onClick={() => void act(() => cancelAccessRequest(item.id), `${item.code} cancelada`)}>Cancelar</Button>}
              </div></td>
            </tr>
          )
        })}</tbody>
      </table>{list.length === 0 && <div className="empty-state"><KeyRound size={24} /><strong>Sin accesos</strong><span>{staff ? 'No hay solicitudes que coincidan.' : 'Usa "Solicitar acceso" cuando necesites permiso a un sistema.'}</span></div>}</div></Card>
      {creating && <RequestAccessModal onClose={() => setCreating(false)} onCreated={(item) => { notify(`${item.code} registrada; queda pendiente de aprobación`); void load() }} />}
      {prompt?.kind === 'reject' && <NoteModal title={`Rechazar ${prompt.item.code}`} label="Motivo del rechazo" required confirm="Rechazar" tone="danger" onClose={() => setPrompt(null)} onConfirm={(note) => act(() => decideAccessRequest(prompt.item.id, false, note), `${prompt.item.code} rechazada`)} />}
      {prompt?.kind === 'revoke' && <NoteModal title={`Revocar acceso de ${prompt.item.requesterName}`} label="Motivo de la revocación" hint={`${prompt.item.resourceName} · ${levelLabels[prompt.item.level]}. Queda registrado en la auditoría.`} required confirm="Revocar acceso" tone="danger" onClose={() => setPrompt(null)} onConfirm={(note) => act(() => revokeAccess(prompt.item.id, note), `Acceso ${prompt.item.code} revocado`)} />}
    </>
  )
}

function RequestAccessModal({ onClose, onCreated }: { onClose: () => void; onCreated: (item: AccessRequest) => void }) {
  const [resources, setResources] = useState<AccessResource[]>([])
  const [form, setForm] = useState({ resourceId: '', level: 'READ' as AccessLevel, justification: '', expiresOn: '' })
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  useEffect(() => { listAccessResources().then((list) => { setResources(list); setForm((f) => ({ ...f, resourceId: list[0]?.id ?? '' })) }).catch((e) => setError(errorText(e))) }, [])
  const resource = resources.find((item) => item.id === form.resourceId)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setError('')
    try {
      onCreated(await requestAccess({ resourceId: form.resourceId, level: form.level, justification: form.justification, expiresOn: form.expiresOn || undefined }))
      onClose()
    } catch (e) { setError(errorText(e)) } finally { setSaving(false) }
  }

  return (
    <Modal open onClose={onClose} title="Solicitar acceso" footer={<><Button variant="secondary" onClick={onClose}>Cancelar</Button><Button type="submit" form="access-form" disabled={saving}>{saving ? 'Enviando…' : 'Enviar solicitud'}</Button></>}>
      <form id="access-form" className="account-form" onSubmit={submit}>
        <div className="form-grid">
          <label className="field-label">Sistema o recurso<Select value={form.resourceId} onChange={(e) => setForm({ ...form, resourceId: e.target.value })}>{resources.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</Select></label>
          <label className="field-label">Nivel de permiso<Select value={form.level} onChange={(e) => setForm({ ...form, level: e.target.value as AccessLevel })}>{(Object.keys(levelLabels) as AccessLevel[]).map((level) => <option key={level} value={level}>{levelLabels[level]}</option>)}</Select></label>
        </div>
        {resource && <p className="muted">{resource.description} Responsable: {resource.owner}.</p>}
        <label className="field-label">Justificación<Textarea required rows={4} maxLength={2000} value={form.justification} onChange={(e) => setForm({ ...form, justification: e.target.value })} placeholder="Para qué lo necesitas y durante cuánto tiempo" /></label>
        <label className="field-label">Vence el (opcional)<Input type="date" value={form.expiresOn} onChange={(e) => setForm({ ...form, expiresOn: e.target.value })} /><span className="field-hint">Recomendado para accesos temporales: al vencer, deja de estar vigente sin que nadie lo revoque.</span></label>
        {error && <div className="error-note">{error}</div>}
      </form>
    </Modal>
  )
}
