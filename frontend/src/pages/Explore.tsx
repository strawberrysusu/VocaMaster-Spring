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
  // 페이지 넘김 (8/28) — 백엔드 Page는 원래 완비, 화면이 첫 페이지만 쓰고 있었다.
  // '더 보기'(이어 붙이기)가 아니라 페이지 교체 — 쌓이면 과부하가 되돌아온다 (사용자 피드백)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(1)

  useEffect(() => {
    setDecks(null)
    const params = new URLSearchParams({ sort, size: '30', page: String(page) })
    if (query.trim()) params.set('keyword', query.trim())
    let alive = true   // 빠르게 검색·정렬을 바꾸면 늦게 도착한 옛 응답이 새 결과를 덮는다 — 이 effect가 살아있을 때만 반영
    api<PageResp<PublicDeck>>(`/public/decks?${params}`)
      .then((p) => { if (alive) { setDecks(p.content); setTotalPages(Math.max(1, p.totalPages)) } })
      .catch((e) => { if (alive) setError(e.message) })
    return () => { alive = false }
  }, [query, sort, page])

  function goPage(p: number) {
    setPage(p)
    window.scrollTo({ top: 0, behavior: 'smooth' })   // 아래 페이저에서 눌러도 새 목록의 처음부터
  }

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
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                setQuery(keyword)
                setPage(0)
              }
            }}
          />
          <button className="btn-primary" onClick={() => { setQuery(keyword); setPage(0) }}>
            검색
          </button>
        </div>

        <div className="sort-tabs">
          <button className={sort === 'popular' ? 'active' : ''} onClick={() => { setSort('popular'); setPage(0) }}>
            인기순
          </button>
          <button className={sort === 'recent' ? 'active' : ''} onClick={() => { setSort('recent'); setPage(0) }}>
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

        {totalPages > 1 && (
          <div className="pager">
            <button disabled={page === 0} onClick={() => goPage(page - 1)}>◀ 이전</button>
            {Array.from({ length: totalPages }, (_, i) => (
              <button key={i} className={i === page ? 'active' : ''} onClick={() => goPage(i)}>
                {i + 1}
              </button>
            ))}
            <button disabled={page === totalPages - 1} onClick={() => goPage(page + 1)}>다음 ▶</button>
          </div>
        )}
      </div>
    </>
  )
}
