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

// 위쪽 바 검색의 결과 화면 (11/14 사용자 요청 — Quizlet처럼 덱을 바로 찾기).
// · 내 덱: 이름·설명에 검색어가 든 것 (내 덱 목록은 이미 한 번에 다 오니 화면에서 거른다)
// · 공개 덱: 탐색 화면과 같은 검색 API — 내 덱은 위에 이미 나오니 뺀다
// 폰에선 위쪽 바에 검색창 대신 돋보기만 있어서, 이 화면 맨 위 검색창으로 찾는다.
export default function Search() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const q = (params.get('q') ?? '').trim()
  const [input, setInput] = useState(q)
  const [mine, setMine] = useState<MyDeck[] | null>(null)
  const [publicDecks, setPublicDecks] = useState<PublicDeck[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => setInput(q), [q])

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
    api<PageResp<PublicDeck>>(`/public/decks?${new URLSearchParams({ keyword: q, size: '12', sort: 'popular' })}`)
      .then((p) => { if (alive) setPublicDecks(p.content.filter((d) => !d.mine)) })
      .catch((e) => { if (alive) setError((e as Error).message) })
    return () => { alive = false }
  }, [q])

  const myHits = useMemo(() => {
    if (!q || !mine) return []
    const needle = q.toLowerCase()
    return mine.filter((d) => d.title.toLowerCase().includes(needle) || (d.description ?? '').toLowerCase().includes(needle))
  }, [mine, q])

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
                {t('내 덱')} {mine && <span className="search-count">{myHits.length}</span>}
              </h2>
              {mine === null ? (
                <p className="muted">{t('불러오는 중...')}</p>
              ) : myHits.length === 0 ? (
                <p className="muted">{t('일치하는 덱이 없어요')}</p>
              ) : (
                <div className="deck-grid">
                  {myHits.map((d) => (
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
            </section>

            <section className="search-section">
              <h2>
                {t('공개 덱')} {publicDecks && <span className="search-count">{publicDecks.length}</span>}
              </h2>
              {publicDecks === null ? (
                !error && <p className="muted">{t('불러오는 중...')}</p>
              ) : publicDecks.length === 0 ? (
                <p className="muted">{t('일치하는 덱이 없어요')}</p>
              ) : (
                <div className="deck-grid">
                  {publicDecks.map((d) => (
                    <div key={d.id} className="deck-card">
                      <span className="tag">{d.authorNickname}</span>
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
            </section>
          </>
        )}
      </div>
    </>
  )
}
