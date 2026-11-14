import { useEffect, useId, useMemo, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import type { PageResp, PublicDeck } from '../api/publicDecks'
import { getRecentStudy } from '../lib/recent'
import { useDropdown } from '../lib/useDropdown'
import { t } from '../i18n'
import { ICON } from './icons'

interface MyDeck {
  id: number
  title: string
  cardCount: number
}

interface Suggestion {
  to: string               // 누르면 갈 앱 경로
  title: string
  meta: string
  kind: 'mine' | 'public' | 'all'
}

const MAX_EACH = 5   // 칸마다 몇 개까지
const DEBOUNCE_MS = 250   // 치는 동안은 공개 덱 API를 부르지 않고, 잠깐 멈췄을 때만

/** 덱 이름에서 검색어와 맞는 부분을 굵게 (대소문자 무시, 첫 번째만) */
function highlight(title: string, q: string) {
  const at = q ? title.toLowerCase().indexOf(q.toLowerCase()) : -1
  if (at < 0) return title
  return (
    <>
      {title.slice(0, at)}
      <b>{title.slice(at, at + q.length)}</b>
      {title.slice(at + q.length)}
    </>
  )
}

// 위쪽 바 검색창 + 연관 검색 (11/14 사용자 요청 — 퀴즐렛처럼 치는 동안 덱이 바로 뜨게).
// · 비어 있을 때: 최근 학습한 덱(이 브라우저 기록, 홈의 '최근 학습 덱'과 같은 값) — 없으면 내 덱 앞쪽
// · 칠 때: 이름이 맞는 내 덱(이미 받은 목록에서 거름) + 공개 덱(탐색 검색 API) + 맨 아래 '모든 결과 보기'
// · ↑↓로 고르고 Enter로 연다. 아무것도 안 골랐으면 Enter = 검색 화면. Esc·바깥 클릭이면 닫힌다
export default function SearchBox() {
  const { pathname, search } = useLocation()
  const navigate = useNavigate()
  const box = useDropdown()
  const listId = useId()
  const inputRef = useRef<HTMLInputElement>(null)
  const [myDecks, setMyDecks] = useState<MyDeck[] | null>(null)
  const [publicHits, setPublicHits] = useState<PublicDeck[]>([])
  const [active, setActive] = useState(-1)

  // 검색창 글자는 검색 화면 주소(?q=)를 따라간다 — 다른 화면으로 가면 비워진다
  const urlQuery = pathname === '/search' ? (new URLSearchParams(search).get('q') ?? '') : ''
  const [query, setQuery] = useState(urlQuery)
  useEffect(() => setQuery(urlQuery), [urlQuery])

  // 화면을 옮기면 닫는다
  const closeBox = box.setOpen
  useEffect(() => closeBox(false), [pathname, search, closeBox])

  // 내 덱 목록 — 처음 펼칠 때 한 번만 받는다
  useEffect(() => {
    if (!box.open || myDecks !== null) return
    api<MyDeck[]>('/decks').then(setMyDecks).catch(() => setMyDecks([]))
  }, [box.open, myDecks])

  // 공개 덱 — 치다가 잠깐 멈췄을 때만 부르고, 늦게 온 옛 결과는 버린다
  const q = query.trim()
  useEffect(() => {
    if (!box.open || !q) {
      setPublicHits([])
      return
    }
    let alive = true
    const timer = window.setTimeout(() => {
      api<PageResp<PublicDeck>>(`/public/decks?${new URLSearchParams({ keyword: q, size: '10', sort: 'popular' })}`)
        .then((p) => { if (alive) setPublicHits(p.content.filter((d) => !d.mine)) })   // 내 덱은 위 칸에 이미 있다
        .catch(() => { if (alive) setPublicHits([]) })
    }, DEBOUNCE_MS)
    return () => {
      alive = false
      window.clearTimeout(timer)
    }
  }, [box.open, q])

  const recentLabel = q ? '' : getRecentStudy().length > 0 ? t('최근 학습') : t('내 덱')
  const items: Suggestion[] = useMemo(() => {
    const mine = myDecks ?? []
    const card = (d: MyDeck): Suggestion => ({
      to: `/decks/${d.id}`,
      title: d.title,
      meta: t('카드 {n}장', { n: d.cardCount }),
      kind: 'mine',
    })
    if (!q) {
      const recent = getRecentStudy()
        .map((e) => mine.find((d) => d.id === e.id))
        .filter((d): d is MyDeck => !!d)
      return (recent.length > 0 ? recent : mine).slice(0, MAX_EACH).map(card)
    }
    const needle = q.toLowerCase()
    const myHits = mine.filter((d) => d.title.toLowerCase().includes(needle)).slice(0, MAX_EACH).map(card)
    const pubHits = publicHits.slice(0, MAX_EACH).map((d): Suggestion => ({
      to: `/explore/${d.id}`,
      title: d.title,
      meta: `${d.authorNickname} · ${t('카드 {n}장', { n: d.cardCount })}`,
      kind: 'public',
    }))
    const all: Suggestion = { to: `/search?q=${encodeURIComponent(q)}`, title: t('"{q}" 모든 결과 보기', { q }), meta: '', kind: 'all' }
    return [...myHits, ...pubHits, all]
  }, [myDecks, publicHits, q])

  // 목록이 바뀌면 고른 줄을 처음으로
  useEffect(() => setActive(-1), [q, items.length])

  function go(to: string) {
    box.setOpen(false)
    inputRef.current?.blur()
    navigate(to)
  }

  function submit(e: FormEvent) {
    e.preventDefault()
    if (active >= 0 && items[active]) return go(items[active].to)
    go(q ? `/search?q=${encodeURIComponent(q)}` : '/search')
  }

  function onKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (e.nativeEvent.isComposing) return   // 한글 조합 중 ↑↓·Enter는 입력기 몫
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      if (items.length === 0) return
      e.preventDefault()
      box.setOpen(true)
      // -1(아무것도 안 고름, 검색창) → 0 → … → 마지막 → 다시 -1 로 돈다
      const step = e.key === 'ArrowDown' ? 1 : -1
      const n = items.length
      setActive((i) => {
        const next = i + step
        if (next < -1) return n - 1
        if (next >= n) return -1
        return next
      })
    }
  }

  const showList = box.open && items.length > 0
  const firstPublic = items.findIndex((s) => s.kind === 'public')
  const allIndex = items.findIndex((s) => s.kind === 'all')

  return (
    <div className="search-box" ref={box.ref}>
      <form className="top-search" role="search" onSubmit={submit}>
        {ICON.search}
        <input
          ref={inputRef}
          role="combobox"
          aria-label={t('덱 검색')}
          aria-autocomplete="list"
          aria-expanded={showList}
          aria-controls={listId}
          aria-activedescendant={showList && active >= 0 ? `${listId}-${active}` : undefined}
          placeholder={t('덱 검색 (내 덱·공개 덱)')}
          value={query}
          onChange={(e) => {
            setQuery(e.target.value)
            box.setOpen(true)
          }}
          onFocus={() => box.setOpen(true)}
          onKeyDown={onKeyDown}
        />
      </form>
      {showList && (
        <div className="search-suggest" role="listbox" id={listId} aria-label={t('덱 검색')}>
          {!q && <p className="suggest-head">{recentLabel}</p>}
          {q && items[0]?.kind === 'mine' && <p className="suggest-head">{t('내 덱')}</p>}
          {items.map((s, i) => (
            <div key={`${s.kind}-${s.to}`}>
              {i === firstPublic && <p className="suggest-head">{t('공개 덱')}</p>}
              {i === allIndex && i > 0 && <div className="top-menu-sep" />}
              <div
                id={`${listId}-${i}`}
                role="option"
                aria-selected={i === active}
                className={`suggest-item${i === active ? ' active' : ''}${s.kind === 'all' ? ' suggest-all' : ''}`}
                onMouseDown={(e) => e.preventDefault()}   // 누르는 순간 검색창이 포커스를 잃어 목록이 먼저 닫히지 않게
                onClick={() => go(s.to)}
                onMouseEnter={() => setActive(i)}
              >
                {s.kind === 'all' ? ICON.search : s.kind === 'public' ? ICON.explore : ICON.cards}
                <span className="suggest-text">
                  <span className="suggest-title">{s.kind === 'all' ? s.title : highlight(s.title, q)}</span>
                  {s.meta && <span className="suggest-meta">{s.meta}</span>}
                </span>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
