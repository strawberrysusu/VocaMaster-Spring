import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../api/client'
import TopNav from '../components/TopNav'
import SpeakButton from '../components/SpeakButton'

// 백엔드(Phase 2 타이핑 세션 API) 계약 — direction 소문자, 채점은 서버(쉼표 복수 정답·대소문자 무시, ADR-026)
type Direction = 'front_to_back' | 'back_to_front'

interface Question {
  questionId: number
  questionOrder: number
  question: string
  reading?: string | null   // 단어가 문제일 때 읽기(요미가나), 없으면 null
}

interface StartResp {
  sessionId: number
  direction: string
  total: number
  questions: Question[]
}

interface AnswerResp {
  correct: boolean
  correctAnswer: string
  typedAnswer: string
  sessionEnded: boolean
}

interface Summary {
  total: number
  answered: number
  correct: number
  wrong: number
  accuracy: number
  questions: { questionId: number; question: string; correctAnswer: string | null; typedAnswer: string | null; correct: boolean | null }[]
}

/**
 * 타이핑 — 선택지 없이 직접 입력. 퀴즈와 같은 세션 패턴(시작 → 답 → 요약).
 * 채점은 서버만 한다 (정답을 화면이 미리 알지 않음 — 제출 후 공개).
 */
export default function Typing() {
  const { deckId } = useParams()
  const [direction, setDirection] = useState<Direction>('front_to_back')
  const [count, setCount] = useState(10)
  const [wrongOnly, setWrongOnly] = useState(false)
  const [cardCount, setCardCount] = useState<number | null>(null)   // 문제 수 상한 = 덱 카드 수

  useEffect(() => {
    api<{ cardCount: number }>(`/decks/${deckId}`).then((d) => setCardCount(d.cardCount)).catch(() => {})
  }, [deckId])

  const [session, setSession] = useState<StartResp | null>(null)
  const [idx, setIdx] = useState(0)
  const [typed, setTyped] = useState('')
  const [result, setResult] = useState<AnswerResp | null>(null)
  const [correctCount, setCorrectCount] = useState(0)
  const [summary, setSummary] = useState<Summary | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)

  // 새 문제가 뜨면 입력창에 포커스 — 마우스 없이 연속 타이핑
  useEffect(() => {
    if (session && !result && !summary) inputRef.current?.focus()
  }, [session, idx, result, summary])

  async function start(opts?: { sourceSessionId?: number }) {
    if (busy) return
    setBusy(true)
    setError('')
    try {
      const body = opts?.sourceSessionId
        ? { direction, total: count, sourceSessionId: opts.sourceSessionId }
        : { direction, total: count, wrongOnly }
      const res = await api<StartResp>(`/decks/${deckId}/typing-sessions`, { method: 'POST', body: JSON.stringify(body) })
      setSummary(null)   // 성공한 뒤에만 요약을 치운다 — 실패하면 결과 화면 유지
      setSession(res)
      setIdx(0)
      setTyped('')
      setResult(null)
      setCorrectCount(0)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  async function submit() {
    if (!session || result || busy) return
    if (!typed.trim()) return   // 빈 입력은 서버도 오답 처리하지만, 실수 제출은 막는다
    setBusy(true)
    try {
      const q = session.questions[idx]
      const res = await api<AnswerResp>(`/decks/${deckId}/typing-sessions/${session.sessionId}/answers`, {
        method: 'POST',
        body: JSON.stringify({ questionId: q.questionId, typedAnswer: typed }),
      })
      setResult(res)
      setTyped(res.typedAnswer)   // 서버가 채점한 그 문자열로 화면을 맞춤 (응답 대기 중 입력이 바뀌어도 어긋나지 않게)
      if (res.correct) setCorrectCount((n) => n + 1)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  async function next() {
    if (!session) return
    if (idx + 1 < session.questions.length) {
      setIdx(idx + 1)
      setTyped('')
      setResult(null)
      return
    }
    try {
      setSummary(await api<Summary>(`/decks/${deckId}/typing-sessions/${session.sessionId}/summary`))
    } catch (e) {
      setError((e as Error).message)
    }
  }

  const q = session?.questions[idx]
  const total = session?.questions.length ?? 0

  return (
    <>
      <TopNav />
      <div className="shell study-shell">
        <div className="study-top">
          <Link to={`/decks/${deckId}`} className="hero-secondary">← 덱으로</Link>
          {session && !summary && (
            <span className="muted" style={{ fontSize: 13.5 }}>
              {idx + 1} / {total} · 정답 {correctCount}
            </span>
          )}
        </div>

        {error && <p className="error" role="alert">{error}</p>}

        {!session && (
          <div className="quiz-setup">
            <h2>타이핑</h2>
            <p className="muted">직접 쳐서 답해요 · 대소문자·앞뒤 공백 무시 · 뜻이 "사과, 능금"처럼 여러 개면 하나만 맞아도 정답</p>
            <div className="setup-row">
              <span className="setup-label">방향</span>
              <div className="sort-tabs">
                <button className={direction === 'front_to_back' ? 'active' : ''} disabled={busy} onClick={() => setDirection('front_to_back')}>
                  단어 보고 뜻 쓰기
                </button>
                <button className={direction === 'back_to_front' ? 'active' : ''} disabled={busy} onClick={() => setDirection('back_to_front')}>
                  뜻 보고 단어 쓰기
                </button>
              </div>
            </div>
            <div className="setup-row">
              <span className="setup-label">문제 수</span>
              <div className="sort-tabs">
                <input
                  type="number"
                  className="count-input"
                  aria-label="문제 수"
                  min={1}
                  max={cardCount ?? undefined}
                  value={count}
                  disabled={busy}
                  onChange={(e) => setCount(Number(e.target.value) || 0)}
                  onBlur={() => setCount((c) => Math.min(Math.max(1, c), cardCount ?? Math.max(1, c)))}
                />
                {[10, 20].map((n) => (
                  <button key={n} className={count === n ? 'active' : ''} disabled={busy} onClick={() => setCount(n)}>{n}</button>
                ))}
                {cardCount !== null && cardCount > 0 && (
                  <button className={count === cardCount ? 'active' : ''} disabled={busy} onClick={() => setCount(cardCount)}>
                    전체 {cardCount}
                  </button>
                )}
              </div>
            </div>
            <div className="setup-row">
              <span className="setup-label">범위</span>
              <div className="sort-tabs">
                <button className={!wrongOnly ? 'active' : ''} disabled={busy} onClick={() => setWrongOnly(false)}>전체</button>
                <button className={wrongOnly ? 'active' : ''} disabled={busy} onClick={() => setWrongOnly(true)}>오답만</button>
              </div>
            </div>
            <button className="cta" style={{ marginTop: 8 }} disabled={busy} onClick={() => start()}>
              {busy ? '준비 중...' : '시작'}
            </button>
            <p className="muted" style={{ fontSize: 12.5 }}>'오답만'은 타이핑에서 지금까지 틀린 카드 전체예요 (카드 1장부터 가능)</p>
          </div>
        )}

        {session && q && !summary && (
          <>
            <div className="progress-track" role="progressbar" aria-valuemin={0} aria-valuemax={total} aria-valuenow={idx}>
              <div className="progress-fill" style={{ width: `${((idx + (result ? 1 : 0)) / total) * 100}%` }} />
            </div>
            <div className="quiz-card">
              <p className="quiz-kicker">{direction === 'front_to_back' ? '이 단어의 뜻을 입력하세요' : '이 뜻의 단어를 입력하세요'}</p>
              <p className="quiz-question">
                {q.reading && <span className="reading">{q.reading}</span>}
                {q.question} <SpeakButton text={q.reading || q.question} />
              </p>
              <form
                className="typing-form"
                onSubmit={(e) => {
                  e.preventDefault()
                  if (result) next()
                  else submit()
                }}
              >
                <input
                  ref={inputRef}
                  className={`typing-input ${result ? (result.correct ? 'ok' : 'ng') : ''}`}
                  value={typed}
                  onChange={(e) => setTyped(e.target.value)}
                  onKeyDown={(e) => {
                    // 일본어·한국어 IME: 변환 확정 Enter는 '조합 중'이라 제출로 치지 않는다 (keyCode 229 = 구형 브라우저 신호)
                    if (e.key === 'Enter' && (e.nativeEvent.isComposing || e.keyCode === 229)) e.preventDefault()
                  }}
                  placeholder="정답 입력 후 Enter"
                  readOnly={!!result || busy}
                  maxLength={500}
                  autoComplete="off"
                  autoCapitalize="off"
                  spellCheck={false}
                  aria-label="답 입력"
                />
                {!result ? (
                  <button type="submit" className="btn-primary" disabled={busy || !typed.trim()}>확인</button>
                ) : (
                  <button type="submit" className="btn-primary">{idx + 1 < total ? '다음 (Enter)' : '결과 보기'}</button>
                )}
              </form>
              {result && (
                <div className="quiz-feedback" style={{ borderTop: 0, paddingTop: 0, marginTop: 14 }}>
                  <p className={result.correct ? 'ok' : 'ng'}>
                    {result.correct ? '정답!' : <>오답 — 정답은 <b>"{result.correctAnswer}"</b> <SpeakButton text={result.correctAnswer} /></>}
                  </p>
                </div>
              )}
            </div>
          </>
        )}

        {summary && (
          <div className="result-panel">
            <h2>타이핑 완료 {summary.accuracy >= 80 ? '🎉' : ''}</h2>
            <p className="result-line">
              {summary.total}문제 중 <b>정답 {summary.correct}</b> · <b>오답 {summary.wrong}</b> · 정답률 {summary.accuracy}%
            </p>
            {summary.wrong > 0 && (
              <div className="word-list" style={{ marginTop: 18, textAlign: 'left' }}>
                {summary.questions.filter((x) => x.correct === false).map((x) => (
                  <div key={x.questionId} className="word-row">
                    <span className="word-front">{x.question}</span>
                    <span className="word-back">
                      <s style={{ color: '#b0485c' }}>{x.typedAnswer || '(빈 답)'}</s> → {x.correctAnswer}
                    </span>
                  </div>
                ))}
              </div>
            )}
            <div className="answer-buttons" style={{ marginTop: 26 }}>
              <Link to={`/decks/${deckId}`} className="answer-no" style={{ textDecoration: 'none', textAlign: 'center' }}>
                덱으로
              </Link>
              {summary.wrong > 0 && session ? (
                <button className="answer-yes" disabled={busy} onClick={() => start({ sourceSessionId: session.sessionId })}>
                  이번 오답 다시 ({summary.wrong})
                </button>
              ) : (
                <button className="answer-yes" disabled={busy} onClick={() => start()}>한 번 더</button>
              )}
            </div>
          </div>
        )}
      </div>
    </>
  )
}
