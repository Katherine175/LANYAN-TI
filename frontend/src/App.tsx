import { useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react'
import {
  Activity, ArrowLeft, ArrowRight, BarChart3, BookOpen, Check, CheckCircle2,
  ChevronDown, ChevronRight, CircleAlert, ClipboardCheck, Clock3, Gauge,
  Headphones, KeyRound, LayoutDashboard, Lightbulb, LockKeyhole, LogOut, Menu,
  MessageSquare, MoreHorizontal, Plus, RefreshCw, Search, Settings2,
  SlidersHorizontal, Sparkles, TicketCheck, TrendingUp, UserCheck, Users,
  Wrench, X, Zap, type LucideIcon,
} from 'lucide-react'
import { articles, monthlyData, problems, requests, services, tickets as initialTickets } from './data/mockData'
import type { Priority, Role, RouteKey, Ticket, TicketStatus, WorkflowRequest } from './types'
import { Badge, Button, Card, Input, Modal, PageHeader, Progress, Select, Textarea } from './components/ui'
import { Catalog, ServicesPanel } from './pages/catalog'
import { AuditPanel, RolesPanel, UsersPanel } from './pages/administration'
import { SESSION_EXPIRED_EVENT } from './services/api'
import { listPublishedServices } from './services/catalogService'
import { currentAccount, initials, login, logout, mainRoleLabel, portalRole, type Account } from './services/identityService'

const navItems: { id: RouteKey; label: string; icon: LucideIcon; roles: Role[] }[] = [
  { id: 'dashboard', label: 'Resumen', icon: LayoutDashboard, roles: ['usuario', 'tecnico', 'administrador'] },
  { id: 'catalogo', label: 'Catálogo TI', icon: Sparkles, roles: ['usuario', 'tecnico', 'administrador'] },
  { id: 'tickets', label: 'Tickets', icon: TicketCheck, roles: ['usuario', 'tecnico', 'administrador'] },
  { id: 'service-desk', label: 'Service Desk', icon: Headphones, roles: ['tecnico', 'administrador'] },
  { id: 'problemas', label: 'Problemas', icon: CircleAlert, roles: ['tecnico', 'administrador'] },
  { id: 'accesos', label: 'Accesos', icon: KeyRound, roles: ['usuario', 'tecnico', 'administrador'] },
  { id: 'solicitudes', label: 'Solicitudes', icon: ClipboardCheck, roles: ['usuario', 'tecnico', 'administrador'] },
  { id: 'operaciones', label: 'Operaciones TI', icon: Activity, roles: ['tecnico', 'administrador'] },
  { id: 'conocimiento', label: 'Conocimiento', icon: BookOpen, roles: ['usuario', 'tecnico', 'administrador'] },
  { id: 'reportes', label: 'Reportes', icon: BarChart3, roles: ['tecnico', 'administrador'] },
  { id: 'mejora', label: 'Mejora continua', icon: TrendingUp, roles: ['tecnico', 'administrador'] },
  { id: 'administracion', label: 'Administración', icon: Settings2, roles: ['administrador'] },
]

const statusTone = (status: string) => {
  if (['Resuelto', 'Aprobado', 'Cerrado', 'Operativo', 'Completado'].includes(status)) return 'mint'
  if (['Nuevo', 'Pendiente', 'Investigando'].includes(status)) return 'blue'
  if (['Crítica', 'Revocado', 'Vencido'].includes(status)) return 'danger'
  if (['Alta', 'En espera'].includes(status)) return 'pink'
  return 'lavender'
}

function App() {
  const [account, setAccount] = useState<Account | null>(null)
  const [checkingSession, setCheckingSession] = useState(true)
  const [route, setRoute] = useState<RouteKey>('dashboard')
  const [mobileMenu, setMobileMenu] = useState(false)
  const [ticketList, setTicketList] = useState<Ticket[]>(initialTickets)
  const [requestList, setRequestList] = useState<WorkflowRequest[]>(requests)
  const [toast, setToast] = useState('')

  useEffect(() => {
    const hash = window.location.hash.replace('#/', '') as RouteKey
    if (navItems.some((item) => item.id === hash)) setRoute(hash)
    const listener = () => {
      const next = window.location.hash.replace('#/', '') as RouteKey
      if (navItems.some((item) => item.id === next)) setRoute(next)
    }
    window.addEventListener('hashchange', listener)
    return () => window.removeEventListener('hashchange', listener)
  }, [])

  useEffect(() => {
    currentAccount().then(setAccount).catch(() => setAccount(null)).finally(() => setCheckingSession(false))
    const expired = () => setAccount(null)
    window.addEventListener(SESSION_EXPIRED_EVENT, expired)
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, expired)
  }, [])

  const navigate = (next: RouteKey) => {
    setRoute(next)
    setMobileMenu(false)
    window.location.hash = `/${next}`
  }

  const notify = (message: string) => {
    setToast(message)
    window.setTimeout(() => setToast(''), 2600)
  }

  const role = account ? portalRole(account) : null
  const allowed = role !== null && navItems.find((item) => item.id === route)?.roles.includes(role) === true

  useEffect(() => {
    if (role && !allowed) navigate('dashboard')
  }, [role, allowed])

  const closeSession = () => {
    logout().catch(() => undefined).finally(() => {
      setAccount(null)
      setRoute('dashboard')
      window.location.hash = ''
    })
  }

  if (checkingSession) return <div className="boot-screen"><Logo /></div>
  if (!account || !role) return <Login onLogin={setAccount} />

  const view: RouteKey = allowed ? route : 'dashboard'

  return (
    <div className="app-shell">
      <Sidebar role={role} route={view} onNavigate={navigate} open={mobileMenu} onClose={() => setMobileMenu(false)} onLogout={closeSession} />
      <div className="app-main">
        <Header account={account} onMenu={() => setMobileMenu(true)} />
        <main className="page-wrap">
          {view === 'dashboard' && <Dashboard account={account} onNavigate={navigate} tickets={ticketList} />}
          {view === 'catalogo' && <Catalog canOperate={role !== 'usuario'} notify={notify} onNavigate={navigate} />}
          {view === 'tickets' && <TicketsPage role={role} tickets={ticketList} setTickets={setTicketList} notify={notify} />}
          {view === 'service-desk' && <ServiceDesk tickets={ticketList} setTickets={setTicketList} notify={notify} />}
          {view === 'problemas' && <Problems notify={notify} />}
          {view === 'accesos' && <WorkflowPage mode="Acceso" items={requestList} setItems={setRequestList} notify={notify} />}
          {view === 'solicitudes' && <WorkflowPage mode="Servicio" items={requestList} setItems={setRequestList} notify={notify} />}
          {view === 'operaciones' && <Operations notify={notify} />}
          {view === 'conocimiento' && <Knowledge notify={notify} />}
          {view === 'reportes' && <Reports notify={notify} />}
          {view === 'mejora' && <Improvement notify={notify} />}
          {view === 'administracion' && <Administration currentId={account.id} notify={notify} />}
        </main>
      </div>
      {toast && <div className="toast"><CheckCircle2 size={18} />{toast}</div>}
    </div>
  )
}

