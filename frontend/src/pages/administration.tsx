import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Check, ChevronLeft, ChevronRight, Search, ShieldCheck, Users, Wrench, GraduationCap } from 'lucide-react'
import { Badge, Button, Card, Input, Modal, Select } from '../components/ui'
import { ApiError } from '../services/api'
import {
  backendRoleLabels, createAccount, initials, listAccounts, listAudit, listRoles,
  type Account, type AuditEvent, type BackendRole, type Page, type RoleSummary,
} from '../services/identityService'

const roleTone: Record<BackendRole, 'lavender' | 'blue' | 'neutral' | 'mint'> = {
  ROLE_ADMIN: 'lavender', ROLE_TECHNICIAN: 'blue', ROLE_USER: 'neutral', ROLE_STUDENT: 'mint',
}

const errorText = (error: unknown) => (error instanceof Error ? error.message : 'No se pudo completar la operación.')

export function UsersPanel({ creating, onCloseCreate, notify }: { creating: boolean; onCloseCreate: () => void; notify: (message: string) => void }) {
  const [accounts, setAccounts] = useState<Account[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [query, setQuery] = useState('')

  const load = () => listAccounts().then((page) => { setAccounts(page.content); setError('') }).catch((e) => setError(errorText(e))).finally(() => setLoading(false))
  useEffect(() => { void load() }, [])

  const visible = useMemo(() => {
    const text = query.trim().toLowerCase()
    return accounts.filter((account) => `${account.fullName} ${account.email}`.toLowerCase().includes(text))
  }, [accounts, query])

  return (
    <>
      <Card className="table-card">
        <div className="card-title">
          <div><h2>Usuarios del sistema</h2><p>{accounts.length} {accounts.length === 1 ? 'cuenta registrada' : 'cuentas registradas'}</p></div>
          <div className="table-search small"><Search size={16} /><Input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Buscar usuario" /></div>
        </div>
        {error && <div className="error-note">{error}</div>}
        <div className="table-scroll">
          <table>
            <thead><tr><th>Usuario</th><th>Correo</th><th>Rol</th><th>Estado</th></tr></thead>
            <tbody>
              {visible.map((account) => (
                <tr key={account.id}>
                  <td><div className="person-cell"><div className="avatar">{initials(account.fullName)}</div><strong>{account.fullName}</strong></div></td>
                  <td>{account.email}</td>
                  <td><div className="badge-row">{account.roles.map((role) => <Badge key={role} tone={roleTone[role]}>{backendRoleLabels[role]}</Badge>)}</div></td>
                  <td><Badge tone={account.active ? 'mint' : 'neutral'}>{account.active ? 'Activo' : 'Inactivo'}</Badge></td>
                </tr>
              ))}
            </tbody>
          </table>
          {!loading && visible.length === 0 && <div className="empty-state"><Search size={24} /><strong>Sin resultados</strong><span>No hay cuentas que coincidan.</span></div>}
        </div>
      </Card>
      <CreateAccountModal open={creating} onClose={onCloseCreate} onCreated={(account) => { notify(`Cuenta de ${account.fullName} creada`); void load() }} />
    </>
  )
}

function CreateAccountModal({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: (account: Account) => void }) {
  const empty = { fullName: '', email: '', password: '', role: 'ROLE_USER' as BackendRole }
  const [form, setForm] = useState(empty)
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  const close = () => { setForm(empty); setError(''); onClose() }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (form.password.length < 12) { setError('La contraseña debe tener al menos 12 caracteres.'); return }
    setSaving(true)
    setError('')
    try {
      const account = await createAccount({ fullName: form.fullName, email: form.email, password: form.password, roles: [form.role] })
      onCreated(account)
      close()
    } catch (e) {
      setError(e instanceof ApiError && e.code === 'VALIDATION_ERROR' ? 'Revisa el nombre y el correo.' : errorText(e))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal open={open} onClose={close} title="Añadir usuario" footer={<><Button variant="secondary" onClick={close}>Cancelar</Button><Button type="submit" form="create-account" disabled={saving}>{saving ? 'Guardando…' : 'Crear cuenta'}</Button></>}>
      <form id="create-account" onSubmit={submit}>
        <div className="form-grid">
          <label className="field-label">Nombre<Input required maxLength={120} value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} placeholder="Nombre completo" /></label>
          <label className="field-label">Correo<Input required type="email" maxLength={254} value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} placeholder="usuario@unp.edu.pe" /></label>
        </div>
        <div className="form-grid">
          <label className="field-label">Rol<Select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value as BackendRole })}>{(Object.keys(backendRoleLabels) as BackendRole[]).map((role) => <option key={role} value={role}>{backendRoleLabels[role]}</option>)}</Select></label>
          <label className="field-label">Contraseña inicial<Input required type="password" autoComplete="new-password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} placeholder="Mínimo 12 caracteres" /></label>
        </div>
        {error && <div className="error-note">{error}</div>}
      </form>
    </Modal>
  )
}

