import { useEffect, useState } from 'react'
import { api } from '../api/client'
import TopNav from '../components/TopNav'

interface PublicDeck {
  id: number
  title: string
  description: string
  authorNickname: string
  cardCount: number
  likeCount: number
  copyCount: number
}

interface PageResp {
  content: PublicDeck[]
  totalElements: number
}

interface LikeResponse {
  liked: boolean
  likeCount: number
}

// 탐색 — Phase 4에서 만든 공개 API 3종(검색·복사·좋아요)의 첫 새 화면.
// 좋아요 초기 상태(내가 눌렀는지)는 목록 API에 없어서 세션 내 토글로만 추적 — likedByMe 필드는 백엔드 후보.
export default function Explore() {
  const [decks, setDecks] = useState<PublicDeck[]>([])
  const [keyword, setKeyword] = useState('')
  const [query, setQuery] = useState('')          // 실제 검색에 쓰인 값
  const [sort, setSort] = useState<'popular' | 'recent'>('popular')
  const [liked, setLiked] = useState<Record<number, boolean>>({})
  const [copiedIds, setCopiedIds] = useState<Record<number, boolean>>({})
  const [busyId, setBusyId] = useState<number | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    const params = new URLSearchParams({ sort, size: '30' })
    if (query.trim()) params.set('keyword', query.trim())
    api<PageResp>(`/public/decks?${params}`)
      .then((p) => setDecks(p.content))
      .catch((e) => setError(e.message))
  }, [query, sort])

  async function toggleLike(deck: PublicDeck) {
    if (busyId) return
    setBusyId(deck.id)
    try {
      const method = liked[deck.id] ? 'DELETE' : 'POST'
      const res = await api<LikeResponse>(`/public/decks/${deck.id}/like`, { method })
      setLiked((m) => ({ ...m, [deck.id]: res.liked }))
      setDecks((ds) => ds.map((d) => (d.id === deck.id ? { ...d, likeCount: res.likeCount } : d)))
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
      await api(`/decks/${deck.id}/copy`, { method: 'POST' })
      setCopiedIds((m) => ({ ...m, [deck.id]: true }))
      setDecks((ds) => ds.map((d) => (d.id === deck.id ? { ...d, copyCount: d.copyCount + 1 } : d)))
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

        <div className="deck-grid">
          {decks.map((d) => (
            <div key={d.id} className="deck-card explore-card">
              <span className="tag">{d.authorNickname}</span>
              <p className="title">{d.title}</p>
              <p className="meta">
                카드 {d.cardCount}장{d.description ? ` · ${d.description}` : ''}
              </p>
              <div className="explore-actions">
                <button
                  className={`like-btn ${liked[d.id] ? 'on' : ''}`}
                  disabled={busyId === d.id}
                  onClick={() => toggleLike(d)}
                  aria-label="좋아요"
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
          {decks.length === 0 && !error && (
            <p className="muted">
              {query ? '검색 결과가 없어요.' : '아직 공개 덱이 없어요 — 내 덱을 공개해서 첫 주자가 되어보세요.'}
            </p>
          )}
        </div>
      </div>
    </>
  )
}
