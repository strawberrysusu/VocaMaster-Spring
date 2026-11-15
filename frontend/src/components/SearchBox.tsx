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
const PUBLIC_FETCH = 30   // 공개 덱은 넉넉히 받아서 내 덱을 뺀 뒤 5개를 고른다 — 10개만 받으면 앞쪽이 다 내 덱일 때 남의 덱이 안 보였다 (11/15 점검)
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
// · 칠 때: 이름이 맞는 내 덱(받아 둔 목록에서 거름) + 공개 덱(탐색 검색 API) + 맨 아래 '모든 결과 보기'
// · ↑↓로 고르고 Enter로 연다. 화살표로 고르지 않았으면 Enter = 검색 화면. Esc·바깥 클릭·Tab으로 나가면 닫힌다
export default function SearchBox() {
  const { pathname, search } = useLocation()
  const navigate = useNavigate()
  const box = useDropdown()
  const listId = useId()
  const inputRef = useRef<HTMLInputElement>(null)
  const [myDecks, setMyDecks] = useState<MyDeck[] | null>(null)
  const [publicHits, setPublicHits] = useState<PublicDeck[]>([])
  // 고른 줄은 '몇 번째'가 아니라 그 줄의 주소로 기억한다 — 공개 덱 결과가 늦게 와서 목록이 바뀌어도
  // 같은 번호의 다른 덱이 열리지 않게 (Codex 4차 검산 11/14). 목록에서 사라지면 자연히 '안 고름'(-1).
  // 화살표 키만 바꾼다 — 마우스가 올라간 줄까지 '고른 줄'로 치면, 가만히 둔 마우스 밑에 뜬 줄이
  // Enter의 행선지가 되어 검색 화면 대신 그 덱이 열렸다 (11/15 점검). 마우스 강조는 CSS :hover가 한다
  const [activeTo, setActiveTo] = useState<string | null>(null)

  // 검색창 글자는 검색 화면 주소(?q=)를 따라간다 — 다른 화면으로 가면 비워진다
  const urlQuery = pathname === '/search' ? (new URLSearchParams(search).get('q') ?? '') : ''
  const [query, setQuery] = useState(urlQuery)
  useEffect(() => setQuery(urlQuery), [urlQuery])

  // 화면을 옮기면 닫는다
  const closeBox = box.setOpen
  useEffect(() => closeBox(false), [pathname, search, closeBox])

  // 내 덱 목록 — 펼칠 때마다 다시 받는다(그사이 새로 만들거나 이름을 고친 덱이 바로 보이게).
  // 실패하면 앞서 받은 목록을 그대로 쓴다
  useEffect(() => {
    if (!box.open) return
    let alive = true
    api<MyDeck[]>('/decks')
      .then((d) => { if (alive) setMyDecks(d) })
      .catch(() => { if (alive) setMyDecks((prev) => prev ?? []) })
    return () => { alive = false }
  }, [box.open])

  // 공개 덱 — 치다가 잠깐 멈췄을 때만 부르고, 늦게 온 옛 결과는 버린다
  const q = query.trim()
  useEffect(() => {
    setPublicHits([])   // 검색어가 바뀌면 지난 검색어의 공개 덱은 바로 치운다 — 남겨 두면 새 검색어의 결과처럼 보여 골라진다
    if (!box.open || !q) return
    let alive = true
    const timer = window.setTimeout(() => {
      api<PageResp<PublicDeck>>(`/public/decks?${new URLSearchParams({ keyword: q, size: String(PUBLIC_FETCH), sort: 'popular' })}`)
        .then((p) => { if (alive) setPublicHits(p.content.filter((d) => !d.mine)) })   // 내 덱은 위 칸에 이미 있다
        .catch(() => { if (alive) setPublicHits([]) })
    }, DEBOUNCE_MS)
    return () => {
      alive = false
      window.clearTimeout(timer)
    }
  }, [box.open, q])

  // 보여 줄 줄과 (비어 있을 때의) 제목 — 제목은 실제로 보여 주는 목록에 맞춘다:
  // 학습 기록은 있는데 그 덱이 다 지워졌으면 '최근 학습'이 아니라 '내 덱'
  const { items, label } = useMemo((): { items: Suggestion[]; label: string } => {
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
      return {
        items: (recent.length > 0 ? recent : mine).slice(0, MAX_EACH).map(card),
        label: recent.length > 0 ? t('최근 학습') : t('내 덱'),
      }
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
    return { items: [...myHits, ...pubHits, all], label: '' }
  }, [myDecks, publicHits, q])

  const active = activeTo === null ? -1 : items.findIndex((s) => s.to === activeTo)
  const showList = box.open && items.length > 0

  // 검색어가 바뀌거나 닫히면 고른 줄을 푼다 — 안 풀면 Esc로 닫은 뒤의 Enter가 안 보이는 줄을 연다
  useEffect(() => setActiveTo(null), [q, box.open])

  // 화살표로 고른 줄이 목록의 보이는 범위 밖이면 따라 내려간다 (줄이 많으면 목록 안에서 스크롤된다)
  useEffect(() => {
    if (showList && active >= 0) document.getElementById(`${listId}-${active}`)?.scrollIntoView({ block: 'nearest' })
  }, [showList, active, listId])

  function go(to: string) {
    box.setOpen(false)
    inputRef.current?.blur()
    navigate(to)
  }

  function submit(e: FormEvent) {
    e.preventDefault()
    if (showList && active >= 0 && items[active]) return go(items[active].to)   // 목록이 열려 있고 화살표로 고른 줄이 있을 때만
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
      let next = active + step
      if (next < -1) next = n - 1
      if (next >= n) next = -1
      setActiveTo(next === -1 ? null : items[next].to)
    }
  }

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
          onClick={() => box.setOpen(true)}   // Esc로 닫은 뒤 다시 누르면 열린다 (포커스는 그대로라 onFocus가 안 온다)
          onKeyDown={onKeyDown}
        />
      </form>
      {showList && (
        <div className="search-suggest" role="listbox" id={listId} aria-label={t('덱 검색')}>
          {!q && <p className="suggest-head">{label}</p>}
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
