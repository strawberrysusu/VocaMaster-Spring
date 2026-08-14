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
          {/* 통계·설정 — 통계는 그래프 API(주간/덱별) 신설 후, 설정은 담을 기능 생기면 */}
          <Link to="/soon/통계" className="soon">통계</Link>
          <Link to="/soon/설정" className="soon">설정</Link>
        </nav>
        {streak !== undefined && streak > 0 && (
          <div className="streak-pill">
            <span className="dot" /> {streak}일 연속
          </div>
        )}
        <div className="avatar">{emailInitial()}</div>
        <button className="nav-logout" onClick={logout}>
          로그아웃
        </button>
      </div>
    </header>
  )
}