function Login({ onLogin }: { onLogin: (account: Account) => void }) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [help, setHelp] = useState(false)
  const [busy, setBusy] = useState(false)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      onLogin(await login(email, password))
    } catch (e) {
      setPassword('')
      setError(e instanceof Error ? e.message : 'No se pudo iniciar sesión.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="login-layout">
      <section className="login-brand">
        <div className="ambient ambient-one" /><div className="ambient ambient-two" />
        <div className="brand-lockup"><Logo /><span>LANYAN TI</span></div>
        <div className="login-copy">
          <Badge tone="mint"><Sparkles size={13} /> Gestión TI · ITIL 4</Badge>
          <h1>Servicios que conectan a toda una comunidad.</h1>
          <p>La experiencia simple y humana para gestionar los servicios tecnológicos del Instituto de Idiomas.</p>
          <div className="login-metrics">
            <div><strong>ITIL 4</strong><span>Buenas prácticas</span></div>
            <div><strong>ISO/IEC 20000</strong><span>Procesos de referencia</span></div>
          </div>
        </div>
        <p className="login-foot">Universidad Nacional de Piura · Instituto de Idiomas</p>
      </section>
      <section className="login-panel">
        <form className="login-form" onSubmit={submit}>
          <div className="mobile-brand"><Logo /><span>LANYAN TI</span></div>
          <p className="eyebrow">Bienvenido de vuelta</p>
          <h2>Ingresa a tu espacio</h2>
          <p className="muted">Utiliza tus credenciales institucionales.</p>
          <label className="field-label">Correo institucional<Input type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} required /></label>
          <label className="field-label">Contraseña<Input type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required /></label>
          {error && <div className="error-note" role="alert">{error}</div>}
          {help && <div className="success-note"><CircleAlert size={17} />Para restablecer tu contraseña, comunícate con el Service Desk del Instituto.</div>}
          <div className="form-between"><span /><Button type="button" variant="ghost" onClick={() => setHelp(true)}>¿Olvidaste tu contraseña?</Button></div>
          <Button className="btn-wide" type="submit" disabled={busy}>{busy ? 'Verificando…' : 'Ingresar'}<ArrowRight size={17} /></Button>
        </form>
      </section>
    </div>
  )
}

function Logo() {
  return <div className="logo"><span /><span /><span /></div>
}

function Sidebar({ role, route, onNavigate, open, onClose, onLogout }: { role: Role; route: RouteKey; onNavigate: (route: RouteKey) => void; open: boolean; onClose: () => void; onLogout: () => void }) {
  return (
    <>
      {open && <div className="sidebar-scrim" onClick={onClose} />}
      <aside className={`sidebar ${open ? 'sidebar-open' : ''}`}>
        <div className="sidebar-head"><div className="brand-lockup dark"><Logo /><span>LANYAN TI</span></div><Button className="mobile-close icon-btn" variant="ghost" onClick={onClose}><X size={19} /></Button></div>
        <div className="workspace"><div className="workspace-icon">II</div><div><strong>Instituto de Idiomas</strong><span>Universidad Nacional de Piura</span></div><ChevronDown size={15} /></div>
        <nav>
          <p className="nav-label">Espacio de trabajo</p>
          {navItems.filter((item) => item.roles.includes(role)).map((item) => {
            const Icon = item.icon
            return <Button variant="ghost" className={`nav-item ${route === item.id ? 'active' : ''}`} key={item.id} onClick={() => onNavigate(item.id)}><Icon size={18} /><span>{item.label}</span></Button>
          })}
        </nav>
        <div className="sidebar-bottom">
          <div className="support-card"><div className="support-icon"><Headphones size={18} /></div><strong>¿Necesitas ayuda?</strong><span>Contacta al Service Desk</span><Button variant="secondary" onClick={() => onNavigate('tickets')}>Abrir soporte</Button></div>
          <Button variant="ghost" className="nav-item logout" onClick={onLogout}><LogOut size={18} />Cerrar sesión</Button>
        </div>
      </aside>
    </>
  )
}

