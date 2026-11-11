import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { CheckCircle2, ChevronRight, CircleAlert, Lightbulb, Plus, RefreshCw, Search, TriangleAlert, UserCheck, X } from 'lucide-react'
import { Badge, Button, Card, Input, Modal, PageHeader, Select, Textarea } from '../components/ui'
import { listPublishedServices, type CatalogService } from '../services/catalogService'
import { allTickets, formatDateTime, priorityLabels, statusLabels, type Priority, type Ticket } from '../services/ticketService'
import {
  createProblem, documentKnownError, linkIncident, listProblems, problemCandidates, problemStatusLabels, resolveProblem, unlinkIncident,
  type Candidate, type Problem,
} from '../services/problemService'

const errorText = (error: unknown) => (error instanceof Error ? error.message : 'No se pudo completar la operación.')

export function ProblemsPage({ notify }: { notify: (message: string) => void }) {
  const [problems, setProblems] = useState<Problem[]>([])
  const [candidates, setCandidates] = useState<Candidate[]>([])
  const [tickets, setTickets] = useState<Ticket[]>([])
  const [error, setError] = useState('')
  const [creating, setCreating] = useState<{ serviceId?: string } | null>(null)
  const [selectedId, setSelectedId] = useState<string | null>(null)

  const load = () => Promise.all([listProblems(), problemCandidates(), allTickets()])
    .then(([list, found, page]) => { setProblems(list); setCandidates(found); setTickets(page.content); setError('') })
    .catch((e) => setError(errorText(e)))
  useEffect(() => { void load() }, [])

  const replace = (problem: Problem) => { setProblems((list) => list.map((item) => (item.id === problem.id ? problem : item))); void problemCandidates().then(setCandidates) }
  const active = problems.filter((item) => item.status !== 'RESOLVED')
  const selected = problems.find((item) => item.id === selectedId) ?? null

  return (
    <>
      <PageHeader eyebrow="Gestión de problemas" title="Problemas" description="Agrupa incidentes repetidos, encuentra la causa raíz y deja documentada la solución." action={<Button onClick={() => setCreating({})}><Plus size={17} />Nuevo problema</Button>} />
      <div className="stat-grid three">
        <Card className="mini-stat"><CircleAlert size={20} /><span>Problemas activos</span><strong>{active.length}</strong></Card>
        <Card className="mini-stat"><RefreshCw size={20} /><span>Incidentes vinculados</span><strong>{active.reduce((total, item) => total + item.incidents.length, 0)}</strong></Card>
        <Card className="mini-stat"><Lightbulb size={20} /><span>Errores conocidos</span><strong>{problems.filter((item) => item.status === 'KNOWN_ERROR').length}</strong></Card>
      </div>
      {error && <div className="error-note">{error}</div>}
      {candidates.length > 0 && (
        <Card className="candidates-card">
          <div className="card-title"><div><h2><TriangleAlert size={17} />Incidentes repetidos sin analizar</h2><p>Servicios con 3 o más incidentes en los últimos 30 días que todavía no tienen un problema asociado.</p></div></div>
          {candidates.map((item) => (
            <div className="candidate-row" key={item.serviceId}>
              <div><strong>{item.serviceName}</strong><span>{item.incidents} incidentes · último {formatDateTime(item.lastIncidentAt)}</span></div>
              <Button variant="secondary" onClick={() => setCreating({ serviceId: item.serviceId })}>Abrir problema</Button>
            </div>
          ))}
        </Card>
      )}
      <div className="problem-list">{problems.map((problem) => (
        <Card className="problem-card" key={problem.id} onClick={() => setSelectedId(problem.id)}>
          <div className="problem-id"><span>{problem.code}</span><Badge tone={problemStatusLabels[problem.status].tone}>{problemStatusLabels[problem.status].text}</Badge></div>
          <div><h3>{problem.title}</h3><p><strong>{problem.serviceName}</strong> · {problem.rootCause ? `Causa: ${problem.rootCause}` : 'Causa raíz en investigación'}</p></div>
          <div className="problem-meta"><span><RefreshCw size={15} />{problem.incidents.length} incidentes</span><span><UserCheck size={15} />{problem.ownerName}</span><ChevronRight size={18} /></div>
        </Card>
      ))}</div>
      {problems.length === 0 && !error && <div className="empty-state"><Search size={24} /><strong>Sin problemas registrados</strong><span>Cuando un mismo incidente se repite, ábrelo como problema para buscar su causa.</span></div>}
      {creating && <CreateProblemModal presetService={creating.serviceId} tickets={tickets} onClose={() => setCreating(null)} onCreated={(problem) => { notify(`${problem.code} registrado`); void load(); setSelectedId(problem.id) }} />}
      {selected && <ProblemDetail problem={selected} tickets={tickets} onClose={() => setSelectedId(null)} onChanged={(problem, message) => { replace(problem); notify(message); if (problem.status === 'RESOLVED') void allTickets().then((page) => setTickets(page.content)) }} />}
    </>
  )
}

