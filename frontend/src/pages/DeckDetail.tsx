import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { fetchAllCards, type CardDto } from '../api/cards'
import TopNav from '../components/TopNav'
import SpeakButton from '../components/SpeakButton'
import Ruby from '../components/Ruby'

interface Deck {
  id: number
  title: string
  description: string | null
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
  // 덱 제목·설명 인라인 수정 (8/28, 동결 예외 — 기존 PATCH /decks/{id} 재사용)
  const [editingDeck, setEditingDeck] = useState(false)
  const [dTitle, setDTitle] = useState('')
  const [dDesc, setDDesc] = useState('')
  // 카드 인라인 수정 — 한 번에 한 행만 (기존 PATCH /cards/{id} 재사용)
  const [editingCardId, setEditingCardId] = useState<number | null>(null)
  const [eFront, setEFront] = useState('')
  const [eReading, setEReading] = useState('')
  const [eBack, setEBack] = useState('')
  const [saving, setSaving] = useState(false)

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

  // 공개 범위 변경 — 백엔드(updateVisibility)는 PUBLIC 전환 시 랭킹 등록/제거까지 처리 (ADR-032)
  async function changeVisibility(v: Deck['visibility']) {
    if (!deck || v === deck.visibility) return
    if (v === 'PUBLIC' && !window.confirm('전체 공개하면 탐색에 노출되고 누구나 검색·복사할 수 있어요.\n공개할까요?')) {
      load()   // 셀렉트 표시값 원위치
      return
    }
    try {
      await api(`/decks/${id}/visibility`, { method: 'PATCH', body: JSON.stringify({ visibility: v }) })
      load()
    } catch (e) {
      setError((e as Error).message)
    }
  }

  function startDeckEdit() {
    if (!deck) return
    setDTitle(deck.title)
    setDDesc(deck.description ?? '')
    setEditingDeck(true)
  }

  async function saveDeck() {
    if (saving || !dTitle.trim()) return
    setSaving(true)
    try {
      await api(`/decks/${id}`, {
        method: 'PATCH',
        body: JSON.stringify({ title: dTitle.trim(), description: dDesc.trim() }),
      })
      setEditingDeck(false)
      load()
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setSaving(false)
    }
  }

  function startCardEdit(c: CardDto) {
    setEditingCardId(c.id)
    setEFront(c.front)
    setEReading(c.reading ?? '')
    setEBack(c.back)
  }

  async function saveCard() {
    if (saving || editingCardId === null || !eFront.trim() || !eBack.trim()) return
    setSaving(true)
    try {
      // reading은 빈 문자열도 그대로 보낸다 — 백엔드가 ""를 "읽기 삭제"로 처리
      await api(`/cards/${editingCardId}`, {
        method: 'PATCH',
        body: JSON.stringify({ front: eFront.trim(), reading: eReading.trim(), back: eBack.trim() }),
      })
      setEditingCardId(null)
      load()
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setSaving(false)
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
            {editingDeck ? (
              <div className="deck-edit-form">
                <input
                  aria-label="덱 이름"
                  value={dTitle}
                  onChange={(e) => setDTitle(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && saveDeck()}
                  maxLength={100}
                  autoFocus
                />
                <input
                  aria-label="덱 설명"
                  placeholder="설명 (선택)"
                  value={dDesc}
                  onChange={(e) => setDDesc(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && saveDeck()}
                  maxLength={255}
                />
                <div className="edit-actions">
                  <button className="btn-primary" disabled={saving || !dTitle.trim()} onClick={saveDeck}>
                    {saving ? '저장 중...' : '저장'}
                  </button>
                  <button className="btn-ghost-link" disabled={saving} onClick={() => setEditingDeck(false)}>취소</button>
                </div>
              </div>
            ) : (
              <h1>
                {deck?.title ?? '...'}
                {deck && (
                  <button className="edit-btn" title="제목·설명 수정" aria-label="덱 제목·설명 수정" onClick={startDeckEdit}>✏️</button>
                )}
              </h1>
            )}
            {!editingDeck && deck?.description && <p className="deck-desc">{deck.description}</p>}
            <p className="sub" style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
              카드 {cards.length}장{deck ? ` · 별표 ${deck.starredCount}장` : ''}
              <select
                className="vis-select"
                value={deck?.visibility ?? 'PRIVATE'}
                disabled={!deck}
                onChange={(e) => changeVisibility(e.target.value as Deck['visibility'])}
                aria-label="공개 범위"
              >
                <option value="PRIVATE">🔒 비공개 — 나만</option>
                <option value="UNLISTED">🔗 링크 공개 — 링크 아는 사람만</option>
                <option value="PUBLIC">🌐 전체 공개 — 탐색 노출·복사 가능</option>
              </select>
            </p>
          </div>
          <div className="mode-buttons">
            {cards.length > 0 ? (
              <Link to={`/study?deckId=${id}`} className="btn-primary" style={{ textDecoration: 'none' }}>
                복습 학습 (Leitner)
              </Link>
            ) : (
              <button className="btn-primary" disabled title="카드를 먼저 추가하세요">복습 학습 (Leitner)</button>
            )}
            {deck !== null && deck.starredCount > 0 && (
              <Link to={`/study?deckId=${id}&starredOnly=1`} className="mode-btn" title={`★ 표시한 ${deck.starredCount}장만 복습`}>
                ⭐만
              </Link>
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
            {cards.length >= 1 ? (
              <Link to={`/listening/${id}`} className="mode-btn">듣기</Link>
            ) : (
              <button className="mode-stub" disabled title="카드를 먼저 추가하세요">듣기</button>
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
          {cards.map((c, i) =>
            c.id === editingCardId ? (
              <div key={c.id} className="word-row row-edit">
                <span className="word-idx">{i + 1}</span>
                <input aria-label="단어" value={eFront} onChange={(e) => setEFront(e.target.value)} maxLength={255} autoFocus />
                <input aria-label="읽기" className="reading-input" placeholder="읽기 (선택)" value={eReading} onChange={(e) => setEReading(e.target.value)} maxLength={200} />
                <input aria-label="뜻" value={eBack} onChange={(e) => setEBack(e.target.value)} maxLength={255} onKeyDown={(e) => e.key === 'Enter' && saveCard()} />
                <div className="row-actions">
                  <button className="btn-primary btn-sm" disabled={saving || !eFront.trim() || !eBack.trim()} onClick={saveCard}>저장</button>
                  <button className="btn-ghost-link" disabled={saving} onClick={() => setEditingCardId(null)}>취소</button>
                </div>
              </div>
            ) : (
              <div key={c.id} className="word-row">
                <span className="word-idx">{i + 1}</span>
                <span className="word-front"><Ruby front={c.front} reading={c.reading} /> <SpeakButton text={c.reading || c.front} /></span>
                <span className="word-back">{c.back}</span>
                <div className="row-actions">
                  <button className="edit-btn" title="카드 수정" aria-label={`${c.front} 수정`} onClick={() => startCardEdit(c)}>
                    ✏️
                  </button>
                  <button className={`star-btn ${c.starred ? 'on' : ''}`} title={c.starred ? '별표 해제' : '별표'} aria-pressed={c.starred} onClick={() => toggleStar(c.id)}>
                    ★
                  </button>
                  <button className="del-btn" title="카드 삭제" aria-label={`${c.front} 삭제`} onClick={() => removeCard(c.id, c.front)}>
                    🗑
                  </button>
                </div>
              </div>
            )
          )}
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
