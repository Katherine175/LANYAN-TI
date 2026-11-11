import { useEffect, useState, type FormEvent } from 'react'
import {
  Activity, ArrowRight, BarChart3, BookOpen, Check, CheckCircle2,
  ChevronDown, ChevronRight, CircleAlert, ClipboardCheck, Clock3, Gauge,
  Headphones, KeyRound, LayoutDashboard, Lightbulb, LogOut, Menu,
  Plus, RefreshCw, Search, Settings2,
  SlidersHorizontal, Sparkles, TicketCheck, TrendingUp, UserCheck,
  Wrench, X, Zap, GraduationCap, type LucideIcon,
} from 'lucide-react'
import { articles, problems, services } from './data/mockData'
import type { Role, RouteKey } from './types'
import { AccessPage, ServiceRequestsPage } from './pages/workflows'
import { DashboardTickets, isStaff, ServiceDesk, SlaConfigPanel, TicketsPage, useTickets, useTicketStats } from './pages/tickets'
import { Badge, Button, Card, Input, Modal, PageHeader, Progress, Select, Textarea } from './components/ui'
import { Catalog, ServicesPanel } from './pages/catalog'
import { AuditPanel, RolesPanel, UsersPanel } from './pages/administration'
import { CourseCatalog, CoursesPanel, MyCourses } from './pages/courses'
import { SESSION_EXPIRED_EVENT } from './services/api'
import { currentAccount, initials, login, logout, mainRoleLabel, portalRole, type Account } from './services/identityService'

