import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { setToken } from '../api/client'

export default function Login() {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [nickname, setNickname] = useState('')
  const [error, setError] = useState('')
  const navigate = useNavigate()

  async function submit() {
    setError('')
    const path = mode === 'login' ? '/auth/login' : '/auth/register'
    const body = mode === 'login' ? { email, password } : { email, password, nickname }
    const res = await fetch(path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    const data = await res.json().catch(() => null)
    if (!res.ok) {
      setError(data?.message ?? '요청에 실패했습니다')
      return
    }
    setToken(data.accessToken)
    navigate('/')
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
        {error && <p className="error">{error}</p>}
        <button className="primary" onClick={submit}>
          {mode === 'login' ? '로그인' : '가입하고 시작'}
        </button>
        <button className="ghost" onClick={() => setMode(mode === 'login' ? 'register' : 'login')}>
          {mode === 'login' ? '계정이 없어요 → 회원가입' : '이미 계정이 있어요 → 로그인'}
        </button>
      </div>
    </div>
  )
}