function Header({ account, onMenu }: { account: Account; onMenu: () => void }) {
  return (
    <header className="topbar">
      <Button variant="ghost" className="menu-button icon-btn" onClick={onMenu}><Menu size={21} /></Button>
      <div className="global-search"><Search size={18} /><Input placeholder="Buscar tickets, servicios, artículos..." /></div>
      <div className="top-actions">
        <div className="profile"><div className="avatar">{initials(account.fullName)}</div><div><strong>{account.fullName}</strong><span className="profile-role">{mainRoleLabel(account)}</span></div></div>
      </div>
    </header>
  )
}

const greeting = () => {
  const hour = new Date().getHours()
  return hour < 12 ? 'Buenos días' : hour < 19 ? 'Buenas tardes' : 'Buenas noches'
}

const longDate = () => {
  const text = new Date().toLocaleDateString('es-PE', { weekday: 'long', day: 'numeric', month: 'long' })
  return text.charAt(0).toUpperCase() + text.slice(1)
}

function Dashboard({ account, onNavigate, tickets }: { account: Account; onNavigate: (route: RouteKey) => void; tickets: Ticket[] }) {
  const stats = [
    { label: 'Tickets abiertos', value: '24', delta: '8% menos', icon: TicketCheck, tone: 'lavender' },
    { label: 'Resueltos este mes', value: '142', delta: '12% más', icon: CheckCircle2, tone: 'mint' },
    { label: 'Pendientes', value: '11', delta: '3 requieren atención', icon: Clock3, tone: 'pink' },
    { label: 'Cumplimiento SLA', value: '94.8%', delta: 'Meta 92%', icon: Gauge, tone: 'blue' },
  ]
  return (
    <>
      <PageHeader eyebrow={longDate()} title={`${greeting()}, ${account.fullName.trim().split(/\s+/)[0]}`} description="Aquí tienes el pulso de los servicios TI de hoy." action={<Button onClick={() => onNavigate('tickets')}><Plus size={17} />Nuevo ticket</Button>} />
      <div className="stat-grid">{stats.map(({ label, value, delta, icon: Icon, tone }) => <Card className="stat-card" key={label}><div className={`stat-icon icon-${tone}`}><Icon size={20} /></div><div className="stat-copy"><span>{label}</span><strong>{value}</strong><small>{delta.includes('menos') || delta.includes('más') || delta.includes('Meta') ? <TrendingUp size={13} /> : <CircleAlert size={13} />}{delta}</small></div><MoreHorizontal size={18} className="more" /></Card>)}</div>
      <div className="dashboard-grid">
        <Card className="chart-card">
          <div className="card-title"><div><h2>Rendimiento de tickets</h2><p>Comparativo mensual de solicitudes</p></div><Select defaultValue="6"><option value="6">Últimos 6 meses</option><option value="12">Último año</option></Select></div>
          <div className="legend"><span><i className="dot lavender" />Abiertos</span><span><i className="dot mint" />Resueltos</span></div>
          <div className="bar-chart">{monthlyData.map((item) => <div className="bar-group" key={item.month}><div className="bars"><span className="bar lavender-bar" style={{ height: `${item.opened * 1.7}px` }} title={`${item.opened} abiertos`} /><span className="bar mint-bar" style={{ height: `${item.resolved * 1.7}px` }} title={`${item.resolved} resueltos`} /></div><small>{item.month}</small></div>)}</div>
        </Card>
        <Card className="sla-card">
          <div className="card-title"><div><h2>Salud del SLA</h2><p>Objetivo mensual</p></div><Badge tone="mint">En objetivo</Badge></div>
          <div className="donut" style={{ background: 'conic-gradient(var(--lavender-strong) 0 94.8%, var(--surface-2) 94.8%)' }}><div><strong>94.8%</strong><span>cumplimiento</span></div></div>
          <div className="sla-list"><div><span>Dentro del SLA</span><strong>135</strong></div><div><span>Próximos a vencer</span><strong>5</strong></div><div><span>Vencidos</span><strong>2</strong></div></div>
        </Card>
      </div>
      <Card className="table-card">
        <div className="card-title"><div><h2>Actividad reciente</h2><p>Tickets que necesitan seguimiento</p></div><Button variant="ghost" onClick={() => onNavigate('tickets')}>Ver todos <ArrowRight size={16} /></Button></div>
        <TicketTable tickets={tickets.slice(0, 5)} />
      </Card>
    </>
  )
}

