// 로그인 토큰(JWT) 가운데 조각에서 이메일 읽기 — 화면 표시·탈퇴 확인용 (검증은 서버가 한다).
// atob()은 바이트를 한 글자씩(Latin-1)으로 돌려줘서 한글 이메일(홍길동@…)이 깨졌다 → TextDecoder로 UTF-8로 읽는다 (11/14 검토).
// 다른 모듈을 import 하지 않아야 Node 단위 테스트(scripts/test-text.mjs)가 불러올 수 있다
export function jwtEmail(token: string | null): string {
  if (!token) return ''
  try {
    const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0))
    const payload = JSON.parse(new TextDecoder().decode(bytes)) as { email?: unknown }
    return typeof payload.email === 'string' ? payload.email : ''
  } catch {
    return ''
  }
}
