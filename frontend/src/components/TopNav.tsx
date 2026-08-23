import { Link, useLocation, useNavigate } from 'react-router-dom'
import { clearToken, getToken } from '../api/client'

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
          {/* 설정 — 담을 기능(테마 색·음성 선택·퀴즈 자동 넘김) 생기면 */}
          <Link to="/stats" className={pathname.startsWith('/stats') ? 'active' : ''}>통계</Link>
          <Link to="/soon/설정" className="soon">설정</Link>
        </nav>
        {streak !== undefined && streak > 0 && (
          <div className="streak-pill">
            <span className="dot" /> {streak}일 연속
          </div>
        )}
        {getToken() ? (
          <>
            <div className="avatar">{emailInitial()}</div>
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