function TicketsPage({ role, tickets, setTickets, notify }: { role: Role; tickets: Ticket[]; setTickets: (tickets: Ticket[]) => void; notify: (message: string) => void }) {
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState('Todos')
  const [createOpen, setCreateOpen] = useState(false)
  const [selected, setSelected] = useState<Ticket | null>(null)
  const [serviceNames, setServiceNames] = useState<string[]>([])
  useEffect(() => { listPublishedServices().then((page) => setServiceNames(page.content.map((service) => service.name))).catch(() => undefined) }, [])
  const [form, setForm] = useState({ title: '', service: 'Plataforma virtual', description: '', priority: 'Media' as Priority })
  const filtered = tickets.filter((ticket) => (status === 'Todos' || ticket.status === status) && `${ticket.title} ${ticket.id}`.toLowerCase().includes(query.toLowerCase()))

  const createTicket = () => {
    if (!form.title.trim()) return
    const ticket: Ticket = { id: `INC-${1050 + tickets.length}`, title: form.title, service: form.service, description: form.description, requester: 'Andrea Salazar', technician: 'Sin asignar', priority: form.priority, status: 'Nuevo', createdAt: 'Ahora', sla: 100, comments: 0 }
    setTickets([ticket, ...tickets]); setCreateOpen(false); notify(`${ticket.id} registrado correctamente`); setForm({ title: '', service: 'Plataforma virtual', description: '', priority: 'Media' })
  }
  const updateStatus = (next: TicketStatus) => {
    if (!selected) return
    const updated = { ...selected, status: next }
    setTickets(tickets.map((ticket) => ticket.id === selected.id ? updated : ticket)); setSelected(updated); notify('Estado actualizado')
  }
  return (
    <>
      <PageHeader eyebrow="Gestión de atención" title={role === 'usuario' ? 'Mis tickets' : 'Tickets'} description={role === 'usuario' ? 'Consulta y da seguimiento a tus solicitudes.' : 'Gestiona incidentes y solicitudes desde un solo lugar.'} action={<Button onClick={() => setCreateOpen(true)}><Plus size={17} />Registrar ticket</Button>} />
      <Card className="toolbar"><div className="table-search"><Search size={17} /><Input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Buscar por ID o asunto" /></div><div className="toolbar-right"><Select value={status} onChange={(e) => setStatus(e.target.value)}><option>Todos</option><option>Nuevo</option><option>En progreso</option><option>En espera</option><option>Resuelto</option></Select><Button variant="secondary"><SlidersHorizontal size={16} />Más filtros</Button></div></Card>
      <Card className="table-card"><div className="result-count"><strong>{filtered.length} tickets</strong><span>Actualizado hace unos segundos</span></div><TicketTable tickets={filtered} onSelect={setSelected} /></Card>
      <Modal open={createOpen} onClose={() => setCreateOpen(false)} title="Registrar nuevo ticket" footer={<><Button variant="secondary" onClick={() => setCreateOpen(false)}>Cancelar</Button><Button onClick={createTicket}>Registrar ticket</Button></>}>
        <label className="field-label">Asunto<Input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} placeholder="Resume el problema en una frase" /></label>
        <div className="form-grid"><label className="field-label">Servicio<Select value={form.service} onChange={(e) => setForm({ ...form, service: e.target.value })}>{serviceNames.map((name) => <option key={name}>{name}</option>)}</Select></label><label className="field-label">Prioridad<Select value={form.priority} onChange={(e) => setForm({ ...form, priority: e.target.value as Priority })}><option>Baja</option><option>Media</option><option>Alta</option><option>Crítica</option></Select></label></div>
        <label className="field-label">Descripción<Textarea rows={5} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} placeholder="Incluye los pasos, mensajes de error y cuándo comenzó..." /></label>
        <div className="upload-zone"><Plus size={20} /><strong>Adjuntar evidencia</strong><span>PNG, JPG o PDF hasta 10 MB</span></div>
      </Modal>
      <Modal open={!!selected} onClose={() => setSelected(null)} title={`${selected?.id} · ${selected?.title}`} footer={<Button onClick={() => { notify('Comentario agregado'); setSelected(null) }}><MessageSquare size={16} />Añadir comentario</Button>}>
        {selected && <><div className="detail-grid"><div><span>Estado</span><Badge tone={statusTone(selected.status)}>{selected.status}</Badge></div><div><span>Prioridad</span><Badge tone={statusTone(selected.priority)}>{selected.priority}</Badge></div><div><span>Técnico</span><strong>{selected.technician}</strong></div><div><span>SLA restante</span><strong>{selected.sla}%</strong></div></div>
        <div className="status-actions">{(['Nuevo', 'En progreso', 'En espera', 'Resuelto'] as TicketStatus[]).map((item) => <Button key={item} variant={selected.status === item ? 'primary' : 'secondary'} onClick={() => updateStatus(item)}>{item}</Button>)}</div>
        <div className="comment-box"><div className="avatar small">CV</div><div><strong>Carlos Vega</strong><small>Hace 12 min</small><p>Estamos validando las credenciales con el equipo de plataforma.</p></div></div>
        <label className="field-label">Nuevo comentario<Textarea rows={3} placeholder="Escribe una actualización..." /></label></>}
      </Modal>
    </>
  )
}

function TicketTable({ tickets, onSelect }: { tickets: Ticket[]; onSelect?: (ticket: Ticket) => void }) {
  return <div className="table-scroll"><table><thead><tr><th>Ticket</th><th>Servicio</th><th>Prioridad</th><th>Estado</th><th>Técnico</th><th>SLA</th><th /></tr></thead><tbody>{tickets.map((ticket) => <tr key={ticket.id} onClick={() => onSelect?.(ticket)} className={onSelect ? 'clickable' : ''}><td><strong>{ticket.id}</strong><span>{ticket.title}</span></td><td>{ticket.service}</td><td><Badge tone={statusTone(ticket.priority)}>{ticket.priority}</Badge></td><td><Badge tone={statusTone(ticket.status)}>{ticket.status}</Badge></td><td><div className="person-cell"><div className="avatar mini">{ticket.technician.split(' ').map((word) => word[0]).join('').slice(0, 2)}</div>{ticket.technician}</div></td><td><div className="sla-cell"><Progress value={ticket.sla} tone={ticket.sla < 40 ? 'pink' : 'mint'} /><small>{ticket.sla}%</small></div></td><td><ChevronRight size={17} /></td></tr>)}</tbody></table>{tickets.length === 0 && <div className="empty-state"><Search size={24} /><strong>Sin resultados</strong><span>Prueba con otros filtros.</span></div>}</div>
}

