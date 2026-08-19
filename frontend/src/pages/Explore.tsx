import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { afterCopy, copyDeckApi, toggleLikeApi, type PageResp, type PublicDeck } from '../api/publicDecks'
import TopNav from '../components/TopNav'

// 탐색 — Phase 4에서 만든 공개 API 3종(검색·복사·좋아요)의 새 화면.
// 좋아요 초기 상태·내 덱 여부는 서버 필드(likedByMe/mine) 사용 — 세션 내 추적 제거 (백로그 ①③).
export default function Explore() {
  const [decks, setDecks] = useState<PublicDeck[] | null>(null)
  const [keyword, setKeyword] = useState('')
  const [query, setQuery] = useState('')          // 실제 검색에 쓰인 값
  const [sort, setSort] = useState<'popular' | 'recent'>('popular')
  const [copiedIds, setCopiedIds] = useState<Record<number, boolean>>({})
  const [busyId, setBusyId] = useState<number | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    setDecks(null)
    const params = new URLSearchParams({ sort, size: '30' })
    if (query.trim()) params.set('keyword', query.trim())
    api<PageResp<PublicDeck>>(`/public/decks?${params}`)
      .then((p) => setDecks(p.content))
      .catch((e) => setError(e.message))
  }, [query, sort])

  async function toggleLike(deck: PublicDeck) {
    if (busyId) return
    setBusyId(deck.id)
    try {
      const res = await toggleLikeApi(deck)
      setDecks((ds) => (ds ?? []).map((d) => (d.id === deck.id ? { ...d, likedByMe: res.liked, likeCount: res.likeCount } : d)))
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusyId(null)
    }
  }

  async function copy(deck: PublicDeck) {
    if (busyId) return
    setBusyId(deck.id)
    try {
      await copyDeckApi(deck.id)
      setCopiedIds((m) => ({ ...m, [deck.id]: true }))
      setDecks((ds) => (ds ?? []).map((d) => (d.id === deck.id ? afterCopy(d) : d)))
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusyId(null)
    }
  }

  return (
    <>
      <TopNav />
      <div className="shell">
        <div className="page-head">
          <div>
            <h1>탐색</h1>
            <p className="sub">다른 사람들의 공개 단어장을 검색하고, 내 덱으로 복사하세요</p>
          </div>
        </div>

        <div className="create-row">
          <input
            aria-label="공개 덱 검색"
            placeholder="제목이나 설명으로 검색"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && setQuery(keyword)}
          />
          <button className="btn-primary" onClick={() => setQuery(keyword)}>
            검색
          </button>
        </div>

        <div className="sort-tabs">
          <button className={sort === 'popular' ? 'active' : ''} onClick={() => setSort('popular')}>
            인기순
          </button>
          <button className={sort === 'recent' ? 'active' : ''} onClick={() => setSort('recent')}>
            최신순
          </button>
        </div>

        {error && <p className="error" role="alert">{error}</p>}
        {decks === null && !error && <p className="muted">불러오는 중...</p>}

        <div className="deck-grid">
          {(decks ?? []).map((d) => (
            <div key={d.id} className="deck-card explore-card">
              <span className="tag">{d.mine ? '내 덱' : d.authorNickname}</span>
              <Link to={`/explore/${d.id}`} className="title-link">
                <p className="title">{d.title}</p>
              </Link>
              <p className="meta">
                카드 {d.cardCount}장{d.description ? ` · ${d.description}` : ''}
              </p>
              <div className="explore-actions">
                <button
                  className={`like-btn ${d.likedByMe ? 'on' : ''}`}
                  disabled={busyId === d.id}
                  onClick={() => toggleLike(d)}
                  aria-label={d.likedByMe ? '좋아요 취소' : '좋아요'}
                  aria-pressed={d.likedByMe}
                >
                  ♥ {d.likeCount}
                </button>
                <span className="muted" style={{ fontSize: 12.5 }}>복사 {d.copyCount}</span>
                <button
                  className="btn-primary copy-btn"
                  disabled={busyId === d.id || copiedIds[d.id]}
                  onClick={() => copy(d)}
                >
                  {copiedIds[d.id] ? '복사됨 ✓' : '내 덱으로 복사'}
                </button>
              </div>
            </div>
          ))}
          {decks !== null && decks.length === 0 && !error && (
            <p className="muted">
              {query ? '검색 결과가 없어요.' : '아직 공개 덱이 없어요 — 내 덱을 공개해서 첫 주자가 되어보세요.'}
            </p>
          )}
        </div>
      </div>
    </>
  )
}