function CreateProblemModal({ presetService, tickets, onClose, onCreated }: { presetService?: string; tickets: Ticket[]; onClose: () => void; onCreated: (problem: Problem) => void }) {
  const [services, setServices] = useState<CatalogService[]>([])
  const [form, setForm] = useState({ title: '', description: '', serviceId: presetService ?? '', priority: 'MEDIUM' as Priority })
  const [chosen, setChosen] = useState<string[]>([])
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    listPublishedServices().then((page) => {
      setServices(page.content)
      setForm((f) => {
        const serviceId = f.serviceId || page.content[0]?.id || ''
        const service = page.content.find((item) => item.id === serviceId)
        return { ...f, serviceId, priority: service && service.defaultPriority !== 'CRITICAL' ? service.defaultPriority : f.priority }
      })
    }).catch((e) => setError(errorText(e)))
  }, [])

  // Solo incidentes del mismo servicio que aún no pertenecen a otro problema.
  const available = useMemo(() => tickets.filter((item) => item.service.id === form.serviceId && !item.problemCode), [tickets, form.serviceId])
  useEffect(() => { setChosen(available.filter((item) => item.status !== 'RESOLVED').map((item) => item.id)) }, [available])

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setError('')
    try { onCreated(await createProblem({ ...form, ticketIds: chosen })); onClose() } catch (e) { setError(errorText(e)) } finally { setSaving(false) }
  }

  return (
    <Modal open onClose={onClose} title="Nuevo problema" footer={<><Button variant="secondary" onClick={onClose}>Cancelar</Button><Button type="submit" form="problem-form" disabled={saving}>{saving ? 'Guardando…' : 'Registrar problema'}</Button></>}>
      <form id="problem-form" className="account-form" onSubmit={submit}>
        <label className="field-label">Título<Input required maxLength={160} value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} placeholder="Ej. El aula virtual se cae en horas punta" /></label>
        <div className="form-grid">
          <label className="field-label">Servicio afectado<Select value={form.serviceId} onChange={(e) => setForm({ ...form, serviceId: e.target.value })}>{services.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</Select></label>
          <label className="field-label">Prioridad<Select value={form.priority} onChange={(e) => setForm({ ...form, priority: e.target.value as Priority })}>{(Object.keys(priorityLabels) as Priority[]).map((item) => <option key={item} value={item}>{priorityLabels[item].text}</option>)}</Select></label>
        </div>
        <label className="field-label">Síntoma<Textarea required rows={3} maxLength={4000} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} placeholder="Qué observan los usuarios y desde cuándo" /></label>
        <fieldset className="incident-picker">
          <legend>Incidentes a vincular ({chosen.length})</legend>
          {available.length === 0 && <p className="muted">No hay incidentes libres de este servicio.</p>}
          {available.map((item) => (
            <label key={item.id}>
              <input type="checkbox" checked={chosen.includes(item.id)} onChange={() => setChosen(chosen.includes(item.id) ? chosen.filter((id) => id !== item.id) : [...chosen, item.id])} />
              <strong>{item.code}</strong><span>{item.title}</span><Badge tone={statusLabels[item.status].tone}>{statusLabels[item.status].text}</Badge>
            </label>
          ))}
        </fieldset>
        {error && <div className="error-note">{error}</div>}
      </form>
    </Modal>
  )
}

