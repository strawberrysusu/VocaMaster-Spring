import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { fetchAllCards, type CardDto } from '../api/cards'
import TopNav from '../components/TopNav'
import SpeakButton from '../components/SpeakButton'

interface Deck {
  id: number
  title: string
  visibility: 'PRIVATE' | 'PUBLIC' | 'UNLISTED'
  cardCount: number
  starredCount: number
}

export default function DeckDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [deck, setDeck] = useState<Deck | null>(null)
  const [cards, setCards] = useState<CardDto[]>([])
  const [front, setFront] = useState('')
  const [back, setBack] = useState('')
  const [reading, setReading] = useState('')   // 읽기(요미가나) — 선택
  const [adding, setAdding] = useState(false)
  const [error, setError] = useState('')

  const load = useCallback(() => {
    api<Deck>(`/decks/${id}`).then(setDeck).catch((e) => setError(e.message))
    fetchAllCards(id!)
      .then(({ cards }) => setCards(cards))
      .catch((e) => setError(e.message))
  }, [id])

  useEffect(load, [load])

  async function addCard() {
    if (adding || !front.trim() || !back.trim()) return // 더블클릭 중복 등록 방어
    setAdding(true)
    try {
      await api(`/decks/${id}/cards`, { method: 'POST', body: JSON.stringify({ front, reading, back }) })
      setFront('')
      setReading('')
      setBack('')
      load()
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setAdding(false)
    }
  }

  async function toggleStar(cardId: number) {
    try {
      await api(`/cards/${cardId}/star`, { method: 'PATCH' })
      load()
    } catch (e) {
      setError((e as Error).message)
    }
  }

  // 삭제 = 학습 이력까지 함께 사라짐 (ADR-040 CASCADE) — 되돌릴 수 없으니 확인창
  async function removeCard(cardId: number, frontText: string) {
    if (!window.confirm(`"${frontText}" 카드를 삭제할까요?\n이 카드의 학습 기록도 함께 사라져요.`)) return
    try {
      await api(`/cards/${cardId}`, { method: 'DELETE' })
      load()
    } catch (e) {
      setError((e as Error).message)
    }
  }

  async function removeDeck() {
    if (!deck) return
    if (!window.confirm(`덱 "${deck.title}"을(를) 삭제할까요?\n카드 ${cards.length}장과 학습·퀴즈·타이핑 기록이 전부 사라져요.`)) return
    try {
      await api(`/decks/${id}`, { method: 'DELETE' })
      navigate('/decks', { replace: true })
    } catch (e) {
      setError((e as Error).message)
    }
  }

  return (
    <>
      <TopNav />
      <div className="shell">
        <p style={{ margin: '0 0 14px', display: 'flex', justifyContent: 'space-between' }}>
          <Link to="/decks" className="hero-secondary">← 내 덱</Link>
          <button className="danger-link" onClick={removeDeck}>덱 삭제</button>
        </p>

        <div className="page-head">
          <div>
            <h1>{deck?.title ?? '...'}</h1>
            <p className="sub">카드 {cards.length}장{deck ? ` · 별표 ${deck.starredCount}장` : ''}</p>
          </div>
          <div className="mode-buttons">
            {cards.length > 0 ? (
              <Link to={`/study?deckId=${id}`} className="btn-primary" style={{ textDecoration: 'none' }}>
                복습 학습 (Leitner)
              </Link>
            ) : (
              <button className="btn-primary" disabled title="카드를 먼저 추가하세요">복습 학습 (Leitner)</button>
            )}
            {cards.length >= 2 ? (
              <Link to={`/quiz/${id}`} className="mode-btn">퀴즈</Link>
            ) : (
              <button className="mode-stub" disabled title="퀴즈는 카드 2장부터 (오답지가 필요해요)">퀴즈</button>
            )}
            {cards.length >= 1 ? (
              <Link to={`/typing/${id}`} className="mode-btn">타이핑</Link>
            ) : (
              <button className="mode-stub" disabled title="카드를 먼저 추가하세요">타이핑</button>
            )}
          </div>
        </div>

        <div className="create-row">
          <input
            aria-label="단어"
            placeholder="단어 (예: 会議)"
            value={front}
            onChange={(e) => setFront(e.target.value)}
          />
          <input
            aria-label="읽기"
            className="reading-input"
            placeholder="읽기 (예: かいぎ, 선택)"
            value={reading}
            onChange={(e) => setReading(e.target.value)}
            maxLength={200}
          />
          <input
            aria-label="뜻"
            placeholder="뜻 (예: 회의)"
            value={back}
            onChange={(e) => setBack(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && addCard()}
          />
          <button className="btn-primary" disabled={adding || !front.trim() || !back.trim()} onClick={addCard}>
            {adding ? '추가 중...' : '추가'}
          </button>
        </div>
        <p className="muted" style={{ margin: '-8px 0 18px', fontSize: 12.5 }}>
          여러 장을 한 번에? <Link to={`/decks/${id}/import`} style={{ color: 'var(--a)', fontWeight: 600 }}>일괄 가져오기 →</Link>
        </p>

        {error && <p className="error" role="alert">{error}</p>}

        <div className="word-list">
          {cards.map((c, i) => (
            <div key={c.id} className="word-row">
              <span className="word-idx">{i + 1}</span>
              <span className="word-front">{c.reading && <span className="reading-inline">{c.reading}</span>}{c.front} <SpeakButton text={c.reading || c.front} /></span>
              <span className="word-back">{c.back}</span>
              <div className="row-actions">
                <button className={`star-btn ${c.starred ? 'on' : ''}`} title={c.starred ? '별표 해제' : '별표'} aria-pressed={c.starred} onClick={() => toggleStar(c.id)}>
                  ★
                </button>
                <button className="del-btn" title="카드 삭제" aria-label={`${c.front} 삭제`} onClick={() => removeCard(c.id, c.front)}>
                  🗑
                </button>
              </div>
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