function ServiceDesk({ tickets, setTickets, notify }: { tickets: Ticket[]; setTickets: (tickets: Ticket[]) => void; notify: (message: string) => void }) {
  const statuses: TicketStatus[] = ['Nuevo', 'En progreso', 'En espera', 'Resuelto']
  const move = (ticket: Ticket, direction: number) => {
    const index = statuses.indexOf(ticket.status)
    const next = statuses[Math.max(0, Math.min(statuses.length - 1, index + direction))]
    setTickets(tickets.map((item) => item.id === ticket.id ? { ...item, status: next } : item)); notify(`${ticket.id} movido a ${next}`)
  }
  return (
    <>
      <PageHeader eyebrow="Centro de atención" title="Service Desk" description="Prioriza, asigna y resuelve el trabajo del equipo en tiempo real." action={<div className="header-actions"><Button variant="secondary"><Users size={16} />3 técnicos activos</Button><Button><Plus size={17} />Asignar ticket</Button></div>} />
      <div className="desk-summary"><span><i className="dot blue" />6 en cola</span><span><i className="dot pink" />2 críticos</span><span><i className="dot mint" />18 min respuesta media</span></div>
      <div className="kanban">{statuses.map((status) => <div className="kanban-column" key={status}><div className="kanban-head"><div><i className={`dot ${status === 'Nuevo' ? 'blue' : status === 'Resuelto' ? 'mint' : status === 'En espera' ? 'pink' : 'lavender'}`} /><strong>{status}</strong><span>{tickets.filter((item) => item.status === status).length}</span></div><Button variant="ghost" className="icon-btn"><Plus size={17} /></Button></div><div className="kanban-list">{tickets.filter((item) => item.status === status).map((ticket) => <Card className="kanban-card" key={ticket.id}><div className="kanban-card-top"><Badge tone={statusTone(ticket.priority)}>{ticket.priority}</Badge><span>{ticket.id}</span></div><h3>{ticket.title}</h3><p>{ticket.service}</p><div className="kanban-footer"><div className="avatar mini">{ticket.technician.split(' ').map((v) => v[0]).join('').slice(0, 2)}</div><span><Clock3 size={13} />{ticket.sla}% SLA</span><div><Button variant="ghost" className="icon-btn" onClick={() => move(ticket, -1)} disabled={status === 'Nuevo'}><ArrowLeft size={14} /></Button><Button variant="ghost" className="icon-btn" onClick={() => move(ticket, 1)} disabled={status === 'Resuelto'}><ArrowRight size={14} /></Button></div></div></Card>)}</div></div>)}</div>
    </>
  )
}

function Problems({ notify }: { notify: (message: string) => void }) {
  const [selected, setSelected] = useState<(typeof problems)[number] | null>(null)
  return (
    <>
      <PageHeader eyebrow="Gestión proactiva" title="Problemas" description="Identifica patrones, elimina causas raíz y previene nuevos incidentes." action={<Button onClick={() => notify('Nuevo análisis de problema iniciado')}><Plus size={17} />Nuevo problema</Button>} />
      <div className="stat-grid three"><Card className="mini-stat"><CircleAlert size={20} /><span>Problemas activos</span><strong>7</strong></Card><Card className="mini-stat"><RefreshCw size={20} /><span>Incidentes vinculados</span><strong>37</strong></Card><Card className="mini-stat"><CheckCircle2 size={20} /><span>Resueltos este mes</span><strong>12</strong></Card></div>
      <div className="problem-list">{problems.map((problem) => <Card className="problem-card" key={problem.id} onClick={() => setSelected(problem)}><div className="problem-id"><span>{problem.id}</span><Badge tone={statusTone(problem.status)}>{problem.status}</Badge></div><div><h3>{problem.title}</h3><p><strong>Causa:</strong> {problem.cause}</p></div><div className="problem-meta"><span><RefreshCw size={15} />{problem.incidents} incidentes</span><span><UserCheck size={15} />{problem.owner}</span><ChevronRight size={18} /></div></Card>)}</div>
      <Modal open={!!selected} onClose={() => setSelected(null)} title={`Análisis de causa raíz · ${selected?.id}`} footer={<><Button variant="secondary" onClick={() => setSelected(null)}>Guardar borrador</Button><Button onClick={() => { setSelected(null); notify('Análisis actualizado') }}>Actualizar problema</Button></>}>
        <div className="detail-callout"><Lightbulb size={24} /><div><strong>{selected?.title}</strong><p>{selected?.incidents} incidentes asociados · Responsable: {selected?.owner}</p></div></div>
        <label className="field-label">Causa raíz<Textarea rows={3} defaultValue={selected?.cause} /></label>
        <label className="field-label">Solución permanente<Textarea rows={4} placeholder="Documenta la acción correctiva..." /></label>
        <div className="form-grid"><label className="field-label">Estado<Select defaultValue={selected?.status}><option>Investigando</option><option>Causa identificada</option><option>Resuelto</option></Select></label><label className="field-label">Fecha objetivo<Input type="date" defaultValue="2025-06-21" /></label></div>
      </Modal>
    </>
  )
}

