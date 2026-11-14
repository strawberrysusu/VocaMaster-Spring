import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { api, getToken, logout as clientLogout } from '../api/client'
import { getLang, t } from '../i18n'
import { jwtEmail } from '../lib/jwt'
import LangSwitch from './LangSwitch'

type Me = { id: number; email: string; nickname: string; provider: string; createdAt?: string }

interface Folder {
  id: number
  name: string
}

interface DeckLite {
  id: number
  folderId: number | null
}

// JWT payload에서 이메일 첫 글자 — 별도 /me API 없이 아바타 이니셜용.
// UTF-8로 읽고(jwt.ts), 첫 '글자'는 Array.from으로 — [0]은 이모지·확장 한자의 반쪽을 집는다 (11/14 검토)
function emailInitial(): string {
  return (Array.from(jwtEmail(getToken()))[0] ?? '?').toUpperCase()
}

// 라인 아이콘 (의존성 없이 인라인 SVG)
const svg = (size: number, children: ReactNode) => (
  <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    {children}
  </svg>
)
const ICON = {
  home: svg(18, <><path d="M3 10.5 12 3l9 7.5" /><path d="M5.5 9.5V21h13V9.5" /></>),
  decks: svg(18, <><rect x="4" y="7" width="14" height="13" rx="2.5" /><path d="M8 4h11a2 2 0 0 1 2 2v11" /></>),
  explore: svg(18, <><circle cx="12" cy="12" r="8.5" /><path d="m15.5 8.5-2 5-5 2 2-5z" /></>),
  stats: svg(18, <><path d="M5 20V13" /><path d="M12 20V5" /><path d="M19 20v-9" /></>),
  settings: svg(18, <><path d="M4 8h10" /><circle cx="17" cy="8" r="2.5" /><path d="M20 16H10" /><circle cx="7" cy="16" r="2.5" /></>),
  folder: svg(16, <path d="M3.5 6.5a2 2 0 0 1 2-2h4l2 2.5h7a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2z" />),
  search: svg(18, <><circle cx="11" cy="11" r="7" /><path d="m20 20-3.8-3.8" /></>),
  plus: svg(20, <><path d="M12 5v14" /><path d="M5 12h14" /></>),
  file: svg(18, <><path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z" /><path d="M14 3v5h5" /></>),
  shield: svg(18, <><path d="M12 3 5 6v6c0 4.2 3 7.6 7 9 4-1.4 7-4.8 7-9V6z" /></>),
  logout: svg(18, <><path d="M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3" /><path d="M10 16l-4-4 4-4" /><path d="M6 12h10" /></>),
}

const MENU = [
  { to: '/', label: t('홈'), icon: ICON.home, isActive: (p: string) => p === '/' },
  { to: '/decks', label: t('내 덱'), icon: ICON.decks, isActive: (p: string) => p.startsWith('/decks') || p.startsWith('/import') },
  { to: '/explore', label: t('탐색'), icon: ICON.explore, isActive: (p: string) => p.startsWith('/explore') || p.startsWith('/public') },
  { to: '/stats', label: t('통계'), icon: ICON.stats, isActive: (p: string) => p.startsWith('/stats') },
  { to: '/settings', label: t('설정'), icon: ICON.settings, isActive: (p: string) => p.startsWith('/settings') },
]

/** 펼침 메뉴 하나(+ 메뉴·프로필 메뉴) — 바깥을 누르거나 Esc면 닫힌다 */
function useDropdown() {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!open) return
    const onDown = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])
  return { open, setOpen, ref }
}

