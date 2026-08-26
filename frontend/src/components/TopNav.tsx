import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { api, clearToken, getToken } from '../api/client'

type Me = { id: number; email: string; nickname: string; provider: string; createdAt?: string }

// JWT payload에서 이메일 첫 글자 — 별도 /me API 없이 아바타 이니셜용
function emailInitial(): string {
  const token = getToken()
  if (!token) return '?'
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
    return (payload.email?.[0] ?? '?').toUpperCase()
  } catch {
    return '?'
  }
}

export default function TopNav({ streak }: { streak?: number }) {
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const [me, setMe] = useState<Me | null>(null)
  const [open, setOpen] = useState(false)
  const popRef = useRef<HTMLDivElement>(null)

  // 아바타 클릭 → 내 프로필 팝오버 (첫 클릭에만 /users/me 조회)
  async function toggleProfile() {
    const next = !open
    setOpen(next)
    if (next && !me) {
      try {
        setMe(await api<Me>('/users/me'))
      } catch {
        /* 못 불러와도 팝오버 자체는 뜬다 */
      }
    }
  }

  // 바깥 클릭으로 닫기
  useEffect(() => {
    if (!open) return
    const onDown = (e: MouseEvent) => {
      if (popRef.current && !popRef.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onDown)
    return () => document.removeEventListener('mousedown', onDown)
  }, [open])

  async function logout() {
    await fetch('/auth/logout', { method: 'POST' }).catch(() => {})
    clearToken()
    navigate('/login')   // basename(/app)을 라우터가 처리 — 절대경로 /login은 API 보안에 걸림 (Codex 검산)
  }

  return (
    <header className="topnav">
      <div className="topnav-inner">
        <Link to="/" className="logo">
          <span className="logo-mark">語</span> VocaMaster
        </Link>
        <nav className="nav-links">
          <Link to="/" className={pathname === '/' ? 'active' : ''}>홈</Link>
          <Link to="/decks" className={pathname.startsWith('/decks') ? 'active' : ''}>내 덱</Link>
          <Link to="/explore" className={pathname.startsWith('/explore') ? 'active' : ''}>탐색</Link>
          <Link to="/stats" className={pathname.startsWith('/stats') ? 'active' : ''}>통계</Link>
          <Link to="/settings" className={pathname.startsWith('/settings') ? 'active' : ''}>설정</Link>
        </nav>
        {streak !== undefined && streak > 0 && (
          <div className="streak-pill">
            <span className="dot" /> {streak}일 연속
          </div>
        )}
        {getToken() ? (
          <>
            <div className="avatar-wrap" ref={popRef}>
              <button className="avatar avatar-btn" onClick={toggleProfile} aria-label="내 프로필">
                {emailInitial()}
              </button>
              {open && (
                <div className="profile-pop">
                  {me ? (
                    <>
                      <p className="pp-nick">{me.nickname}</p>
                      <p className="pp-email">{me.email}</p>
                      <p className="pp-meta">
                        <span className="pp-badge">{me.provider === 'google' ? '구글 연결됨' : '이메일 가입'}</span>
                        {me.createdAt && <span>{me.createdAt.slice(0, 10)} 가입</span>}
                      </p>
                    </>
                  ) : (
                    <p className="pp-email">불러오는 중…</p>
                  )}
                </div>
              )}
            </div>
            <button className="nav-logout" onClick={logout}>
              로그아웃
            </button>
          </>
        ) : (
          // 비로그인 열람(UNLISTED 링크 진입 등) — 로그인으로 유도
          <Link to="/login" className="btn-primary" style={{ textDecoration: 'none', padding: '9px 16px', fontSize: 13.5 }}>
            로그인
          </Link>
        )}
      </div>
    </header>
  )
}