function WorkflowPage({ mode, items, setItems, notify }: { mode: 'Acceso' | 'Servicio'; items: WorkflowRequest[]; setItems: (items: WorkflowRequest[]) => void; notify: (message: string) => void }) {
  const [open, setOpen] = useState(false)
  const [filter, setFilter] = useState('Todos')
  const list = items.filter((item) => item.type === mode && (filter === 'Todos' || item.status === filter))
  const title = mode === 'Acceso' ? 'Gestión de accesos' : 'Solicitudes de servicio'
  const approve = (id: string, status: WorkflowRequest['status']) => { setItems(items.map((item) => item.id === id ? { ...item, status } : item)); notify(`Solicitud ${status.toLowerCase()}`) }
  const create = () => {
    const prefix = mode === 'Acceso' ? 'ACC' : 'SOL'
    setItems([{ id: `${prefix}-${210 + items.length}`, title: mode === 'Acceso' ? 'Nueva solicitud de permisos' : 'Nueva solicitud de servicio', requester: 'Andrea Salazar', type: mode, status: 'Pendiente', date: 'Hoy', approver: 'Jefatura TI' }, ...items]); setOpen(false); notify('Solicitud registrada')
  }
  return (
    <>
      <PageHeader eyebrow={mode === 'Acceso' ? 'Seguridad y permisos' : 'Flujo de atención'} title={title} description={mode === 'Acceso' ? 'Solicita, aprueba y revoca permisos de forma segura.' : 'Registra y sigue cada solicitud hasta su cierre.'} action={<Button onClick={() => setOpen(true)}><Plus size={17} />Nueva solicitud</Button>} />
      <div className="workflow-tabs">{['Todos', 'Pendiente', 'Aprobado', 'En proceso', 'Cerrado'].map((item) => <Button key={item} variant={filter === item ? 'primary' : 'secondary'} onClick={() => setFilter(item)}>{item}</Button>)}</div>
      <Card className="table-card"><div className="table-scroll"><table><thead><tr><th>Solicitud</th><th>Solicitante</th><th>Fecha</th><th>Aprobador</th><th>Estado</th><th>Acciones</th></tr></thead><tbody>{list.map((item) => <tr key={item.id}><td><strong>{item.id}</strong><span>{item.title}</span></td><td>{item.requester}</td><td>{item.date}</td><td>{item.approver}</td><td><Badge tone={statusTone(item.status)}>{item.status}</Badge></td><td><div className="row-actions">{item.status === 'Pendiente' && <Button variant="ghost" onClick={() => approve(item.id, 'Aprobado')}><Check size={16} />Aprobar</Button>}{mode === 'Acceso' && item.status === 'Aprobado' && <Button variant="danger" onClick={() => approve(item.id, 'Revocado')}><LockKeyhole size={15} />Revocar</Button>}{mode === 'Servicio' && item.status === 'En proceso' && <Button variant="ghost" onClick={() => approve(item.id, 'Cerrado')}><CheckCircle2 size={16} />Cerrar</Button>}</div></td></tr>)}</tbody></table></div></Card>
      <Modal open={open} onClose={() => setOpen(false)} title={mode === 'Acceso' ? 'Solicitar acceso' : 'Registrar solicitud'} footer={<><Button variant="secondary" onClick={() => setOpen(false)}>Cancelar</Button><Button onClick={create}>Enviar solicitud</Button></>}>
        <label className="field-label">{mode === 'Acceso' ? 'Sistema o recurso' : 'Servicio'}<Select>{services.slice(0, 5).map((service) => <option key={service.id}>{service.name}</option>)}</Select></label>
        {mode === 'Acceso' && <label className="field-label">Nivel de permiso<Select><option>Solo lectura</option><option>Editor</option><option>Administrador</option></Select></label>}
        <label className="field-label">Justificación<Textarea rows={4} placeholder="Explica el motivo y el resultado esperado..." /></label>
        <label className="field-label">Fecha requerida<Input type="date" /></label>
      </Modal>
    </>
  )
}

function Operations({ notify }: { notify: (message: string) => void }) {
  const [maintenance, setMaintenance] = useState(false)
  return (
    <>
      <PageHeader eyebrow="Monitoreo de servicios" title="Operaciones TI" description="Disponibilidad, estado operativo y mantenimientos programados." action={<Button onClick={() => setMaintenance(true)}><Plus size={17} />Programar mantenimiento</Button>} />
      <div className="status-banner"><div><span className="pulse" /><div><strong>Todos los sistemas operan con normalidad</strong><p>Última comprobación hace 2 minutos</p></div></div><Badge tone="mint">Operativo</Badge></div>
      <div className="operations-grid"><Card className="availability-card"><div className="card-title"><div><h2>Disponibilidad de servicios</h2><p>Últimos 30 días</p></div><strong className="big-number">99.4%</strong></div>{services.slice(0, 6).map((service) => <div className="availability-row" key={service.id}><div><span className="pulse small" /><strong>{service.name}</strong></div><Progress value={service.availability} tone={service.availability < 98 ? 'pink' : 'mint'} /><span>{service.availability}%</span></div>)}</Card><Card><div className="card-title"><div><h2>Próximos mantenimientos</h2><p>Ventanas planificadas</p></div></div><div className="timeline"><div><span>18</span><small>JUN</small><p><strong>Actualización del aula virtual</strong><small>22:00 — 23:30 · Impacto bajo</small></p></div><div><span>22</span><small>JUN</small><p><strong>Mantenimiento de red</strong><small>08:00 — 10:00 · Laboratorio 2</small></p></div><div><span>29</span><small>JUN</small><p><strong>Respaldo de base de datos</strong><small>23:00 — 00:30 · Sin interrupción</small></p></div></div></Card></div>
      <Modal open={maintenance} onClose={() => setMaintenance(false)} title="Programar mantenimiento" footer={<><Button variant="secondary" onClick={() => setMaintenance(false)}>Cancelar</Button><Button onClick={() => { setMaintenance(false); notify('Mantenimiento programado') }}>Programar</Button></>}>
        <label className="field-label">Servicio<Select>{services.map((service) => <option key={service.id}>{service.name}</option>)}</Select></label><label className="field-label">Descripción<Input placeholder="Ej. Actualización de seguridad" /></label><div className="form-grid"><label className="field-label">Inicio<Input type="datetime-local" /></label><label className="field-label">Fin<Input type="datetime-local" /></label></div><label className="field-label">Impacto<Select><option>Bajo</option><option>Medio</option><option>Alto</option></Select></label>
      </Modal>
    </>
  )
}

