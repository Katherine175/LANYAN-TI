import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Check, ChevronLeft, ChevronRight, Pencil, Search, ShieldCheck, Trash2, TriangleAlert, Users, Wrench, GraduationCap } from 'lucide-react'
import { Badge, Button, Card, Input, Modal, Select } from '../components/ui'
import { ApiError } from '../services/api'
import {
  backendRoleLabels, createAccount, deleteAccount, initials, INSTITUTIONAL_DOMAIN, isInstitutionalEmail, listAccounts, listAudit, listRoles, updateAccount,
  type Account, type AuditEvent, type BackendRole, type Page, type RoleSummary,
} from '../services/identityService'

const roleTone: Record<BackendRole, 'lavender' | 'blue' | 'neutral' | 'mint'> = {
  ROLE_ADMIN: 'lavender', ROLE_TECHNICIAN: 'blue', ROLE_USER: 'neutral', ROLE_STUDENT: 'mint',
}

const errorText = (error: unknown) => (error instanceof Error ? error.message : 'No se pudo completar la operación.')

const roleOrder: BackendRole[] = ['ROLE_ADMIN', 'ROLE_TECHNICIAN', 'ROLE_USER', 'ROLE_STUDENT']

// El servidor ya explica cada rechazo (correo fuera de dominio, repetido, contraseña corta).
const formErrorText = (error: unknown) => {
  if (error instanceof ApiError && error.code === 'VALIDATION_ERROR') {
    const fields = Object.keys(error.fields)
    if (fields.includes('email')) return 'Revisa el correo: debe tener un formato válido.'
    if (fields.includes('fullName')) return 'Escribe el nombre completo.'
    if (fields.includes('roles')) return 'Selecciona al menos un rol.'
    return 'Revisa los datos del formulario.'
  }
  return errorText(error)
}

