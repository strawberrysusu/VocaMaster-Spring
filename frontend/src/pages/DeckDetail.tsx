import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../api/client'
import TopNav from '../components/TopNav'

interface Deck {
  id: number
  title: string
  visibility: 'PRIVATE' | 'PUBLIC' | 'UNLISTED'
  cardCount: number
  starredCount: number
}

interface CardItem {
  id: number
  front: string
  back: string
  starred: boolean
}

interface Page<T> {
  content: T[]
  totalElements: number
}

export default function DeckDetail() {
  const { id } = useParams()
  const [deck, setDeck] = useState<Deck | null>(null)
  const [cards, setCards] = useState<CardItem[]>([])
  const [front, setFront] = useState('')
  const [back, setBack] = useState('')
  const [error, setError] = useState('')

  const load = useCallback(() => {
    api<Deck>(`/decks/${id}`).then(setDeck).catch((e) => setError(e.message))
    api<Page<CardItem>>(`/decks/${id}/cards?size=200`)
      .then((p) => setCards(p.content))
      .catch((e) => setError(e.message))
  }, [id])

  useEffect(load, [load])

  async function addCard() {
    if (!front.trim() || !back.trim()) return
    await api(`/decks/${id}/cards`, { method: 'POST', body: JSON.stringify({ front, back }) })
    setFront('')
    setBack('')
    load()
  }

  return (
    <>
      <TopNav />
      <div className="shell">
        <p style={{ margin: '0 0 14px' }}>
          <Link to="/decks" className="hero-secondary">← 내 덱</Link>
        </p>

        <div className="page-head">
          <div>
            <h1>{deck?.title ?? '...'}</h1>
            <p className="sub">카드 {cards.length}장{deck ? ` · 별표 ${deck.starredCount}장` : ''}</p>
          </div>
          <div className="mode-buttons">
            {cards.length > 0 ? (
              <Link to={`/study?deckId=${id}`} className="btn-primary" style={{ textDecoration: 'none' }}>
                플래시카드 학습
              </Link>
            ) : (
              <button className="btn-primary" disabled title="카드를 먼저 추가하세요">플래시카드 학습</button>
            )}
            <button className="mode-stub" title="다음 시공 화면">퀴즈</button>
            <button className="mode-stub" title="다음 시공 화면">타이핑</button>
          </div>
        </div>

        <div className="create-row">
          <input
            placeholder="단어 (예: 会議)"
            value={front}
            onChange={(e) => setFront(e.target.value)}
          />
          <input
            placeholder="뜻 (예: 회의)"
            value={back}
            onChange={(e) => setBack(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && addCard()}
          />
          <button className="btn-primary" disabled={!front.trim() || !back.trim()} onClick={addCard}>
            추가
          </button>
        </div>

        {error && <p className="error">{error}</p>}

        <div className="word-list">
          {cards.map((c, i) => (
            <div key={c.id} className="word-row">
              <span className="word-idx">{i + 1}</span>
              <span className="word-front">{c.front}</span>
              <span className="word-back">{c.back}</span>
              {c.starred && <span title="별표 카드">⭐</span>}
            </div>
          ))}
          {cards.length === 0 && (
            <p className="muted" style={{ padding: '24px 4px' }}>
              아직 카드가 없어요 — 위에서 첫 단어를 추가하면 학습을 시작할 수 있어요.
            </p>
          )}
        </div>
      </div>
    </>
  )
}
