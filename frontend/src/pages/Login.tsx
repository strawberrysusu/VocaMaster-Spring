import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { setToken } from '../api/client'

export default function Login() {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [password2, setPassword2] = useState('')   // 가입 시 재입력 확인 — 서버 전송 없음 (클라 검증 전용)
  const [nickname, setNickname] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? '/'   // 좋아요·복사 누르다 로그인 온 경우 원래 화면으로

  // 구글 로그인 복귀 지점 (ADR-047): 서버가 refresh 쿠키만 심고 여기로 보낸다 —
  // 그 쿠키로 access token을 받아야 로그인 완성 (라우트 가드가 localStorage 토큰 기준이라)
  useEffect(() => {
    const oauth = new URLSearchParams(location.search).get('oauth')
    if (oauth === 'success') {
      fetch('/auth/refresh', { method: 'POST' })
        .then(async (r) => {
          if (!r.ok) throw new Error()
          const d = await r.json()
          setToken(d.accessToken)
          navigate('/', { replace: true })
        })
        .catch(() => setError('구글 로그인 처리에 실패했어요 — 다시 시도해주세요'))
    } else if (oauth === 'local_exists') {
      // pre-hijack 가드 — 일반 가입 계정엔 구글 자동 연결을 하지 않는다 (Codex 검산 8/28)
      setError('이 이메일로 일반 가입된 계정이 있어요 — 이메일/비밀번호로 로그인해 주세요')
    } else if (oauth === 'error') {
      setError('구글 로그인에 실패했어요 — 다시 시도하거나 이메일로 로그인해주세요')
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function submit() {
    if (submitting) return
    if (mode === 'register' && password !== password2) {
      setError('비밀번호가 서로 달라요 — 같은 비밀번호를 두 번 입력해주세요')
      return
    }
    setSubmitting(true)
    setError('')
    try {
      const path = mode === 'login' ? '/auth/login' : '/auth/register'
      const body = mode === 'login' ? { email, password } : { email, password, nickname }
      const res = await fetch(path, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      })
      const data = await res.json().catch(() => null)
      if (!res.ok) {
        // 백엔드가 꺼져 있으면 프록시가 JSON 아닌 에러를 돌려줌 — 주인장용 힌트를 명확히
        setError(
          data?.message ??
            '백엔드 서버가 꺼져 있는 것 같아요 — 바탕화면의 VocaMaster 아이콘을 실행한 뒤 다시 시도하세요',
        )
        return
      }
      setToken(data.accessToken)
      navigate(from, { replace: true })
    } catch {
      // fetch 자체가 실패(네트워크·서버 다운)하면 res.ok 분기에 못 들어와 아무 표시도 없던 구멍 (Codex 감사)
      setError('서버에 연결할 수 없어요 — 바탕화면의 VocaMaster 아이콘으로 서버를 켠 뒤 다시 시도하세요')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="auth-page">
      <h1>VocaMaster</h1>
      {/* 비로그인 방문자용 — 이 서비스가 뭔지 + 정책 문서 도달 경로 (구글 OAuth 운영 요건, 9/1) */}
      <p className="auth-tagline">
        일본어·영어 단어장을 만들고 플래시카드·퀴즈·타이핑·듣기로 외우는 학습 서비스예요.
      </p>
      <div className="card auth-card">
        <h2>{mode === 'login' ? '로그인' : '회원가입'}</h2>
        <input placeholder="이메일" value={email} onChange={(e) => setEmail(e.target.value)} />
        <input
          placeholder="비밀번호 (6자 이상)"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && submit()}
        />
        {mode === 'register' && (
          <>
            <input
              placeholder="비밀번호 확인 (한 번 더)"
              type="password"
              value={password2}
              onChange={(e) => setPassword2(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && submit()}
              aria-label="비밀번호 확인"
            />
            {password2 !== '' && password !== password2 && (
              <p className="error" style={{ margin: '0 0 4px', fontSize: 13 }}>비밀번호가 서로 달라요</p>
            )}
            <input
              placeholder="닉네임"
              value={nickname}
              onChange={(e) => setNickname(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && submit()}
            />
          </>
        )}
        {error && <p className="error" role="alert">{error}</p>}
        <button className="primary" disabled={submitting} onClick={submit}>
          {submitting ? '처리 중...' : mode === 'login' ? '로그인' : '가입하고 시작'}
        </button>
        <button
          className="ghost google-btn"
          disabled={submitting}
          onClick={() => {
            // SPA 라우팅이 아니라 서버의 OAuth 시작점으로 전체 이동 — 구글 → 콜백 → 다시 이 화면(?oauth=)
            window.location.href = '/oauth2/authorization/google'
          }}
        >
          <svg width="16" height="16" viewBox="0 0 48 48" aria-hidden="true"><path fill="#EA4335" d="M24 9.5c3.5 0 6.6 1.2 9.1 3.6l6.8-6.8C35.7 2.4 30.2 0 24 0 14.6 0 6.5 5.4 2.6 13.2l7.9 6.2C12.4 13.6 17.7 9.5 24 9.5z"/><path fill="#4285F4" d="M46.5 24.5c0-1.6-.1-3.1-.4-4.5H24v9h12.7c-.6 3-2.3 5.5-4.8 7.2l7.7 6c4.5-4.2 6.9-10.3 6.9-17.7z"/><path fill="#FBBC05" d="M10.5 28.6c-.5-1.5-.8-3-.8-4.6s.3-3.1.8-4.6l-7.9-6.2C.9 16.5 0 20.1 0 24s.9 7.5 2.6 10.8l7.9-6.2z"/><path fill="#34A853" d="M24 48c6.2 0 11.7-2 15.6-5.6l-7.7-6c-2.1 1.4-4.8 2.3-7.9 2.3-6.3 0-11.6-4.1-13.5-9.9l-7.9 6.2C6.5 42.6 14.6 48 24 48z"/></svg>
          구글로 로그인
        </button>
        <button className="ghost" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setPassword2(''); setError('') }}>
          {mode === 'login' ? '계정이 없어요 → 회원가입' : '이미 계정이 있어요 → 로그인'}
        </button>
      </div>
      <p className="auth-foot">
        <a className="doc-link" href="/privacy.html" target="_blank" rel="noreferrer">개인정보처리방침</a>
      </p>
    </div>
  )
}