function Knowledge({ notify }: { notify: (message: string) => void }) {
  const [query, setQuery] = useState('')
  const [selected, setSelected] = useState<(typeof articles)[number] | null>(null)
  const filtered = articles.filter((article) => `${article.title} ${article.category}`.toLowerCase().includes(query.toLowerCase()))
  return (
    <>
      <PageHeader eyebrow="Ayuda y autoservicio" title="Base de conocimiento" description="Respuestas claras para resolver las dudas más frecuentes." action={<Button onClick={() => notify('Borrador de artículo creado')}><Plus size={17} />Nuevo artículo</Button>} />
      <div className="knowledge-search"><BookOpen size={26} /><div><h2>¿Cómo podemos ayudarte?</h2><div className="hero-search"><Search size={19} /><Input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Buscar una solución..." /></div></div></div>
      <div className="knowledge-layout"><div className="article-list">{filtered.map((article) => <Card className="article-card" key={article.id} onClick={() => setSelected(article)}><div className="article-icon"><BookOpen size={19} /></div><div><Badge tone="blue">{article.category}</Badge><h3>{article.title}</h3><p>{article.summary}</p><span>{article.views.toLocaleString()} lecturas · {article.helpful}% útil · Actualizado {article.updatedAt}</span></div><ChevronRight size={18} /></Card>)}</div><Card className="popular-card"><h3>Temas populares</h3>{['Acceso y contraseñas', 'Aula virtual', 'Matrícula y pagos', 'Correo institucional', 'Conectividad'].map((topic, index) => <Button variant="ghost" key={topic}><span>{String(index + 1).padStart(2, '0')}</span>{topic}<ChevronRight size={15} /></Button>)}</Card></div>
      <Modal open={!!selected} onClose={() => setSelected(null)} title={selected?.title || ''} footer={<><span className="muted">¿Te resultó útil?</span><Button variant="secondary" onClick={() => notify('Gracias por tu valoración')}>Sí, me ayudó</Button><Button variant="ghost">No todavía</Button></>}>
        <Badge tone="blue">{selected?.category}</Badge><div className="article-content"><p>Esta guía te ayudará a completar el proceso de forma segura.</p><h3>1. Verifica tus datos institucionales</h3><p>Confirma que estés utilizando el correo registrado y que tu conexión sea estable.</p><h3>2. Sigue el proceso de recuperación</h3><p>Selecciona “Olvidé mi contraseña” e ingresa el código que recibirás en tu correo.</p><div className="tip"><Lightbulb size={18} /><p><strong>Consejo:</strong> El enlace de recuperación vence en 15 minutos.</p></div></div>
      </Modal>
    </>
  )
}

function Reports({ notify }: { notify: (message: string) => void }) {
  const [range, setRange] = useState('Este mes')
  return (
    <>
      <PageHeader eyebrow="Analítica de servicio" title="Reportes e indicadores" description="Convierte el desempeño operativo en decisiones de mejora." action={<div className="header-actions"><Select value={range} onChange={(e) => setRange(e.target.value)}><option>Este mes</option><option>Últimos 90 días</option><option>Este año</option></Select><Button onClick={() => notify('Reporte exportado en PDF')}><ArrowRight size={16} />Exportar reporte</Button></div>} />
      <div className="stat-grid"><Card className="report-stat"><span>Resolución al primer contacto</span><strong>76.4%</strong><small>+4.2% vs. periodo anterior</small><Progress value={76.4} /></Card><Card className="report-stat"><span>Tiempo medio de resolución</span><strong>3h 18m</strong><small>-22 min vs. periodo anterior</small><Progress value={68} tone="blue" /></Card><Card className="report-stat"><span>Satisfacción del usuario</span><strong>4.8 / 5</strong><small>238 respuestas recibidas</small><Progress value={96} tone="mint" /></Card><Card className="report-stat"><span>Cumplimiento de SLA</span><strong>94.8%</strong><small>+2.1% sobre la meta</small><Progress value={94.8} tone="pink" /></Card></div>
      <div className="dashboard-grid reports"><Card className="chart-card"><div className="card-title"><div><h2>Volumen por categoría</h2><p>Tickets registrados · {range}</p></div></div><div className="horizontal-chart">{[['Plataforma', 84], ['Matrícula', 67], ['Pagos', 53], ['Internet', 46], ['Correo', 35], ['Equipos', 28]].map(([label, value]) => <div key={label}><span>{label}</span><div><i style={{ width: `${value}%` }} /></div><strong>{value}</strong></div>)}</div></Card><Card><div className="card-title"><div><h2>Satisfacción</h2><p>Distribución de valoraciones</p></div></div><div className="rating-score"><strong>4.8</strong><div><span>★★★★★</span><small>Excelente servicio</small></div></div><div className="rating-bars">{[5, 4, 3, 2, 1].map((rating, index) => <div key={rating}><span>{rating}</span><Progress value={[86, 10, 3, 1, 0][index]} tone="mint" /><small>{[86, 10, 3, 1, 0][index]}%</small></div>)}</div></Card></div>
    </>
  )
}