const roleIcon = { ROLE_ADMIN: ShieldCheck, ROLE_TECHNICIAN: Wrench, ROLE_USER: Users, ROLE_STUDENT: GraduationCap }
const roleIconTone = { ROLE_ADMIN: 'lavender', ROLE_TECHNICIAN: 'blue', ROLE_USER: 'mint', ROLE_STUDENT: 'pink' }

export function RolesPanel() {
  const [roles, setRoles] = useState<RoleSummary[]>([])
  const [accounts, setAccounts] = useState<Account[]>([])
  const [error, setError] = useState('')

  useEffect(() => {
    Promise.all([listRoles(), listAccounts()])
      .then(([roleList, page]) => { setRoles(roleList); setAccounts(page.content) })
      .catch((e) => setError(errorText(e)))
  }, [])

  if (error) return <div className="error-note">{error}</div>
  return (
    <div className="role-cards">
      {roles.map((role) => {
        const Icon = roleIcon[role.name]
        const total = accounts.filter((account) => account.roles.includes(role.name)).length
        return (
          <Card key={role.name}>
            <div className={`service-icon icon-${roleIconTone[role.name]}`}><Icon size={22} /></div>
            <h3>{backendRoleLabels[role.name]}</h3>
            <p>{total} {total === 1 ? 'usuario' : 'usuarios'} · {role.permissions.length} permisos</p>
            <div className="permission-list">{[...role.permissions].sort().map((permission) => <span key={permission}><Check size={14} />{permission}</span>)}</div>
          </Card>
        )
      })}
    </div>
  )
}

const auditLabels: Record<string, { text: string; tone: 'lavender' | 'blue' | 'mint' | 'pink' | 'neutral' | 'danger' }> = {
  LOGIN_SUCCEEDED: { text: 'Inicio de sesión', tone: 'mint' },
  LOGIN_FAILED: { text: 'Intento fallido', tone: 'danger' },
  LOGOUT: { text: 'Cierre de sesión', tone: 'neutral' },
  ADMIN_BOOTSTRAPPED: { text: 'Administrador inicial', tone: 'lavender' },
  USER_CREATED: { text: 'Cuenta creada', tone: 'blue' },
  USER_ROLES_CHANGED: { text: 'Roles modificados', tone: 'pink' },
}

const dateFormat = new Intl.DateTimeFormat('es-PE', { dateStyle: 'short', timeStyle: 'medium', timeZone: 'America/Lima' })

export function AuditPanel() {
  const [data, setData] = useState<Page<AuditEvent> | null>(null)
  const [page, setPage] = useState(0)
  const [error, setError] = useState('')

  useEffect(() => { listAudit(page).then(setData).catch((e) => setError(errorText(e))) }, [page])

  return (
    <Card className="table-card">
      <div className="card-title">
        <div><h2>Registro de auditoría</h2><p>Evidencia inalterable de accesos y cambios · {data?.totalElements ?? 0} eventos</p></div>
        <div className="pager">
          <Button variant="secondary" className="icon-btn" aria-label="Página anterior" disabled={page === 0} onClick={() => setPage(page - 1)}><ChevronLeft size={16} /></Button>
          <span>{data && data.totalPages > 0 ? `${page + 1} de ${data.totalPages}` : '—'}</span>
          <Button variant="secondary" className="icon-btn" aria-label="Página siguiente" disabled={!data || page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}><ChevronRight size={16} /></Button>
        </div>
      </div>
      {error && <div className="error-note">{error}</div>}
      <div className="table-scroll">
        <table>
          <thead><tr><th>Fecha y hora</th><th>Evento</th><th>Responsable</th><th>Detalle</th></tr></thead>
          <tbody>
            {data?.content.map((event) => {
              const label = auditLabels[event.action] ?? { text: event.action, tone: 'neutral' as const }
              return (
                <tr key={event.id}>
                  <td>{dateFormat.format(new Date(event.occurredAt))}</td>
                  <td><Badge tone={label.tone}>{label.text}</Badge></td>
                  <td>{event.actorEmail ?? 'Sistema'}</td>
                  <td>{event.detail ?? '—'}</td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </Card>
  )
}