export function UsersPanel({ currentId, creating, onCloseCreate, notify }: { currentId: string; creating: boolean; onCloseCreate: () => void; notify: (message: string) => void }) {
  const [accounts, setAccounts] = useState<Account[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [query, setQuery] = useState('')
  const [editing, setEditing] = useState<Account | null>(null)
  const [removing, setRemoving] = useState<Account | null>(null)

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
            <thead><tr><th>Usuario</th><th>Correo</th><th>Rol</th><th>Estado</th><th>Acciones</th></tr></thead>
            <tbody>
              {visible.map((account) => {
                const self = account.id === currentId
                return (
                  <tr key={account.id}>
                    <td><div className="person-cell"><div className="avatar">{initials(account.fullName)}</div><strong>{account.fullName}</strong>{self && <Badge tone="lavender">Tú</Badge>}</div></td>
                    <td>{account.email}{!isInstitutionalEmail(account.email) && <span className="email-warning"><TriangleAlert size={13} />Correo no institucional</span>}</td>
                    <td><div className="badge-row">{account.roles.map((role) => <Badge key={role} tone={roleTone[role]}>{backendRoleLabels[role]}</Badge>)}</div></td>
                    <td><Badge tone={account.active ? 'mint' : 'neutral'}>{account.active ? 'Activo' : 'Inactivo'}</Badge></td>
                    <td>
                      <div className="row-actions">
                        <Button variant="ghost" className="icon-btn" aria-label={`Editar a ${account.fullName}`} title="Editar" onClick={() => setEditing(account)}><Pencil size={16} /></Button>
                        {!self && <Button variant="ghost" className="icon-btn" aria-label={`Eliminar a ${account.fullName}`} title="Eliminar" onClick={() => setRemoving(account)}><Trash2 size={16} /></Button>}
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
          {!loading && visible.length === 0 && <div className="empty-state"><Search size={24} /><strong>Sin resultados</strong><span>No hay cuentas que coincidan.</span></div>}
        </div>
      </Card>
      {creating && <AccountModal onClose={onCloseCreate} onSaved={(account) => { notify(`Cuenta de ${account.fullName} creada`); void load() }} />}
      {editing && <AccountModal account={editing} self={editing.id === currentId} onClose={() => setEditing(null)} onSaved={(account) => { notify(`Cuenta de ${account.fullName} actualizada`); void load() }} />}
      {removing && <DeleteAccountModal account={removing} onClose={() => setRemoving(null)} onDeleted={() => { notify(`Cuenta de ${removing.fullName} eliminada`); void load() }} />}
    </>
  )
}

/** Sin `account` crea una cuenta nueva; con `account` la edita. */
function AccountModal({ account, self = false, onClose, onSaved }: { account?: Account; self?: boolean; onClose: () => void; onSaved: (account: Account) => void }) {
  const [form, setForm] = useState({
    fullName: account?.fullName ?? '',
    email: account?.email ?? '',
    password: '',
    active: account?.active ?? true,
    roles: account?.roles ?? (['ROLE_USER'] as BackendRole[]),
  })
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const editing = account !== undefined
  // Una cuenta antigua puede conservar su correo; uno nuevo debe ser institucional.
  const emailChanged = form.email.trim().toLowerCase() !== account?.email

  const toggleRole = (role: BackendRole) => {
    const roles = form.roles.includes(role) ? form.roles.filter((item) => item !== role) : [...form.roles, role]
    setForm({ ...form, roles })
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (emailChanged && !isInstitutionalEmail(form.email)) { setError(`Usa un correo institucional que termine en @${INSTITUTIONAL_DOMAIN}.`); return }
    if (form.roles.length === 0) { setError('Selecciona al menos un rol.'); return }
    if ((!editing || form.password) && form.password.length < 12) { setError('La contraseña debe tener al menos 12 caracteres.'); return }
    setSaving(true)
    setError('')
    try {
      const saved = account
        ? await updateAccount(account.id, { fullName: form.fullName, email: form.email, active: form.active, roles: form.roles, ...(form.password ? { password: form.password } : {}) })
        : await createAccount({ fullName: form.fullName, email: form.email, password: form.password, roles: form.roles })
      onSaved(saved)
      onClose()
    } catch (e) {
      setError(formErrorText(e))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal open onClose={onClose} title={editing ? 'Editar usuario' : 'Añadir usuario'} footer={<><Button variant="secondary" onClick={onClose}>Cancelar</Button><Button type="submit" form="account-form" disabled={saving}>{saving ? 'Guardando…' : editing ? 'Guardar cambios' : 'Crear cuenta'}</Button></>}>
      <form id="account-form" className="account-form" onSubmit={submit}>
        <div className="form-grid">
          <label className="field-label">Nombre<Input required maxLength={120} value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} placeholder="Nombre completo" /></label>
          <label className="field-label">Correo<Input required type="email" maxLength={254} value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} placeholder={`usuario@${INSTITUTIONAL_DOMAIN}`} /><span className="field-hint">Solo correos @{INSTITUTIONAL_DOMAIN}</span></label>
        </div>
        <fieldset className="role-picker">
          <legend>Roles</legend>
          {roleOrder.map((role) => {
            const locked = self && role === 'ROLE_ADMIN'
            return (
              <label key={role} className={form.roles.includes(role) ? 'checked' : ''} title={locked ? 'No puedes quitarte el rol de administrador' : undefined}>
                <input type="checkbox" checked={form.roles.includes(role)} disabled={locked} onChange={() => toggleRole(role)} />
                {backendRoleLabels[role]}
              </label>
            )
          })}
        </fieldset>
        <div className="form-grid">
          <label className="field-label">{editing ? 'Nueva contraseña' : 'Contraseña inicial'}<Input required={!editing} type="password" autoComplete="new-password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} placeholder={editing ? 'Déjala vacía para no cambiarla' : 'Mínimo 12 caracteres'} /></label>
          {editing && <label className="field-label">Estado<Select value={form.active ? 'activo' : 'inactivo'} disabled={self} onChange={(e) => setForm({ ...form, active: e.target.value === 'activo' })}><option value="activo">Activo</option><option value="inactivo">Inactivo: no puede ingresar</option></Select></label>}
        </div>
        {error && <div className="error-note">{error}</div>}
      </form>
    </Modal>
  )
}

function DeleteAccountModal({ account, onClose, onDeleted }: { account: Account; onClose: () => void; onDeleted: () => void }) {
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const confirm = async () => {
    setBusy(true)
    setError('')
    try {
      await deleteAccount(account.id)
      onDeleted()
      onClose()
    } catch (e) {
      setError(errorText(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal open onClose={onClose} title="Eliminar usuario" footer={<><Button variant="secondary" onClick={onClose}>Cancelar</Button><Button variant="danger" onClick={confirm} disabled={busy}>{busy ? 'Eliminando…' : 'Eliminar cuenta'}</Button></>}>
      <p>Vas a eliminar la cuenta de <strong>{account.fullName}</strong> ({account.email}). La persona ya no podrá ingresar y la acción no se puede deshacer.</p>
      <p className="muted">El registro de auditoría conserva quién la eliminó y cuándo. Si solo quieres bloquear el acceso por un tiempo, edita la cuenta y márcala como inactiva.</p>
      {error && <div className="error-note">{error}</div>}
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
  USER_UPDATED: { text: 'Cuenta editada', tone: 'pink' },
  USER_DELETED: { text: 'Cuenta eliminada', tone: 'danger' },
  CATALOG_CATEGORY_CREATED: { text: 'Categoría creada', tone: 'blue' },
  CATALOG_SERVICE_CREATED: { text: 'Servicio creado', tone: 'blue' },
  CATALOG_SERVICE_UPDATED: { text: 'Servicio modificado', tone: 'pink' },
  CATALOG_SERVICE_STATUS_CHANGED: { text: 'Estado operativo', tone: 'lavender' },
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
