import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { fetchAllCards, type CardDto, type LearningStatus } from '../api/cards'
import TopNav from '../components/TopNav'
import SpeakButton from '../components/SpeakButton'
import Ruby from '../components/Ruby'
import TestSheetDialog from '../components/TestSheetDialog'
import { isPlainEnter } from '../lib/keys'
import { t } from '../i18n'

interface Deck {
  id: number
  title: string
  description: string | null
  visibility: 'PRIVATE' | 'PUBLIC' | 'UNLISTED'
  cardCount: number
  starredCount: number
}

const STATUS_LABEL: Record<LearningStatus, string> = {
  UNKNOWN: t('몰라요'),
  KNOWN: t('알아요'),
}
const STATUS_GROUPS: LearningStatus[] = ['UNKNOWN', 'KNOWN']

export default function DeckDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [deck, setDeck] = useState<Deck | null>(null)
  const [cards, setCards] = useState<CardDto[]>([])
  const [cardsLoading, setCardsLoading] = useState(true)
  const [cardsLoaded, setCardsLoaded] = useState(false)
  const loadRequest = useRef({ version: 0 })
  const [front, setFront] = useState('')
  const [back, setBack] = useState('')
  const [reading, setReading] = useState('')   // 읽기(요미가나) — 선택
  const [memo, setMemo] = useState('')         // 설명 줄 — 선택 (11/14)
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
  const [eMemo, setEMemo] = useState('')
  const [saving, setSaving] = useState(false)
  const [sheetOpen, setSheetOpen] = useState(false)   // 📝 종이 시험지 (Word) 설정 창

  const load = useCallback(() => {
    const requests = loadRequest.current
    const request = ++requests.version
    setCardsLoading(true)
    setCardsLoaded(false)
    setError('')
    Promise.all([api<Deck>(`/decks/${id}`), fetchAllCards(id!)])
      .then(([nextDeck, { cards: nextCards }]) => {
        if (request !== requests.version) return
        setDeck(nextDeck)
        setCards(nextCards)
        setCardsLoaded(true)
      })
      .catch((e) => {
        if (request === requests.version) setError(e.message)
      })
      .finally(() => {
        if (request === requests.version) setCardsLoading(false)
      })
  }, [id])

  useEffect(() => {
    const requests = loadRequest.current
    setDeck(null)
    setEditingCardId(null)
    load()
    return () => { requests.version++ }
  }, [load])

  const cardGroups = STATUS_GROUPS.map((status) => ({
    status,
    cards: cards.map((card, index) => ({ card, number: index + 1 }))
      .filter(({ card }) => card.learningStatus === status),
  }))

  async function addCard() {
    if (adding || !front.trim() || !back.trim()) return // 더블클릭 중복 등록 방어
    setAdding(true)
    try {
      await api(`/decks/${id}/cards`, { method: 'POST', body: JSON.stringify({ front, reading, back, memo }) })
      setFront('')
      setReading('')
      setBack('')
      setMemo('')
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
    if (!window.confirm(t('"{word}" 카드를 삭제할까요?\n이 카드의 학습 기록도 함께 사라져요.', { word: frontText }))) return
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
    if (v === 'PUBLIC' && !window.confirm(t('전체 공개하면 탐색에 노출되고 누구나 검색·복사할 수 있어요.\n공개할까요?'))) {
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
    setEMemo(c.memo ?? '')
  }

  async function saveCard() {
    if (saving || editingCardId === null || !eFront.trim() || !eBack.trim()) return
    setSaving(true)
    try {
      // reading·memo는 빈 문자열도 그대로 보낸다 — 백엔드가 ""를 "읽기·설명 삭제"로 처리
      await api(`/cards/${editingCardId}`, {
        method: 'PATCH',
        body: JSON.stringify({ front: eFront.trim(), reading: eReading.trim(), back: eBack.trim(), memo: eMemo.trim() }),
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
    if (!window.confirm(t('덱 "{title}"을(를) 삭제할까요?\n카드 {n}장과 학습·퀴즈·타이핑 기록이 전부 사라져요.', { title: deck.title, n: cards.length }))) return
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
          <Link to="/decks" className="hero-secondary">{t('← 내 덱')}</Link>
          <button className="danger-link" onClick={removeDeck}>{t('덱 삭제')}</button>
        </p>

        <div className="page-head">
          <div>
            {editingDeck ? (
              <div className="deck-edit-form">
                <input
                  aria-label={t('덱 이름')}
                  value={dTitle}
                  onChange={(e) => setDTitle(e.target.value)}
                  onKeyDown={(e) => isPlainEnter(e) && saveDeck()}
                  maxLength={100}
                  autoFocus
                />
                <input
                  aria-label={t('덱 설명')}
                  placeholder={t('덱 설명 (선택)')}
                  value={dDesc}
                  onChange={(e) => setDDesc(e.target.value)}
                  onKeyDown={(e) => isPlainEnter(e) && saveDeck()}
                  maxLength={255}
                />
                <div className="edit-actions">
                  <button className="btn-primary" disabled={saving || !dTitle.trim()} onClick={saveDeck}>
                    {saving ? t('저장 중...') : t('저장')}
                  </button>
                  <button className="btn-ghost-link" disabled={saving} onClick={() => setEditingDeck(false)}>{t('취소')}</button>
                </div>
              </div>
            ) : (
              <h1>
                {deck?.title ?? '...'}
                {deck && (
                  <button className="edit-btn" title={t('제목·설명 수정')} aria-label={t('덱 제목·설명 수정')} onClick={startDeckEdit}>{t('수정')}</button>
                )}
              </h1>
            )}
            {!editingDeck && deck?.description && <p className="deck-desc">{deck.description}</p>}
            <p className="sub" style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
              {cardsLoaded ? t('카드 {n}장 · 별표 {starred}장', { n: cards.length, starred: deck?.starredCount ?? 0 }) : cardsLoading ? t('카드 불러오는 중...') : t('카드를 불러오지 못했어요')}
              <select
                className="vis-select"
                value={deck?.visibility ?? 'PRIVATE'}
                disabled={!deck}
                onChange={(e) => changeVisibility(e.target.value as Deck['visibility'])}
                aria-label={t('공개 범위')}
              >
                <option value="PRIVATE">{t('🔒 비공개 — 나만')}</option>
                <option value="UNLISTED">{t('🔗 링크 공개 — 링크 아는 사람만')}</option>
                <option value="PUBLIC">{t('🌐 전체 공개 — 탐색 노출·복사 가능')}</option>
              </select>
            </p>
          </div>
          <div className="mode-buttons">
            {cardsLoaded && cards.length > 0 ? (
              <Link to={`/study?deckId=${id}`} className="btn-primary" style={{ textDecoration: 'none' }}>
                {t('복습 학습 (Leitner)')}
              </Link>
            ) : (
              <button className="btn-primary" disabled title={t('카드를 먼저 추가하세요')}>{t('복습 학습 (Leitner)')}</button>
            )}
            {/* 별표 카드만 골라 Leitner 복습을 '시작'하는 유일한 진입점 (9/3 문구 정정).
                Study 화면에는 카드별 별표 토글은 있지만 '별표만 골라 시작'하는 범위 선택이 없다 —
                이 버튼을 없애면 별표 복습 진입 경로 자체가 사라진다. 로직 무변경, 표시만 변경 */}
            {cardsLoaded && deck !== null && deck.starredCount > 0 && (
              <Link to={`/study?deckId=${id}&starredOnly=1`} className="mode-btn" title={t('★ 표시한 {n}장만 복습', { n: deck.starredCount })}>
                {t('⭐ 별표 복습')}
              </Link>
            )}
            {cardsLoaded && cards.length >= 2 ? (
              <Link to={`/quiz/${id}`} className="mode-btn">{t('퀴즈')}</Link>
            ) : (
              <button className="mode-stub" disabled title={t('퀴즈는 카드 2장부터 (오답지가 필요해요)')}>{t('퀴즈')}</button>
            )}
            {cardsLoaded && cards.length >= 1 ? (
              <Link to={`/typing/${id}`} className="mode-btn">{t('타이핑')}</Link>
            ) : (
              <button className="mode-stub" disabled title={t('카드를 먼저 추가하세요')}>{t('타이핑')}</button>
            )}
            {cardsLoaded && cards.length >= 1 ? (
              <Link to={`/listening/${id}`} className="mode-btn">{t('듣기')}</Link>
            ) : (
              <button className="mode-stub" disabled title={t('카드를 먼저 추가하세요')}>{t('듣기')}</button>
            )}
            {cardsLoaded && cards.length >= 1 ? (
              <button type="button" className="mode-btn" onClick={() => setSheetOpen(true)} title={t('종이(Word) 또는 온라인으로 푸는 쓰기 시험')}>
                {t('시험지')}
              </button>
            ) : (
              <button className="mode-stub" disabled title={t('카드를 먼저 추가하세요')}>{t('시험지')}</button>
            )}
          </div>
        </div>

        <div className="create-row">
          <input
            aria-label={t('단어')}
            placeholder={t('단어 (예: 会議)')}
            value={front}
            onChange={(e) => setFront(e.target.value)}
          />
          <input
            aria-label={t('읽기')}
            className="reading-input"
            placeholder={t('읽기 (예: かいぎ, 선택)')}
            value={reading}
            onChange={(e) => setReading(e.target.value)}
            maxLength={200}
          />
          <input
            aria-label={t('뜻')}
            placeholder={t('뜻 (예: 회의)')}
            value={back}
            onChange={(e) => setBack(e.target.value)}
            onKeyDown={(e) => isPlainEnter(e) && addCard()}
          />
          <input
            aria-label={t('설명')}
            className="memo-input"
            placeholder={t('설명 (선택)')}
            value={memo}
            onChange={(e) => setMemo(e.target.value)}
            onKeyDown={(e) => isPlainEnter(e) && addCard()}
            maxLength={500}
          />
          <button className="btn-primary" disabled={adding || !front.trim() || !back.trim()} onClick={addCard}>
            {adding ? t('추가 중...') : t('추가')}
          </button>
        </div>
        <p className="muted" style={{ margin: '-8px 0 18px', fontSize: 12.5 }}>
          {t('여러 장을 한 번에?')} <Link to={`/decks/${id}/import`} style={{ color: 'var(--a)', fontWeight: 600 }}>{t('일괄 가져오기 →')}</Link>
        </p>

        {error && <p className="error" role="alert">{error}</p>}

        <section className="learning-summary" aria-labelledby="learning-title" aria-busy={cardsLoading}>
          <h2 id="learning-title">{t('이 덱의 단어')}{cardsLoaded ? ` (${cards.length})` : ''}</h2>
          <p className="learning-rule">
            {t('3회 연속 맞히면 알아요, 한 번 틀리면 다시 몰라요. 처음 보는 단어도 몰라요에 포함돼요.')}
          </p>
        </section>

        <div aria-busy={cardsLoading}>
          {cardsLoading && <p className="word-list-message muted" role="status">{t('단어와 학습 상태를 불러오는 중...')}</p>}
          {!cardsLoading && !cardsLoaded && (
            <div className="word-list-message">
              <p className="muted">{t('단어와 학습 상태를 확인하지 못했어요.')}</p>
              <button className="btn-primary btn-sm" onClick={load}>{t('다시 시도')}</button>
            </div>
          )}
          {cardsLoaded && cards.length > 0 && cardGroups.map((group) => (
            <section key={group.status} className="learning-group" aria-labelledby={`group-${group.status}`}>
              <h3 id={`group-${group.status}`} className={`learning-group-title status-${group.status.toLowerCase()}`}>
                {STATUS_LABEL[group.status]} ({group.cards.length})
              </h3>
              {group.cards.length === 0 ? (
                <p className="muted learning-group-empty">{t('아직 ‘{status}’ 상태인 단어가 없어요.', { status: STATUS_LABEL[group.status] })}</p>
              ) : (
                <div className="word-list">
                  {group.cards.map(({ card: c, number }) =>
                    c.id === editingCardId ? (
                      <div key={c.id} className="word-row row-edit">
                        <span className="word-idx">{number}</span>
                        <input aria-label={t('단어')} value={eFront} onChange={(e) => setEFront(e.target.value)} maxLength={255} autoFocus />
                        <input aria-label={t('읽기')} className="reading-input" placeholder={t('읽기 (선택)')} value={eReading} onChange={(e) => setEReading(e.target.value)} maxLength={200} />
                        <input aria-label={t('뜻')} value={eBack} onChange={(e) => setEBack(e.target.value)} maxLength={255} onKeyDown={(e) => isPlainEnter(e) && saveCard()} />
                        <input aria-label={t('설명')} className="memo-input" placeholder={t('설명 (선택 — 뜻 아래 작은 글씨)')} value={eMemo} onChange={(e) => setEMemo(e.target.value)} maxLength={500} onKeyDown={(e) => isPlainEnter(e) && saveCard()} />
                        <div className="row-actions">
                          <button className="btn-primary btn-sm" disabled={saving || !eFront.trim() || !eBack.trim()} onClick={saveCard}>{t('저장')}</button>
                          <button className="btn-ghost-link" disabled={saving} onClick={() => setEditingCardId(null)}>{t('취소')}</button>
                        </div>
                      </div>
                    ) : (
                      <div key={c.id} className="word-row owned-card-row">   {/* owned-card-row: 모바일 2줄 배치는 덱 상세 일반 행에만 (전역 .word-row 무변경) */}
                        <span className="word-idx">{number}</span>
                        <span className="word-front"><Ruby front={c.front} reading={c.reading} /> <SpeakButton text={c.reading || c.front} /></span>
                        <span className="word-back">
                          {c.back}
                          {c.memo && <span className="word-memo">{c.memo}</span>}
                        </span>
                        <div className="row-actions">
                          <button className="edit-btn" title={t('카드 수정')} aria-label={t('{word} 수정', { word: c.front })} onClick={() => startCardEdit(c)}>
                            {t('수정')}
                          </button>
                          <button className={`star-btn ${c.starred ? 'on' : ''}`} title={c.starred ? t('별표 해제') : t('별표')} aria-pressed={c.starred} onClick={() => toggleStar(c.id)}>
                            ★
                          </button>
                          <button className="del-btn" title={t('카드 삭제')} aria-label={t('{word} 삭제', { word: c.front })} onClick={() => removeCard(c.id, c.front)}>
                            🗑
                          </button>
                        </div>
                      </div>
                    )
                  )}
                </div>
              )}
            </section>
          ))}
          {cardsLoaded && cards.length === 0 && (
            <p className="muted word-list-message">
              {t('아직 카드가 없어요 — 위에서 첫 단어를 추가하면 학습을 시작할 수 있어요.')}
            </p>
          )}
        </div>
      </div>
      {sheetOpen && deck && (
        <TestSheetDialog deckId={id!} deckTitle={deck.title} cards={cards} onClose={() => setSheetOpen(false)} />
      )}
    </>
  )
}