function Improvement({ notify }: { notify: (message: string) => void }) {
  const [selected, setSelected] = useState(0)
  const phases = [
    { name: 'Planificar', icon: Lightbulb, count: 5, desc: 'Identificar oportunidades y definir objetivos medibles.' },
    { name: 'Hacer', icon: Wrench, count: 3, desc: 'Ejecutar acciones en un entorno controlado.' },
    { name: 'Verificar', icon: BarChart3, count: 4, desc: 'Medir resultados frente a los indicadores.' },
    { name: 'Actuar', icon: Zap, count: 2, desc: 'Estandarizar mejoras y reiniciar el ciclo.' },
  ]
  return (
    <>
      <PageHeader eyebrow="Ciclo PDCA" title="Mejora continua" description="Impulsa servicios más eficientes con acciones medibles y sostenibles." action={<Button onClick={() => notify('Nueva iniciativa registrada')}><Plus size={17} />Nueva iniciativa</Button>} />
      <div className="pdca">{phases.map((phase, index) => { const Icon = phase.icon; return <Button variant="secondary" className={selected === index ? 'pdca-active' : ''} key={phase.name} onClick={() => setSelected(index)}><div><Icon size={22} /></div><span>Paso {index + 1}</span><strong>{phase.name}</strong><small>{phase.desc}</small><Badge tone={selected === index ? 'lavender' : 'neutral'}>{phase.count} acciones</Badge>{index < 3 && <ArrowRight className="pdca-arrow" size={18} />}</Button> })}</div>
      <div className="improvement-grid"><Card className="table-card"><div className="card-title"><div><h2>Acciones en {phases[selected].name.toLowerCase()}</h2><p>Iniciativas priorizadas del trimestre</p></div><Button variant="ghost"><SlidersHorizontal size={16} />Filtrar</Button></div>{['Reducir tiempo de respuesta del Service Desk', 'Mejorar disponibilidad del aula virtual', 'Automatizar altas de correo institucional'].map((name, index) => <div className="action-row" key={name}><div className={`action-check ${index === 1 ? 'done' : ''}`}>{index === 1 && <Check size={14} />}</div><div><strong>{name}</strong><span>Responsable: {['Carlos Vega', 'Miguel Silva', 'Ana Peña'][index]}</span></div><div><Progress value={[72, 100, 38][index]} tone={index === 1 ? 'mint' : 'lavender'} /><small>{[72, 100, 38][index]}%</small></div><Badge tone={index === 1 ? 'mint' : 'blue'}>{index === 1 ? 'Completado' : 'En curso'}</Badge></div>)}</Card><Card className="indicator-card"><h2>Indicador destacado</h2><p>Tiempo de primera respuesta</p><strong>18 min</strong><span><TrendingUp size={15} /> 28% de mejora</span><div className="sparkline">{[45, 52, 38, 60, 54, 72, 83].map((value, index) => <i key={index} style={{ height: `${value}%` }} />)}</div><small>Objetivo: menos de 20 min</small></Card></div>
    </>
  )
}

function Administration({ currentId, notify }: { currentId: string; notify: (message: string) => void }) {
  const [tab, setTab] = useState('Usuarios')
  const [creating, setCreating] = useState(false)
  const [creatingService, setCreatingService] = useState(false)
  return (
    <>
      <PageHeader eyebrow="Configuración del sistema" title="Administración" description="Gestiona personas, permisos, servicios y acuerdos de nivel." action={tab === 'Usuarios' ? <Button onClick={() => setCreating(true)}><Plus size={17} />Añadir usuario</Button> : tab === 'Servicios' ? <Button onClick={() => setCreatingService(true)}><Plus size={17} />Añadir servicio</Button> : undefined} />
      <div className="admin-tabs">{['Usuarios', 'Roles y permisos', 'Auditoría', 'Servicios', 'Configuración SLA'].map((item) => <Button key={item} variant={tab === item ? 'primary' : 'secondary'} onClick={() => setTab(item)}>{item}</Button>)}</div>
      {tab === 'Usuarios' && <UsersPanel currentId={currentId} creating={creating} onCloseCreate={() => setCreating(false)} notify={notify} />}
      {tab === 'Roles y permisos' && <RolesPanel />}
      {tab === 'Auditoría' && <AuditPanel />}
      {tab === 'Servicios' && <ServicesPanel creating={creatingService} onCloseCreate={() => setCreatingService(false)} notify={notify} />}
      {tab === 'Configuración SLA' && <div className="sla-config">{[['Incidente crítico', '15 min', '2 horas'], ['Prioridad alta', '30 min', '4 horas'], ['Prioridad media', '2 horas', '8 horas'], ['Prioridad baja', '4 horas', '24 horas']].map((item, index) => <Card key={item[0]}><div><Badge tone={['danger', 'pink', 'blue', 'neutral'][index] as 'danger'}>{item[0]}</Badge><h3>{item[0]}</h3></div><label className="field-label">Primera respuesta<Input defaultValue={item[1]} /></label><label className="field-label">Resolución objetivo<Input defaultValue={item[2]} /></label><Button variant="secondary" onClick={() => notify('SLA actualizado')}>Guardar</Button></Card>)}</div>}
    </>
  )
}

export default App