function ProblemDetail({ problem, tickets, onClose, onChanged }: { problem: Problem; tickets: Ticket[]; onClose: () => void; onChanged: (problem: Problem, message: string) => void }) {
  const [diagnosis, setDiagnosis] = useState({ rootCause: problem.rootCause ?? '', workaround: problem.workaround ?? '' })
  const [solution, setSolution] = useState('')
  const [closeIncidents, setCloseIncidents] = useState(true)
  const [toLink, setToLink] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const run = async (action: () => Promise<Problem>, message: string) => {
    setBusy(true)
    setError('')
    try { onChanged(await action(), message) } catch (e) { setError(errorText(e)) } finally { setBusy(false) }
  }

  const linkable = tickets.filter((item) => item.service.id === problem.serviceId && !item.problemCode && !problem.incidents.some((incident) => incident.id === item.id))
  const open = problem.incidents.filter((item) => item.status !== 'RESOLVED').length
  const resolved = problem.status === 'RESOLVED'

  return (
    <Modal open onClose={onClose} title={`${problem.code} · ${problem.title}`} footer={<Button variant="secondary" onClick={onClose}>Cerrar</Button>}>
      <div className="detail-grid">
        <div><span>Estado</span><Badge tone={problemStatusLabels[problem.status].tone}>{problemStatusLabels[problem.status].text}</Badge></div>
        <div><span>Prioridad</span><Badge tone={priorityLabels[problem.priority].tone}>{priorityLabels[problem.priority].text}</Badge></div>
        <div><span>Servicio</span><strong>{problem.serviceName}</strong></div>
        <div><span>Responsable</span><strong>{problem.ownerName}</strong></div>
      </div>
      <p className="course-description"><strong>Síntoma:</strong> {problem.description}</p>

      <h3 className="course-subtitle">Incidentes vinculados ({problem.incidents.length})</h3>
      <ul className="material-list">{problem.incidents.map((item) => (
        <li key={item.id}><RefreshCw size={15} /><div><strong>{item.code} · {item.title}</strong><span>{formatDateTime(item.createdAt)}</span></div><Badge tone={statusLabels[item.status].tone}>{statusLabels[item.status].text}</Badge>
          {!resolved && <Button variant="ghost" className="icon-btn" aria-label={`Desvincular ${item.code}`} onClick={() => void run(() => unlinkIncident(problem.id, item.id), `${item.code} desvinculado`)}><X size={14} /></Button>}</li>
      ))}</ul>
      {!resolved && linkable.length > 0 && <div className="row-actions link-row"><Select value={toLink} onChange={(e) => setToLink(e.target.value)}><option value="">Vincular otro incidente del servicio…</option>{linkable.map((item) => <option key={item.id} value={item.id}>{item.code} · {item.title}</option>)}</Select><Button variant="secondary" disabled={!toLink || busy} onClick={() => void run(() => linkIncident(problem.id, toLink), 'Incidente vinculado').then(() => setToLink(''))}>Vincular</Button></div>}

      <h3 className="course-subtitle">1. Diagnóstico (error conocido)</h3>
      {resolved
        ? <p className="course-description"><strong>Causa raíz:</strong> {problem.rootCause}<br /><strong>Solución temporal:</strong> {problem.workaround}</p>
        : <div className="inline-form">
            <label className="field-label">Causa raíz<Textarea rows={2} maxLength={4000} value={diagnosis.rootCause} onChange={(e) => setDiagnosis({ ...diagnosis, rootCause: e.target.value })} placeholder="Por qué ocurre realmente" /></label>
            <label className="field-label">Solución temporal<Textarea rows={2} maxLength={4000} value={diagnosis.workaround} onChange={(e) => setDiagnosis({ ...diagnosis, workaround: e.target.value })} placeholder="Qué hace el técnico mientras no exista la solución definitiva" /><span className="field-hint">Los técnicos la verán al atender tickets de este servicio.</span></label>
            <div className="row-actions"><Button disabled={busy || !diagnosis.rootCause.trim() || !diagnosis.workaround.trim()} onClick={() => void run(() => documentKnownError(problem.id, diagnosis), `${problem.code}: error conocido documentado`)}>{problem.status === 'KNOWN_ERROR' ? 'Actualizar diagnóstico' : 'Registrar error conocido'}</Button></div>
          </div>}

      <h3 className="course-subtitle">2. Solución definitiva</h3>
      {resolved
        ? <div className="success-note"><CheckCircle2 size={17} />{problem.permanentSolution}{problem.resolvedAt ? ` · ${formatDateTime(problem.resolvedAt)}` : ''}</div>
        : problem.status !== 'KNOWN_ERROR'
          ? <p className="muted">Primero documenta la causa raíz y la solución temporal.</p>
          : <div className="inline-form">
              <label className="field-label">Qué se cambió para que no vuelva a ocurrir<Textarea rows={2} maxLength={4000} value={solution} onChange={(e) => setSolution(e.target.value)} /></label>
              {open > 0 && <label className="check-line"><input type="checkbox" checked={closeIncidents} onChange={(e) => setCloseIncidents(e.target.checked)} />Resolver también los {open} incidentes vinculados abiertos y avisar al solicitante con un comentario</label>}
              <div className="row-actions"><Button disabled={busy || solution.trim().length < 5} onClick={() => void run(() => resolveProblem(problem.id, { permanentSolution: solution, resolveLinkedTickets: closeIncidents }), `${problem.code} resuelto`)}>Marcar problema resuelto</Button></div>
            </div>}
      {error && <div className="error-note">{error}</div>}
    </Modal>
  )
}
