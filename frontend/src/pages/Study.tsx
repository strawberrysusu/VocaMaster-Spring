import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import { fetchAllCards } from '../api/cards'
import TopNav from '../components/TopNav'
import SpeakButton from '../components/SpeakButton'

interface StudyCard {
  cardId: number
  front: string
  back: string
  reading?: string | null
}

interface AnswerResult {
  boxLevel: number
}

/**
 * Leitner 복습 학습 — 두 입구, 한 흐름:
 * - /study            → 오늘 복습 (due 카드 전체)
 * - /study?deckId=N   → 그 덱의 전 카드 (새 카드는 첫 답변으로 박스 1 입장)
 *
 * 설계 결정(2026-08-14): 답변은 /reviews/answer 하나만 호출한다.
 * 기존 StudySession/StudyRecord(플래시카드 세션 이력)는 여기서 기록되지 않음 —
 * 프런트에서 API 두 개를 따로 쏘면 한쪽만 성공하는 반쪽 상태가 생기므로,
 * "복습 기록 + 세션 이력"의 묶음은 Phase 6 백엔드 오케스트레이션(이벤트)에서 해결한다.
 */
export default function Study() {
  const [params] = useSearchParams()
  const deckId = params.get('deckId')

  const [queue, setQueue] = useState<StudyCard[] | null>(null)
  const [idx, setIdx] = useState(0)
  const [revealed, setRevealed] = useState(false)
  const [known, setKnown] = useState(0)
  const [unknown, setUnknown] = useState(0)
  const [lastBox, setLastBox] = useState<number | null>(null)
  const [answering, setAnswering] = useState(false)
  const [error, setError] = useState('')

  const loadQueue = useCallback(() => {
    setQueue(null)
    setIdx(0)
    setKnown(0)
    setUnknown(0)
    setRevealed(false)
    setLastBox(null)
    setError('')
    if (deckId) {
      fetchAllCards(deckId)
        .then(({ cards }) => {
          setQueue(cards.map((c) => ({ cardId: c.id, front: c.front, back: c.back, reading: c.reading })))
          localStorage.setItem('vm.lastStudyDeckId', deckId) // 홈 '이어서 학습' 카드 재료
        })
        .catch((e) => setError(e.message))
    } else {
      api<StudyCard[]>('/reviews/due').then(setQueue).catch((e) => setError(e.message))
    }
  }, [deckId])

  useEffect(loadQueue, [loadQueue])

  async function answer(correct: boolean) {
    if (answering || !queue) return // 더블클릭 방어 — 두 번 전송되면 박스가 두 번 움직인다
    setAnswering(true)
    try {
      const card = queue[idx]
      const res = await api<AnswerResult>(`/reviews/cards/${card.cardId}/answer`, {
        method: 'POST',
        body: JSON.stringify({ correct }),
      })
      setLastBox(res.boxLevel)
      if (correct) setKnown((n) => n + 1)
      else setUnknown((n) => n + 1)
      setRevealed(false)
      setIdx((i) => i + 1)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setAnswering(false)
    }
  }

  const total = queue?.length ?? 0
  const done = queue !== null && idx >= total

  return (
    <>
      <TopNav />
      <div className="shell study-shell">
        {error && <p className="error" role="alert">{error}</p>}

        {queue === null && !error && <p className="muted">불러오는 중...</p>}

        {queue !== null && total === 0 && (
          <div className="stub">
            <h2>{deckId ? '이 덱에는 카드가 없어요' : '지금 복습할 카드가 없어요 🎉'}</h2>
            <p>
              <Link to={deckId ? `/decks/${deckId}` : '/'} className="link" style={{ color: 'var(--a)' }}>
                ← 돌아가기
              </Link>
            </p>
          </div>
        )}

        {!done && queue !== null && total > 0 && (
          <>
            <div className="study-top">
              <Link to={deckId ? `/decks/${deckId}` : '/'} className="hero-secondary">← 그만하기</Link>
              <span className="muted" style={{ fontSize: 13.5 }}>
                {idx + 1} / {total}
              </span>
            </div>
            <div
              className="progress-track"
              role="progressbar"
              aria-valuemin={0}
              aria-valuemax={total}
              aria-valuenow={idx}
            >
              <div className="progress-fill" style={{ width: `${(idx / total) * 100}%` }} />
            </div>

            {/* 🔊는 카드 button의 '형제' — 안에 넣으면 button 중첩(HTML 위반) + Enter/Space가 카드 뒤집기로 전파 */}
            <div className="study-card-wrap">
              <button className="study-card" onClick={() => setRevealed(true)}>
                {queue[idx].reading && <span className="reading">{queue[idx].reading}</span>}
                <span className="study-word">{queue[idx].front}</span>
                {revealed ? (
                  <span className="study-answer">{queue[idx].back}</span>
                ) : (
                  <span className="study-hint">카드를 눌러 뜻 확인</span>
                )}
              </button>
              {/* 읽기가 있으면 읽기를 읽는다 — 한자 TTS 오독 방지 */}
              <SpeakButton text={queue[idx].reading || queue[idx].front} size="lg" className="study-speak" />
            </div>

            {revealed ? (
              <div className="answer-buttons">
                <button className="answer-no" disabled={answering} onClick={() => answer(false)}>
                  몰라요
                </button>
                <button className="answer-yes" disabled={answering} onClick={() => answer(true)}>
                  알아요
                </button>
              </div>
            ) : (
              <p className="muted" style={{ textAlign: 'center', fontSize: 13.5 }}>
                떠올린 다음 카드를 눌러 확인하세요
              </p>
            )}
            {lastBox !== null && (
              <p className="muted" style={{ textAlign: 'center', fontSize: 12.5 }}>
                직전 카드 → 박스 {lastBox}
              </p>
            )}
          </>
        )}

        {done && total > 0 && (
          <div className="result-panel">
            <h2>복습 완료 🎉</h2>
            <p className="result-line">
              {total}장 중 <b>알아요 {known}</b> · <b>몰라요 {unknown}</b>
            </p>
            <p className="muted" style={{ fontSize: 13.5 }}>
              알아요 카드는 다음 박스로 승급, 몰라요 카드는 박스 1로 — 10분 뒤 다시 만나요.
            </p>
            <div className="answer-buttons" style={{ marginTop: 26 }}>
              <Link to="/" className="answer-no" style={{ textDecoration: 'none', textAlign: 'center' }}>
                홈으로
              </Link>
              <button className="answer-yes" onClick={loadQueue}>
                한 번 더
              </button>
            </div>
          </div>
        )}
      </div>
    </>
  )
}
