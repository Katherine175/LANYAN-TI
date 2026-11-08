export const SESSION_EXPIRED_EVENT = 'lanyan:session-expired'

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fields: Record<string, string>

  constructor(status: number, code: string, message: string, fields: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.code = code
    this.fields = fields
  }
}

type Csrf = { token: string; headerName: string }
let csrf: Csrf | null = null

export function forgetCsrf() {
  csrf = null
}

async function readCsrf(): Promise<Csrf> {
  const response = await fetch('/api/v1/auth/csrf', { headers: { Accept: 'application/json' } })
  if (!response.ok) throw serverUnavailable()
  csrf = (await response.json()) as Csrf
  return csrf
}

const serverUnavailable = () => new ApiError(0, 'SERVER_UNAVAILABLE', 'No se pudo conectar con el servidor. Comprueba que el backend esté iniciado.')

// El login y la consulta inicial de sesión responden 401 como parte normal de su flujo.
const expectedUnauthorized = (path: string) => path === '/auth/login' || path === '/auth/me'

async function send(path: string, method: string, body: unknown, token: Csrf | null): Promise<Response> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers[token.headerName] = token.token
  try {
    return await fetch(`/api/v1${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
  } catch {
    throw serverUnavailable()
  }
}

export async function api<T>(path: string, options: { method?: string; body?: unknown } = {}): Promise<T> {
  const method = options.method ?? 'GET'
  const needsToken = method !== 'GET'
  let response = await send(path, method, options.body, needsToken ? csrf ?? (await readCsrf()) : null)
  let data = await response.clone().json().catch(() => null)

  // El token vence junto con la sesión: se renueva una vez antes de dar el error por definitivo.
  if (needsToken && response.status === 403 && data?.code === 'CSRF_INVALID') {
    response = await send(path, method, options.body, await readCsrf())
    data = await response.clone().json().catch(() => null)
  }

  if (response.ok) return (response.status === 204 ? undefined : data) as T
  if (response.status === 401 && !expectedUnauthorized(path)) window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT))
  if (!data) throw serverUnavailable()
  throw new ApiError(response.status, data.code ?? 'ERROR', data.detail ?? 'No se pudo completar la operación.', data.errors ?? {})
}
