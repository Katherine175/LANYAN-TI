import { useEffect, useState, type FormEvent } from 'react'
import {
  Activity, ArrowRight, ChevronRight, Clock3, CreditCard, GraduationCap, Headphones, Mail,
  Monitor, Pencil, Projector, Search, Sparkles, Wifi, type LucideIcon,
} from 'lucide-react'
import { Badge, Button, Card, Input, Modal, PageHeader, Select, Textarea } from '../components/ui'
import type { RouteKey } from '../types'
import {
  changeOperationalStatus, createCategory, createService, formatMinutes, lifecycleLabels, listAllServices, listCategories,
  listPublishedServices, operationalLabels, updateService,
  type CatalogService, type Category, type OperationalStatus, type ServiceIcon, type ServiceInput, type ServiceLifecycle,
} from '../services/catalogService'
import { priorityLabels, type Priority } from '../services/ticketService'

const serviceIcons: Record<ServiceIcon, LucideIcon> = {
  MONITOR: Monitor, GRADUATION: GraduationCap, CREDIT: CreditCard, MAIL: Mail, WIFI: Wifi, PROJECTOR: Projector, HEADSET: Headphones,
}
const iconNames: Record<ServiceIcon, string> = {
  MONITOR: 'Computadora', GRADUATION: 'Académico', CREDIT: 'Pagos', MAIL: 'Correo', WIFI: 'Conectividad', PROJECTOR: 'Audiovisual', HEADSET: 'Soporte',
}
const tones = ['lavender', 'blue', 'mint', 'pink']
const errorText = (error: unknown) => (error instanceof Error ? error.message : 'No se pudo completar la operación.')

