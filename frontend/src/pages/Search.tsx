import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import type { PageResp, PublicDeck } from '../api/publicDecks'
import TopNav from '../components/TopNav'
import { t } from '../i18n'

interface MyDeck {
  id: number
  title: string
  description: string | null
  cardCount: number
}

const MY_PREVIEW = 8   // 내 덱이 많으면(예: 57개) 처음엔 이만큼만 — 공개 덱이 아래로 밀리지 않게

// 위쪽 바 검색의 결과 화면 (11/14 사용자 요청 — Quizlet처럼 덱을 바로 찾기).
// 검색은 '공개된 덱을 찾는 것'이 기본이라 공개 덱이 먼저, 내 덱(비공개 포함)이 그 아래 (11/14 사용자 피드백).
// · 공개 덱: 탐색 화면과 같은 검색 API, 첫 12개 — 내 덱도 '내 덱' 표시로 같이 보인다(탐색 화면과 같게).
//   받은 뒤 내 덱을 빼면, 첫 12개가 다 내 덱일 때 뒤에 남의 덱이 있어도 '일치하는 덱이 없어요'가 됐다 (Codex 검산 11/14).
//   더 있으면 '탐색에서 더 보기'로 이어서 본다
// · 내 덱: 이름·설명에 검색어가 든 것 (내 덱 목록은 이미 한 번에 다 오니 화면에서 거른다)
// 폰에선 위쪽 바에 검색창 대신 돋보기만 있어서, 이 화면 맨 위 검색창으로 찾는다.
export default function Search() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const q = (params.get('q') ?? '').trim()
  const [input, setInput] = useState(q)
  const [mine, setMine] = useState<MyDeck[] | null>(null)
  const [publicDecks, setPublicDecks] = useState<PublicDeck[] | null>(null)
  const [morePublic, setMorePublic] = useState(false)
  const [showAllMine, setShowAllMine] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    setInput(q)
    setShowAllMine(false)   // 새 검색이면 내 덱은 다시 접는다
  }, [q])

  useEffect(() => {
    let alive = true
    api<MyDeck[]>('/decks')
      .then((d) => { if (alive) setMine(d) })
      .catch((e) => { if (alive) setError((e as Error).message) })
    return () => { alive = false }
  }, [])

  useEffect(() => {
    if (!q) return
    let alive = true   // 빠르게 다시 검색하면 늦게 도착한 옛 결과가 새 결과를 덮지 않게
    setPublicDecks(null)
    setError('')   // 새 검색 — 지난 검색의 오류 문구는 지운다 (Codex 검산 11/14: 실패 뒤 성공해도 오류가 남았다)
    api<PageResp<PublicDeck>>(`/public/decks?${new URLSearchParams({ keyword: q, size: '12', sort: 'popular' })}`)
      .then((p) => {
        if (!alive) return
        setPublicDecks(p.content)
        setMorePublic(p.totalPages > 1)
      })
      .catch((e) => { if (alive) setError((e as Error).message) })
    return () => { alive = false }
  }, [q])

  const myHits = useMemo(() => {
    if (!q || !mine) return []
    const needle = q.toLowerCase()
    return mine.filter((d) => d.title.toLowerCase().includes(needle) || (d.description ?? '').toLowerCase().includes(needle))
  }, [mine, q])
  const myShown = showAllMine ? myHits : myHits.slice(0, MY_PREVIEW)

  function submit(e: FormEvent) {
    e.preventDefault()
    const v = input.trim()
    navigate(v ? `/search?q=${encodeURIComponent(v)}` : '/search')
  }

  return (
    <>
      <TopNav />
      <div className="shell">
        <div className="page-head">
          <div>
            <h1>{t('검색')}</h1>
            {q && <p className="sub">{t('"{q}" 검색 결과', { q })}</p>}
          </div>
        </div>

        <form className="search-page-form" role="search" onSubmit={submit}>
          <input
            aria-label={t('덱 검색')}
            placeholder={t('덱 검색 (내 덱·공개 덱)')}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            autoFocus={!q}
          />
          <button className="btn-primary" type="submit">{t('검색')}</button>
        </form>

        {error && <p className="error" role="alert">{error}</p>}

        {!q ? (
          <p className="muted">{t('검색어를 입력하세요')}</p>
        ) : (
          <>
            <section className="search-section">
              <h2>
                {t('공개 덱')} {publicDecks && <span className="search-count">{morePublic ? `${publicDecks.length}+` : publicDecks.length}</span>}
              </h2>
              {publicDecks === null ? (
                !error && <p className="muted">{t('불러오는 중...')}</p>
              ) : publicDecks.length === 0 ? (
                <p className="muted">{t('일치하는 덱이 없어요')}</p>
              ) : (
                <div className="deck-grid">
                  {publicDecks.map((d) => (
                    <div key={d.id} className="deck-card">
                      <span className="tag">{d.mine ? t('내 덱') : d.authorNickname}</span>
                      <Link to={`/explore/${d.id}`} className="title-link">
                        <p className="title">{d.title}</p>
                      </Link>
                      <p className="meta">
                        {t('카드 {n}장', { n: d.cardCount })} · ♥ {d.likeCount}
                      </p>
                    </div>
                  ))}
                </div>
              )}
              {morePublic && (
                <p style={{ marginTop: 14 }}>
                  <Link to={`/explore?q=${encodeURIComponent(q)}`} className="search-more">{t('탐색에서 더 보기 →')}</Link>
                </p>
              )}
            </section>

            <section className="search-section">
              <h2>
                {t('내 덱')} {mine && <span className="search-count">{myHits.length}</span>}
              </h2>
              {mine === null ? (
                <p className="muted">{t('불러오는 중...')}</p>
              ) : myHits.length === 0 ? (
                <p className="muted">{t('일치하는 덱이 없어요')}</p>
              ) : (
                <div className="deck-grid">
                  {myShown.map((d) => (
                    <div key={d.id} className="deck-card">
                      <Link to={`/decks/${d.id}`} className="title-link">
                        <p className="title">{d.title}</p>
                      </Link>
                      <p className="meta">
                        {t('카드 {n}장', { n: d.cardCount })}{d.description ? ` · ${d.description}` : ''}
                      </p>
                    </div>
                  ))}
                </div>
              )}
              {!showAllMine && myHits.length > MY_PREVIEW && (
                <p style={{ marginTop: 14 }}>
                  <button type="button" className="search-more as-link" onClick={() => setShowAllMine(true)}>
                    {t('내 덱 {n}개 모두 보기', { n: myHits.length })}
                  </button>
                </p>
              )}
            </section>
          </>
        )}
      </div>
    </>
  )
}
