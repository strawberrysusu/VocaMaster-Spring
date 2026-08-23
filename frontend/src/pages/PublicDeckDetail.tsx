import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, getToken } from '../api/client'
import { afterCopy, copyDeckApi, toggleLikeApi, type PageResp, type PublicCard, type PublicDeck } from '../api/publicDecks'
import TopNav from '../components/TopNav'
import SpeakButton from '../components/SpeakButton'

// 공개 덱 상세 — 복사 전에 내용을 볼 수 있게 (백로그 ②). UNLISTED 링크 진입점이기도 함.
export default function PublicDeckDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [deck, setDeck] = useState<PublicDeck | null>(null)
  const [cards, setCards] = useState<PublicCard[] | null>(null)
  const [total, setTotal] = useState(0)
  const [busy, setBusy] = useState(false)
  const [copiedId, setCopiedId] = useState<number | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    api<PublicDeck>(`/public/decks/${id}`).then(setDeck).catch((e) => setError(e.message))
    api<PageResp<PublicCard>>(`/public/decks/${id}/cards?size=100`)
      .then((p) => {
        setCards(p.content)
        setTotal(p.totalElements)
      })
      .catch((e) => setError(e.message))
  }, [id])

  // 비로그인 열람은 허용, 행동(좋아요·복사)은 로그인 유도 — 돌아올 곳을 기억
  function requireLogin(): boolean {
    if (getToken()) return true
    navigate('/login', { state: { from: `/explore/${id}` } })
    return false
  }

  async function toggleLike() {
    if (!deck || busy || !requireLogin()) return
    setBusy(true)
    try {
      const res = await toggleLikeApi(deck)
      setDeck({ ...deck, likedByMe: res.liked, likeCount: res.likeCount })
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  async function copy() {
    if (!deck || busy || !requireLogin()) return
    setBusy(true)
    try {
      const created = await copyDeckApi(deck.id)
      setCopiedId(created.id)
      setDeck(afterCopy(deck))
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <TopNav />
      <div className="shell">
        <p style={{ margin: '0 0 14px' }}>
          <Link to="/explore" className="hero-secondary">← 탐색</Link>
        </p>

        {error && <p className="error" role="alert">{error}</p>}

        {deck && (
          <div className="page-head">
            <div>
              <span className="tag">{deck.mine ? '내 덱' : deck.authorNickname}</span>
              <h1 style={{ marginTop: 10 }}>{deck.title}</h1>
              <p className="sub">
                카드 {deck.cardCount}장{deck.description ? ` · ${deck.description}` : ''} · ♥ {deck.likeCount} · 복사 {deck.copyCount}
              </p>
            </div>
            <div className="mode-buttons">
              <button
                className={`like-btn ${deck.likedByMe ? 'on' : ''}`}
                disabled={busy}
                onClick={toggleLike}
                aria-pressed={deck.likedByMe}
              >
                ♥ {deck.likedByMe ? '좋아요 취소' : '좋아요'}
              </button>
              {copiedId ? (
                <button className="btn-primary" onClick={() => navigate(`/decks/${copiedId}`)}>
                  복사됨 ✓ 내 덱 보기
                </button>
              ) : (
                <button className="btn-primary" disabled={busy} onClick={copy}>
                  내 덱으로 복사
                </button>
              )}
            </div>
          </div>
        )}

        {cards === null && !error && <p className="muted">불러오는 중...</p>}

        {cards && (
          <div className="word-list">
            {cards.map((c, i) => (
              <div key={c.id} className="word-row">
                <span className="word-idx">{i + 1}</span>
                <span className="word-front">{c.reading && <span className="reading-inline">{c.reading}</span>}{c.front} <SpeakButton text={c.reading || c.front} /></span>
                <span className="word-back">{c.back}</span>
              </div>
            ))}
            {cards.length === 0 && <p className="muted" style={{ padding: '24px 4px' }}>이 덱에는 아직 카드가 없어요.</p>}
            {total > cards.length && (
              <p className="muted" style={{ padding: '14px 4px', fontSize: 13 }}>
                미리보기 {cards.length}장 / 전체 {total}장 — 복사하면 전부 가져옵니다
              </p>
            )}
          </div>
        )}
      </div>
    </>
  )
}
