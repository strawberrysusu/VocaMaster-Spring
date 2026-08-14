// API 클라이언트 — 모든 요청이 이 관문을 지난다.
// 401이면 refresh 쿠키(httpOnly)로 access token을 재발급받아 1회 재시도:
// ADR-016에서 말한 "access token 자동 갱신 인터셉터"의 실체.

const TOKEN_KEY = 'vm.accessToken'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}

export async function api<T>(path: string, options: RequestInit = {}, retried = false): Promise<T> {
  const headers: Record<string, string> = { ...(options.headers as Record<string, string>) }
  if (options.body && !headers['Content-Type']) headers['Content-Type'] = 'application/json'
  const token = getToken()
  if (token) headers['Authorization'] = `Bearer ${token}`

  const res = await fetch(path, { ...options, headers })

  if (res.status === 401 && !retried) {
    if (await tryRefresh()) return api<T>(path, options, true)
    clearToken()
    window.location.href = '/login'
    throw new Error('로그인이 필요합니다')
  }

  if (!res.ok) {
    const body = await res.json().catch(() => null)
    throw new Error(body?.message ?? `요청 실패 (HTTP ${res.status})`)
  }
  if (res.status === 204) return undefined as T
  return res.json()
}

async function tryRefresh(): Promise<boolean> {
  const res = await fetch('/auth/refresh', { method: 'POST' })
  if (!res.ok) return false
  const data = await res.json()
  setToken(data.accessToken)
  return true
}
