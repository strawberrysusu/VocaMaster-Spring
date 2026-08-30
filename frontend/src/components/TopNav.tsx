import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { api, clearToken, getToken } from '../api/client'

type Me = { id: number; email: string; nickname: string; provider: string; createdAt?: string }

interface Folder {
  id: number
  name: string
}

interface DeckLite {
  id: number
  folderId: number | null
}

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

// 라인 아이콘 (리디자인 목업의 아이콘+텍스트 메뉴 — 의존성 없이 인라인 SVG)
const ICON = {
  home: (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M3 10.5 12 3l9 7.5" /><path d="M5.5 9.5V21h13V9.5" />
    </svg>
  ),
  decks: (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="4" y="7" width="14" height="13" rx="2.5" /><path d="M8 4h11a2 2 0 0 1 2 2v11" />
    </svg>
  ),
  explore: (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="11" cy="11" r="7" /><path d="m20 20-3.8-3.8" />
    </svg>
  ),
  stats: (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M5 20V13" /><path d="M12 20V5" /><path d="M19 20v-9" />
    </svg>
  ),
  settings: (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M4 8h10" /><circle cx="17" cy="8" r="2.5" /><path d="M20 16H10" /><circle cx="7" cy="16" r="2.5" />
    </svg>
  ),
  folder: (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M3.5 6.5a2 2 0 0 1 2-2h4l2 2.5h7a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2z" />
    </svg>
  ),
}

const MENU = [
  { to: '/', label: '홈', icon: ICON.home, isActive: (p: string) => p === '/' },
  { to: '/decks', label: '내 덱', icon: ICON.decks, isActive: (p: string) => p.startsWith('/decks') || p.startsWith('/import') },
  { to: '/explore', label: '탐색', icon: ICON.explore, isActive: (p: string) => p.startsWith('/explore') || p.startsWith('/public') },
  { to: '/stats', label: '통계', icon: ICON.stats, isActive: (p: string) => p.startsWith('/stats') },
  { to: '/settings', label: '설정', icon: ICON.settings, isActive: (p: string) => p.startsWith('/settings') },
]

// 리디자인(8/30): 상단 가로 탭 → Quizlet식 좌측 사이드바. 컴포넌트 이름은 유지해서
// 이 파일 하나로 전 페이지가 전환된다. 모바일(≤768px)은 CSS가 같은 DOM을
// 상단 슬림 헤더 + 하단 탭바로 변형한다.
export default function TopNav({ streak }: { streak?: number }) {
  const { pathname, search } = useLocation()
  const navigate = useNavigate()
  const [me, setMe] = useState<Me | null>(null)
  const [open, setOpen] = useState(false)
  const popRef = useRef<HTMLDivElement>(null)
  const [folders, setFolders] = useState<Folder[]>([])
  const [deckCounts, setDeckCounts] = useState<Map<number, number>>(new Map())
  const loggedIn = !!getToken()

  // 사이드바 폴더 목록 — 페이지 이동마다 재조회 (폴더 생성/이동이 자연 반영). 실패해도 조용히.
  useEffect(() => {
    if (!loggedIn) return
    api<Folder[]>('/folders').then(setFolders).catch(() => {})
    api<DeckLite[]>('/decks')
      .then((ds) => {
        const m = new Map<number, number>()
        for (const d of ds) if (d.folderId !== null) m.set(d.folderId, (m.get(d.folderId) ?? 0) + 1)
        setDeckCounts(m)
      })
      .catch(() => {})
  }, [pathname, loggedIn])

  const activeFolderParam = pathname.startsWith('/decks') ? new URLSearchParams(search).get('folder') : null

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
    <div className="sidenav">
      <div className="side-top">
        <Link to="/" className="logo">
          <span className="logo-mark">語</span> VocaMaster
        </Link>
        {/* 모바일 상단 헤더 오른쪽 — 데스크톱에선 숨김 */}
        <div className="side-account-mobile">
          {loggedIn ? (
            <button className="nav-logout" onClick={logout}>로그아웃</button>
          ) : (
            <Link to="/login" className="btn-primary side-login-btn">로그인</Link>
          )}
        </div>
      </div>

      <nav className="side-menu" aria-label="주 메뉴">
        {MENU.map((m) => (
          <Link key={m.to} to={m.to} className={m.isActive(pathname) ? 'active' : ''}>
            {m.icon}
            <span>{m.label}</span>
          </Link>
        ))}
      </nav>

      {loggedIn && folders.length > 0 && (
        <div className="side-folders">
          <p className="side-folders-head">내 폴더</p>
          {folders.map((f) => (
            <Link
              key={f.id}
              to={`/decks?folder=${f.id}`}
              className={`side-folder ${activeFolderParam === String(f.id) ? 'active' : ''}`}
            >
              {ICON.folder}
              <span className="side-folder-name">{f.name}</span>
              <span className="side-folder-count">{deckCounts.get(f.id) ?? 0}</span>
            </Link>
          ))}
        </div>
      )}

      <div className="side-foot">
        {streak !== undefined && streak > 0 && (
          <div className="streak-pill">
            <span className="dot" /> {streak}일 연속
          </div>
        )}
        {loggedIn ? (
          <div className="side-account">
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
          </div>
        ) : (
          // 비로그인 열람(UNLISTED 링크 진입 등) — 로그인으로 유도
          <Link to="/login" className="btn-primary side-login-btn">
            로그인
          </Link>
        )}
      </div>
    </div>
  )
}
