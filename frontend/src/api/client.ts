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

/** 서버가 준 HTTP status와 code를 함께 나르는 에러. Error를 상속해 기존 호출부와 호환된다 */
export class ApiError extends Error {
  // 생성자 파라미터 프로퍼티는 이 프로젝트의 erasableSyntaxOnly 설정에서 금지 — 필드를 명시한다
  status: number
  code?: string

  constructor(message: string, status: number, code?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

let refreshInFlight: Promise<boolean> | null = null

// 로그아웃과 갱신이 동시에 서버로 가면 어느 쪽이 먼저 처리되든 사고다 (9/5 감사 A1, 9/25 실서버 재현).
// ① 갱신이 먼저: 새 refresh 토큰이 발급되고, 로그아웃은 이미 폐기된 옛 토큰을 들고 가서 아무것도 못 지운다.
//    갱신 응답이 로그아웃 응답보다 늦게 오면 새 쿠키(14일)와 access 토큰이 되살아나 로그아웃이 취소된다
// ② 로그아웃이 먼저: 갱신이 폐기된 토큰을 들고 가 재사용 공격으로 판정되고, 같은 계정의 모든 기기가 로그아웃된다
// 그래서 로그아웃은 진행 중인 갱신이 끝난 뒤(새 쿠키가 심어진 뒤) 보내고, 로그아웃 중에는 새 갱신을 시작하지 않는다
let loggingOut = false
// 로그아웃할 때마다 올라가는 세대 번호 — 로그아웃 전에 출발한 갱신의 응답은 토큰을 되살리지 못한다
let authEpoch = 0

// 갱신은 전역 single-flight — 홈처럼 병렬 요청 3개가 동시에 401을 맞아도 refresh는 딱 1번.
// 동시에 여러 번 돌리면 rotation이 두 번째 요청을 '옛 토큰 재사용 공격'으로 오인해
// 전체 로그아웃(P1-1 제재)이 발동할 수 있다 (Codex 검산)
function tryRefresh(): Promise<boolean> {
  if (loggingOut) return Promise.resolve(false)
  if (!refreshInFlight) {
    const epoch = authEpoch
    refreshInFlight = fetch('/auth/refresh', { method: 'POST' })
      .then(async (res) => {
        if (!res.ok) return false
        const data = await res.json()
        if (epoch !== authEpoch) return false
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

/** 로그아웃 — 진행 중인 갱신이 끝나야 그 갱신이 심은 새 refresh 쿠키까지 서버가 폐기할 수 있다 */
export async function logout(): Promise<void> {
  loggingOut = true
  authEpoch++
  try {
    if (refreshInFlight) await refreshInFlight
    await fetch('/auth/logout', { method: 'POST' }).catch(() => {})
  } finally {
    clearToken()
    loggingOut = false
  }
}

export async function api<T>(path: string, options: RequestInit = {}, retried = false): Promise<T> {
  const headers: Record<string, string> = { ...(options.headers as Record<string, string>) }
  if (options.body && !headers['Content-Type']) headers['Content-Type'] = 'application/json'
  const token = getToken()
  if (token) headers['Authorization'] = `Bearer ${token}`

  const res = await fetch(path, { ...options, headers })

  if (res.status === 401 && !retried) {
    if (await tryRefresh()) return api<T>(path, options, true)
    // 로그아웃 중이면 화면 이동은 로그아웃 쪽이 한다 — 여기서 페이지를 새로 열면 나가는 중인 로그아웃 요청이 끊긴다
    if (!loggingOut) {
      clearToken()
      window.location.href = '/app/login'
    }
    throw new Error('로그인이 필요합니다')
  }

  if (!res.ok) {
    const body = await res.json().catch(() => null)
    // Error를 상속하므로 기존 `(e as Error).message` 호출부는 그대로 동작한다.
    // status/code를 실어 보내는 이유: 같은 409라도 대응이 정반대인 경우가 있어
    // 호출부가 메시지 문자열을 파싱하는 일이 없어야 한다 (2026-09-01)
    throw new ApiError(body?.message ?? `요청 실패 (HTTP ${res.status})`, res.status, body?.code)
  }
  // 204 또는 200+빈 본문(삭제 등) — res.json()은 빈 본문에서 터진다
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}
