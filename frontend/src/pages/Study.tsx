import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import TopNav from '../components/TopNav'

interface StudyCard {
  cardId: number
  front: string
  back: string
}

interface AnswerResult {
  boxLevel: number
}

interface PageResp {
  content: { id: number; front: string; back: string }[]
}

/**
 * 플래시카드 학습 — 두 입구, 한 흐름:
 * - /study            → 오늘 복습 (due 카드 전체)
 * - /study?deckId=N   → 그 덱의 전 카드 (새 카드는 첫 답변으로 박스 1 입장)
 * 답변은 /reviews/cards/{id}/answer — Leitner 증감의 그 API.
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
  const [error, setError] = useState('')

  useEffect(() => {
    if (deckId) {
      api<PageResp>(`/decks/${deckId}/cards?size=200`)
        .then((p) => setQueue(p.content.map((c) => ({ cardId: c.id, front: c.front, back: c.back }))))
        .catch((e) => setError(e.message))
    } else {
      api<StudyCard[]>('/reviews/due').then(setQueue).catch((e) => setError(e.message))
    }
  }, [deckId])

  async function answer(correct: boolean) {
    if (!queue) return
    const card = queue[idx]
    try {
      const res = await api<AnswerResult>(`/reviews/cards/${card.cardId}/answer`, {
        method: 'POST',
        body: JSON.stringify({ correct }),
      })
      setLastBox(res.boxLevel)
    } catch (e) {
      setError((e as Error).message)
      return
    }
    if (correct) setKnown((n) => n + 1)
    else setUnknown((n) => n + 1)
    setRevealed(false)
    setIdx((i) => i + 1)
  }

  const total = queue?.length ?? 0
  const done = queue !== null && idx >= total

  return (
    <>
      <TopNav />
      <div className="shell study-shell">
        {error && <p className="error">{error}</p>}

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
            <div className="progress-track">
              <div className="progress-fill" style={{ width: `${(idx / total) * 100}%` }} />
            </div>

            <button className="study-card" onClick={() => setRevealed(true)}>
              <span className="study-word">{queue[idx].front}</span>
              {revealed ? (
                <span className="study-answer">{queue[idx].back}</span>
              ) : (
                <span className="study-hint">카드를 눌러 뜻 확인</span>
              )}
            </button>

            {revealed ? (
              <div className="answer-buttons">
                <button className="answer-no" onClick={() => answer(false)}>
                  몰라요
                </button>
                <button className="answer-yes" onClick={() => answer(true)}>
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
              <button className="answer-yes" onClick={() => window.location.reload()}>
                한 번 더
              </button>
            </div>
          </div>
        )}
      </div>
    </>
  )
}
