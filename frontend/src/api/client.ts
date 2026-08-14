// API 클라이언트 — 모든 요청이 이 관문을 지난다.
// 401(미인증/만료)이면 refresh 쿠키(httpOnly)로 access token을 재발급받아 1회 재시도.

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

let refreshInFlight: Promise<boolean> | null = null

// 갱신은 전역 single-flight — 홈처럼 병렬 요청 3개가 동시에 401을 맞아도 refresh는 딱 1번.
// 동시에 여러 번 돌리면 rotation이 두 번째 요청을 '옛 토큰 재사용 공격'으로 오인해
// 전체 로그아웃(P1-1 제재)이 발동할 수 있다 (Codex 검산)
function tryRefresh(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = fetch('/auth/refresh', { method: 'POST' })
      .then(async (res) => {
        if (!res.ok) return false
        const data = await res.json()
        setToken(data.accessToken)
        return true
      })
      .catch(() => false)
      .finally(() => {
        refreshInFlight = null
      })
  }
  return refreshInFlight
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
    window.location.href = '/app/login'
    throw new Error('로그인이 필요합니다')
  }

  if (!res.ok) {
    const body = await res.json().catch(() => null)
    throw new Error(body?.message ?? `요청 실패 (HTTP ${res.status})`)
  }
  if (res.status === 204) return undefined as T
  return res.json()
}
