import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { setToken } from '../api/client'

export default function Login() {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [nickname, setNickname] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const navigate = useNavigate()

  async function submit() {
    if (submitting) return
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
      navigate('/')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="auth-page">
      <h1>VocaMaster</h1>
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
          <input placeholder="닉네임" value={nickname} onChange={(e) => setNickname(e.target.value)} />
        )}
        {error && <p className="error" role="alert">{error}</p>}
        <button className="primary" disabled={submitting} onClick={submit}>
          {submitting ? '처리 중...' : mode === 'login' ? '로그인' : '가입하고 시작'}
        </button>
        <button className="ghost" onClick={() => setMode(mode === 'login' ? 'register' : 'login')}>
          {mode === 'login' ? '계정이 없어요 → 회원가입' : '이미 계정이 있어요 → 로그인'}
        </button>
      </div>
    </div>
  )
}