export function Catalog({ canOperate, notify, onNavigate }: { canOperate: boolean; notify: (message: string) => void; onNavigate: (route: RouteKey) => void }) {
  const [items, setItems] = useState<CatalogService[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [query, setQuery] = useState('')
  const [category, setCategory] = useState('Todos')
  const [selected, setSelected] = useState<CatalogService | null>(null)

  useEffect(() => {
    listPublishedServices().then((page) => setItems(page.content)).catch((e) => setError(errorText(e))).finally(() => setLoading(false))
  }, [])

  const categories = ['Todos', ...new Set(items.map((service) => service.category.name))]
  const text = query.trim().toLowerCase()
  const filtered = items.filter((service) => (category === 'Todos' || service.category.name === category) && `${service.name} ${service.description}`.toLowerCase().includes(text))

  const changeStatus = async (status: OperationalStatus) => {
    if (!selected) return
    try {
      const updated = await changeOperationalStatus(selected.id, status, selected.version)
      setItems(items.map((service) => (service.id === updated.id ? updated : service)))
      setSelected(updated)
      setError('')
      notify('Estado operativo actualizado')
    } catch (e) {
      setError(errorText(e))
    }
  }

  const SelectedIcon = selected ? serviceIcons[selected.icon] : null

  return (
    <>
      <PageHeader eyebrow="Servicios para ti" title="Catálogo de servicios TI" description="Encuentra ayuda, solicita un servicio o reporta una interrupción." />
      <div className="catalog-hero"><div><Sparkles size={21} /><h2>¿Qué necesitas resolver hoy?</h2><p>Busca por servicio, problema o palabra clave.</p></div><div className="hero-search"><Search size={19} /><Input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Ej. No puedo ingresar al aula virtual" /></div></div>
      <div className="filter-pills">{categories.map((item) => <Button key={item} variant={category === item ? 'primary' : 'secondary'} onClick={() => setCategory(item)}>{item}</Button>)}</div>
      {error && !selected && <div className="error-note">{error}</div>}
      <div className="service-grid">{filtered.map((service, index) => {
        const Icon = serviceIcons[service.icon]
        const status = operationalLabels[service.operationalStatus]
        return <Card className="service-card" key={service.id} onClick={() => { setError(''); setSelected(service) }}><div className={`service-icon icon-${tones[index % 4]}`}><Icon size={24} /></div><Badge>{service.category.name}</Badge><h3>{service.name}</h3><p>{service.description}</p><div className="service-meta"><span><Activity size={14} />{status.text}</span><span><Clock3 size={14} />Respuesta en {formatMinutes(service.responseTargetMinutes)}</span><span>Prioridad {priorityLabels[service.defaultPriority].text.toLowerCase()}</span></div><Button variant="ghost">Ver opciones <ChevronRight size={16} /></Button></Card>
      })}</div>
      {!loading && filtered.length === 0 && <div className="empty-state"><Search size={24} /><strong>Sin resultados</strong><span>Prueba con otra búsqueda.</span></div>}
      <Modal open={!!selected} onClose={() => setSelected(null)} title={selected?.name || ''} footer={<><Button variant="secondary" onClick={() => setSelected(null)}>Cerrar</Button><Button onClick={() => { setSelected(null); onNavigate('tickets') }}>Ir a tickets <ArrowRight size={16} /></Button></>}>
        {selected && SelectedIcon && <>
          <div className="detail-callout"><div className="service-icon icon-lavender"><SelectedIcon size={24} /></div><div><strong>{selected.description}</strong><p>{selected.code} · Responsable: {selected.owner}</p></div></div>
          <div className="detail-grid">
            <div><span>Estado actual</span><Badge tone={operationalLabels[selected.operationalStatus].tone}>{operationalLabels[selected.operationalStatus].text}</Badge></div>
            <div><span>Disponibilidad objetivo</span><strong>{selected.availabilityTarget}%</strong></div>
            <div><span>Respuesta objetivo</span><strong>{formatMinutes(selected.responseTargetMinutes)}</strong></div>
          </div>
          {canOperate && <label className="field-label">Estado operativo<Select name="operational" value={selected.operationalStatus} onChange={(e) => void changeStatus(e.target.value as OperationalStatus)}>{(Object.keys(operationalLabels) as OperationalStatus[]).map((status) => <option key={status} value={status}>{operationalLabels[status].text}</option>)}</Select></label>}
          {error && <div className="error-note">{error}</div>}
        </>}
      </Modal>
    </>
  )
}

type BasePriority = Exclude<Priority, 'CRITICAL'>
type FormState = { name: string; description: string; categoryId: string; newCategory: string; owner: string; icon: ServiceIcon; lifecycleStatus: ServiceLifecycle; availabilityTarget: string; defaultPriority: BasePriority }

const NEW_CATEGORY = '__nueva__'
const blank = (categories: Category[]): FormState => ({
  name: '', description: '', categoryId: categories[0]?.id ?? NEW_CATEGORY, newCategory: '', owner: 'Service Desk TI', icon: 'HEADSET',
  lifecycleStatus: 'DRAFT', availabilityTarget: '99', defaultPriority: 'MEDIUM',
})
const fromService = (service: CatalogService): FormState => ({
  name: service.name, description: service.description, categoryId: service.category.id, newCategory: '', owner: service.owner, icon: service.icon,
  lifecycleStatus: service.lifecycleStatus, availabilityTarget: String(service.availabilityTarget), defaultPriority: service.defaultPriority === 'CRITICAL' ? 'HIGH' : service.defaultPriority,
})

export function ServicesPanel({ creating, onCloseCreate, notify }: { creating: boolean; onCloseCreate: () => void; notify: (message: string) => void }) {
  const [items, setItems] = useState<CatalogService[]>([])
  const [categories, setCategories] = useState<Category[]>([])
  const [editing, setEditing] = useState<CatalogService | null>(null)
  const [form, setForm] = useState<FormState>(blank([]))
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  const load = () => Promise.all([listAllServices(), listCategories()]).then(([page, list]) => { setItems(page.content); setCategories(list) }).catch((e) => setError(errorText(e)))
  useEffect(() => { void load() }, [])

  useEffect(() => { if (creating) { setError(''); setForm(blank(categories)) } }, [creating])

  const open = creating || editing !== null
  const close = () => { setEditing(null); setError(''); onCloseCreate() }
  const startEdit = (service: CatalogService) => { setError(''); setForm(fromService(service)); setEditing(service) }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setError('')
    try {
      const categoryId = form.categoryId === NEW_CATEGORY ? (await createCategory(form.newCategory)).id : form.categoryId
      const body: ServiceInput = {
        name: form.name, description: form.description, categoryId, owner: form.owner, icon: form.icon, lifecycleStatus: form.lifecycleStatus,
        availabilityTarget: Number(form.availabilityTarget), defaultPriority: form.defaultPriority,
      }
      const saved = editing ? await updateService(editing.id, { ...body, version: editing.version }) : await createService(body)
      notify(`${saved.code} guardado`)
      close()
      await load()
    } catch (e) {
      setError(errorText(e))
      void load()
    } finally {
      setSaving(false)
    }
  }

  const set = (patch: Partial<FormState>) => setForm({ ...form, ...patch })

  return (
    <>
      <Card className="table-card">
        {error && !open && <div className="error-note">{error}</div>}
        <div className="table-scroll">
          <table>
            <thead><tr><th>Servicio</th><th>Categoría</th><th>Disponibilidad objetivo</th><th>Prioridad base</th><th>Publicación</th><th>Estado</th><th /></tr></thead>
            <tbody>
              {items.map((service) => (
                <tr key={service.id}>
                  <td><strong>{service.name}</strong><span>{service.code}</span></td>
                  <td>{service.category.name}</td>
                  <td>{service.availabilityTarget}%</td>
                  <td><Badge tone={priorityLabels[service.defaultPriority].tone}>{priorityLabels[service.defaultPriority].text}</Badge><span>Respuesta en {formatMinutes(service.responseTargetMinutes)}</span></td>
                  <td><Badge tone={lifecycleLabels[service.lifecycleStatus].tone}>{lifecycleLabels[service.lifecycleStatus].text}</Badge></td>
                  <td><Badge tone={operationalLabels[service.operationalStatus].tone}>{operationalLabels[service.operationalStatus].text}</Badge></td>
                  <td><Button variant="ghost" className="icon-btn" aria-label={`Editar ${service.name}`} onClick={() => startEdit(service)}><Pencil size={16} /></Button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
      <Modal open={open} onClose={close} title={editing ? `Editar ${editing.code}` : 'Añadir servicio'} footer={<><Button variant="secondary" onClick={close}>Cancelar</Button><Button type="submit" form="service-form" disabled={saving}>{saving ? 'Guardando…' : 'Guardar'}</Button></>}>
        <form id="service-form" onSubmit={submit}>
          <label className="field-label">Nombre<Input name="name" required maxLength={120} value={form.name} onChange={(e) => set({ name: e.target.value })} /></label>
          <label className="field-label">Descripción<Textarea name="description" required rows={3} maxLength={1000} value={form.description} onChange={(e) => set({ description: e.target.value })} /></label>
          <div className="form-grid">
            <label className="field-label">Categoría<Select value={form.categoryId} onChange={(e) => set({ categoryId: e.target.value })}>{categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}<option value={NEW_CATEGORY}>+ Nueva categoría</option></Select></label>
            {form.categoryId === NEW_CATEGORY
              ? <label className="field-label">Nombre de la categoría<Input name="newCategory" required maxLength={80} value={form.newCategory} onChange={(e) => set({ newCategory: e.target.value })} /></label>
              : <label className="field-label">Responsable<Input name="owner" required maxLength={120} value={form.owner} onChange={(e) => set({ owner: e.target.value })} /></label>}
          </div>
          {form.categoryId === NEW_CATEGORY && <label className="field-label">Responsable<Input name="owner" required maxLength={120} value={form.owner} onChange={(e) => set({ owner: e.target.value })} /></label>}
          <div className="form-grid">
            <label className="field-label">Ícono<Select value={form.icon} onChange={(e) => set({ icon: e.target.value as ServiceIcon })}>{(Object.keys(iconNames) as ServiceIcon[]).map((icon) => <option key={icon} value={icon}>{iconNames[icon]}</option>)}</Select></label>
            <label className="field-label">Publicación<Select name="lifecycle" value={form.lifecycleStatus} onChange={(e) => set({ lifecycleStatus: e.target.value as ServiceLifecycle })}>{(Object.keys(lifecycleLabels) as ServiceLifecycle[]).map((status) => <option key={status} value={status}>{lifecycleLabels[status].text}</option>)}</Select></label>
          </div>
          <div className="form-grid">
            <label className="field-label">Objetivo de disponibilidad (%)<Input name="availability" required type="number" min={0} max={100} step={0.01} value={form.availabilityTarget} onChange={(e) => set({ availabilityTarget: e.target.value })} /></label>
            <label className="field-label">Prioridad base de sus tickets<Select name="priority" value={form.defaultPriority} onChange={(e) => set({ defaultPriority: e.target.value as BasePriority })}><option value="HIGH">Alta</option><option value="MEDIUM">Media</option><option value="LOW">Baja</option></Select><span className="field-hint">Crítica solo la asigna un técnico en cada ticket. Los tiempos están en Configuración SLA.</span></label>
          </div>
          {error && <div className="error-note">{error}</div>}
        </form>
      </Modal>
    </>
  )
}