const navItems: { id: RouteKey; label: string; icon: LucideIcon; roles: Role[] }[] = [
  { id: 'dashboard', label: 'Resumen', icon: LayoutDashboard, roles: ['usuario', 'estudiante', 'tecnico', 'administrador'] },
  { id: 'catalogo', label: 'Catálogo TI', icon: Sparkles, roles: ['usuario', 'estudiante', 'tecnico', 'administrador'] },
  { id: 'mis-cursos', label: 'Mis cursos', icon: BookOpen, roles: ['estudiante'] },
  { id: 'cursos', label: 'Catálogo de cursos', icon: GraduationCap, roles: ['estudiante', 'administrador'] },
  { id: 'tickets', label: 'Tickets', icon: TicketCheck, roles: ['usuario', 'estudiante', 'tecnico', 'administrador'] },
  { id: 'service-desk', label: 'Service Desk', icon: Headphones, roles: ['tecnico', 'administrador'] },
  { id: 'problemas', label: 'Problemas', icon: CircleAlert, roles: ['tecnico', 'administrador'] },
  { id: 'accesos', label: 'Accesos', icon: KeyRound, roles: ['usuario', 'estudiante', 'tecnico', 'administrador'] },
  { id: 'solicitudes', label: 'Solicitudes', icon: ClipboardCheck, roles: ['usuario', 'estudiante', 'tecnico', 'administrador'] },
  { id: 'operaciones', label: 'Operaciones TI', icon: Activity, roles: ['tecnico', 'administrador'] },
  { id: 'conocimiento', label: 'Conocimiento', icon: BookOpen, roles: ['usuario', 'estudiante', 'tecnico', 'administrador'] },
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
          {view === 'dashboard' && <Dashboard account={account} role={role} onNavigate={navigate} />}
          {view === 'catalogo' && <Catalog canOperate={role === 'tecnico' || role === 'administrador'} notify={notify} onNavigate={navigate} />}
          {view === 'cursos' && <CourseCatalog isAdmin={role === 'administrador'} notify={notify} onOpenMyCourses={() => navigate('mis-cursos')} />}
          {view === 'mis-cursos' && <MyCourses onOpenCatalog={() => navigate('cursos')} notify={notify} />}
          {view === 'tickets' && <TicketsPage role={role} notify={notify} />}
          {view === 'service-desk' && <ServiceDesk notify={notify} />}
          {view === 'problemas' && <Problems notify={notify} />}
          {view === 'accesos' && <AccessPage role={role} email={account.email} notify={notify} />}
          {view === 'solicitudes' && <ServiceRequestsPage role={role} email={account.email} notify={notify} />}
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

function Dashboard({ account, role, onNavigate }: { account: Account; role: Role; onNavigate: (route: RouteKey) => void }) {
  const { tickets } = useTickets(role)
  const stats = useTicketStats(tickets)
  const staff = isStaff(role)
  const cards = [
    { label: staff ? 'Tickets abiertos' : 'Mis tickets abiertos', value: String(stats.open), delta: `${stats.waiting} sin atender`, icon: TicketCheck, tone: 'lavender' },
    { label: 'Resueltos', value: String(stats.resolved), delta: `${stats.met} dentro del plazo`, icon: CheckCircle2, tone: 'mint' },
    { label: 'Por vencer o vencidos', value: String(stats.atRisk), delta: `${stats.breachedOpen} ya vencidos`, icon: Clock3, tone: 'pink' },
    { label: 'Cumplimiento SLA', value: stats.compliance === null ? '—' : `${stats.compliance}%`, delta: stats.compliance === null ? 'Aún sin tickets resueltos' : 'De los tickets resueltos', icon: Gauge, tone: 'blue' },
  ]
  const peak = Math.max(1, ...stats.months.flatMap((item) => [item.opened, item.resolved]))
  const compliance = stats.compliance ?? 0
  return (
    <>
      <PageHeader eyebrow={longDate()} title={`${greeting()}, ${account.fullName.trim().split(/\s+/)[0]}`} description={staff ? 'Aquí tienes el pulso de los servicios TI de hoy.' : 'Aquí ves el avance de tus tickets.'} action={<Button onClick={() => onNavigate('tickets')}><Plus size={17} />Nuevo ticket</Button>} />
      <div className="stat-grid">{cards.map(({ label, value, delta, icon: Icon, tone }) => <Card className="stat-card" key={label}><div className={`stat-icon icon-${tone}`}><Icon size={20} /></div><div className="stat-copy"><span>{label}</span><strong>{value}</strong><small>{delta}</small></div></Card>)}</div>
      <div className="dashboard-grid">
        <Card className="chart-card">
          <div className="card-title"><div><h2>Rendimiento de tickets</h2><p>Registrados y resueltos en los últimos 6 meses</p></div></div>
          <div className="legend"><span><i className="dot lavender" />Registrados</span><span><i className="dot mint" />Resueltos</span></div>
          <div className="bar-chart">{stats.months.map((item) => <div className="bar-group" key={item.month}><div className="bars"><span className="bar lavender-bar" style={{ height: `${(item.opened / peak) * 170}px` }} title={`${item.opened} registrados`} /><span className="bar mint-bar" style={{ height: `${(item.resolved / peak) * 170}px` }} title={`${item.resolved} resueltos`} /></div><small>{item.month}</small></div>)}</div>
        </Card>
        <Card className="sla-card">
          <div className="card-title"><div><h2>Salud del SLA</h2><p>Tickets resueltos dentro del plazo</p></div>{stats.compliance !== null && <Badge tone={compliance >= 90 ? 'mint' : 'pink'}>{compliance >= 90 ? 'En objetivo' : 'Bajo la meta'}</Badge>}</div>
          <div className="donut" style={{ background: `conic-gradient(var(--lavender-strong) 0 ${compliance}%, var(--surface-2) ${compliance}%)` }}><div><strong>{stats.compliance === null ? '—' : `${compliance}%`}</strong><span>cumplimiento</span></div></div>
          <div className="sla-list"><div><span>Resueltos a tiempo</span><strong>{stats.met}</strong></div><div><span>Abiertos por vencer o vencidos</span><strong>{stats.atRisk}</strong></div><div><span>Resueltos fuera de plazo</span><strong>{stats.missed}</strong></div></div>
        </Card>
      </div>
      <Card className="table-card">
        <div className="card-title"><div><h2>Actividad reciente</h2><p>{staff ? 'Últimos tickets registrados' : 'Tus últimos tickets'}</p></div><Button variant="ghost" onClick={() => onNavigate('tickets')}>Ver todos <ArrowRight size={16} /></Button></div>
        <DashboardTickets tickets={tickets} />
      </Card>
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
  const [creatingCourse, setCreatingCourse] = useState(false)
  return (
    <>
      <PageHeader eyebrow="Configuración del sistema" title="Administración" description="Gestiona personas, permisos, servicios y acuerdos de nivel." action={tab === 'Usuarios' ? <Button onClick={() => setCreating(true)}><Plus size={17} />Añadir usuario</Button> : tab === 'Servicios' ? <Button onClick={() => setCreatingService(true)}><Plus size={17} />Añadir servicio</Button> : tab === 'Cursos' ? <Button onClick={() => setCreatingCourse(true)}><Plus size={17} />Añadir curso</Button> : undefined} />
      <div className="admin-tabs">{['Usuarios', 'Roles y permisos', 'Cursos', 'Auditoría', 'Servicios', 'Configuración SLA'].map((item) => <Button key={item} variant={tab === item ? 'primary' : 'secondary'} onClick={() => setTab(item)}>{item}</Button>)}</div>
      {tab === 'Usuarios' && <UsersPanel currentId={currentId} creating={creating} onCloseCreate={() => setCreating(false)} notify={notify} />}
      {tab === 'Roles y permisos' && <RolesPanel />}
      {tab === 'Cursos' && <CoursesPanel creating={creatingCourse} onCloseCreate={() => setCreatingCourse(false)} notify={notify} />}
      {tab === 'Auditoría' && <AuditPanel />}
      {tab === 'Servicios' && <ServicesPanel creating={creatingService} onCloseCreate={() => setCreatingService(false)} notify={notify} />}
      {tab === 'Configuración SLA' && <SlaConfigPanel notify={notify} />}
    </>
  )
}

export default App