// 화면 틀 (11/14 사용자 요청 — Quizlet식): 위쪽 바 = 로고 · 가운데 덱 검색 · 오른쪽 + 만들기 · 🌐 언어 · 프로필,
// 왼쪽 사이드바 = 메뉴와 내 폴더만. 이 컴포넌트 하나를 모든 화면이 쓴다(이름은 예전 그대로 TopNav).
// 폰(≤768px)은 CSS가 사이드바 메뉴를 하단 탭바로 바꾸고, 위쪽 바의 검색창은 돋보기(→ 검색 화면)로 접는다.
export default function TopNav({ streak }: { streak?: number }) {
  const { pathname, search } = useLocation()
  const navigate = useNavigate()
  const [me, setMe] = useState<Me | null>(null)
  const [folders, setFolders] = useState<Folder[]>([])
  const [deckCounts, setDeckCounts] = useState<Map<number, number>>(new Map())
  const plusMenu = useDropdown()
  const profileMenu = useDropdown()
  const loggedIn = !!getToken()

  // 위쪽 검색창 글자는 검색 화면 주소(?q=)를 따라간다 — 다른 화면으로 가면 비워진다
  const urlQuery = pathname === '/search' ? (new URLSearchParams(search).get('q') ?? '') : ''
  const [query, setQuery] = useState(urlQuery)
  useEffect(() => setQuery(urlQuery), [urlQuery])

  // 학습 4모드는 몰입 모드 — 위쪽 바·사이드바(모바일 탭바 포함) 없이 풀스크린 (Quizlet 방식, 8/30 사용자 지적).
  // 각 학습 화면에 "← 덱으로" 탈출구가 이미 있다. 본문 여백은 CSS :has(.topbar)·:has(.sidenav)가 따라온다.
  const focusMode =
    pathname === '/study' ||
    pathname.startsWith('/quiz/') ||
    pathname.startsWith('/typing/') ||
    pathname.startsWith('/listening/')

  // 사이드바 폴더 목록 — 페이지 이동 시 + 같은 화면에서 폴더·소속이 바뀌었다는 알림
  // (vm:library-changed, Decks가 발화)마다 재조회 (Codex UI 검산 ②). 실패해도 조용히.
  useEffect(() => {
    if (!loggedIn || focusMode) return
    function refresh() {
      api<Folder[]>('/folders').then(setFolders).catch(() => {})
      api<DeckLite[]>('/decks')
        .then((ds) => {
          const m = new Map<number, number>()
          for (const d of ds) if (d.folderId !== null) m.set(d.folderId, (m.get(d.folderId) ?? 0) + 1)
          setDeckCounts(m)
        })
        .catch(() => {})
    }
    refresh()
    window.addEventListener('vm:library-changed', refresh)
    return () => window.removeEventListener('vm:library-changed', refresh)
  }, [pathname, loggedIn, focusMode])

  // 화면을 옮기면 펼친 메뉴는 닫는다
  const closePlus = plusMenu.setOpen
  const closeProfile = profileMenu.setOpen
  useEffect(() => {
    closePlus(false)
    closeProfile(false)
  }, [pathname, search, closePlus, closeProfile])

  const activeFolderParam = pathname.startsWith('/decks') ? new URLSearchParams(search).get('folder') : null

  // 아바타 → 프로필 메뉴 (처음 열 때만 /users/me 조회 — 못 불러와도 메뉴는 뜬다)
  async function toggleProfile() {
    const next = !profileMenu.open
    profileMenu.setOpen(next)
    if (next && !me) {
      try {
        setMe(await api<Me>('/users/me'))
      } catch {
        /* 이메일은 토큰에서 읽어 보여 준다 */
      }
    }
  }

  function submitSearch(e: FormEvent) {
    e.preventDefault()
    const q = query.trim()
    navigate(q ? `/search?q=${encodeURIComponent(q)}` : '/search')
  }

  async function logout() {
    await clientLogout()   // 진행 중인 토큰 갱신이 끝난 뒤 로그아웃하고 토큰을 지운다 (A1)
    navigate('/login')   // basename(/app)을 라우터가 처리 — 절대경로 /login은 API 보안에 걸림 (Codex 검산)
  }

  // 훅 선언이 모두 끝난 뒤의 조건부 렌더 (React 훅 순서 규칙)
  if (focusMode) return null

  return (
    <>
      <header className="topbar">
        <Link to="/" className="logo">
          <span className="logo-mark">語</span>
          <span className="logo-text">VocaMaster</span>
        </Link>

        <div className="top-center">
          {loggedIn && (
            <form className="top-search" role="search" onSubmit={submitSearch}>
              {ICON.search}
              <input
                aria-label={t('덱 검색')}
                placeholder={t('덱 검색 (내 덱·공개 덱)')}
                value={query}
                onChange={(e) => setQuery(e.target.value)}
              />
            </form>
          )}
        </div>

        <div className="top-actions">
          {loggedIn && (
            // 폰에선 검색창 대신 돋보기 — 검색 화면에 큰 검색창이 있다
            <Link to="/search" className="top-icon-btn top-search-icon" aria-label={t('덱 검색')}>
              {ICON.search}
            </Link>
          )}

          {loggedIn && (
            <div className="menu-wrap" ref={plusMenu.ref}>
              <button
                className="top-plus"
                aria-label={t('새로 만들기')}
                aria-haspopup="menu"
                aria-expanded={plusMenu.open}
                onClick={() => plusMenu.setOpen(!plusMenu.open)}
              >
                {ICON.plus}
              </button>
              {plusMenu.open && (
                <div className="top-menu" role="menu">
                  {/* 덱·폴더는 내 덱 화면의 칸·창을 그대로 연다 (Decks가 ?new=를 한 번 처리하고 지운다) */}
                  <button role="menuitem" onClick={() => navigate('/decks?new=deck')}>
                    {ICON.decks} {t('새 덱')}
                  </button>
                  <button role="menuitem" onClick={() => navigate('/import-files')}>
                    {ICON.file} {t('파일로 덱 만들기')}
                  </button>
                  <button role="menuitem" onClick={() => navigate('/decks?new=folder')}>
                    {ICON.folder} {t('새 폴더')}
                  </button>
                </div>
              )}
            </div>
          )}

          <LangSwitch />

          {loggedIn ? (
            <div className="menu-wrap" ref={profileMenu.ref}>
              <button
                className="avatar avatar-btn"
                aria-label={t('내 프로필')}
                aria-haspopup="menu"
                aria-expanded={profileMenu.open}
                onClick={toggleProfile}
              >
                {emailInitial()}
              </button>
              {profileMenu.open && (
                <div className="top-menu profile-menu" role="menu">
                  <div className="pm-head">
                    <span className="avatar">{emailInitial()}</span>
                    <div className="pm-who">
                      <p className="pp-nick">{me ? me.nickname : t('불러오는 중…')}</p>
                      <p className="pp-email">{me ? me.email : jwtEmail(getToken())}</p>
                      {me && (
                        <p className="pp-meta">
                          <span className="pp-badge">{me.provider === 'google' ? t('구글 연결됨') : t('이메일 가입')}</span>
                          {me.createdAt && <span>{t('{date} 가입', { date: me.createdAt.slice(0, 10) })}</span>}
                        </p>
                      )}
                    </div>
                  </div>
                  <div className="top-menu-sep" />
                  <Link role="menuitem" to="/settings">
                    {ICON.settings} {t('설정')}
                  </Link>
                  <a role="menuitem" href="/privacy.html" target="_blank" rel="noreferrer">
                    {ICON.shield} {getLang() === 'ko' ? t('개인정보처리방침') : t('개인정보처리방침 (한국어)')}
                  </a>
                  <div className="top-menu-sep" />
                  <button role="menuitem" onClick={logout}>
                    {ICON.logout} {t('로그아웃')}
                  </button>
                </div>
              )}
            </div>
          ) : (
            // 비로그인 열람(UNLISTED 링크 진입 등) — 로그인으로 유도
            <Link to="/login" className="btn-primary top-login-btn">
              {t('로그인')}
            </Link>
          )}
        </div>
      </header>

      <div className="sidenav">
        <nav className="side-menu" aria-label={t('주 메뉴')}>
          {MENU.map((m) => (
            <Link key={m.to} to={m.to} className={m.isActive(pathname) ? 'active' : ''}>
              {m.icon}
              <span>{m.label}</span>
            </Link>
          ))}
        </nav>

        {loggedIn && folders.length > 0 && (
          <div className="side-folders">
            <p className="side-folders-head">{t('내 폴더')}</p>
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

        {streak !== undefined && streak > 0 && (
          <div className="side-foot">
            <div className="streak-pill">
              <span className="dot" /> {t('{n}일 연속', { n: streak })}
            </div>
          </div>
        )}
      </div>
    </>
  )
}
