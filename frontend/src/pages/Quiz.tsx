import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../api/client'
import TopNav from '../components/TopNav'
import SpeakButton from '../components/SpeakButton'
import { loadSettings, saveSettings } from '../lib/settings'
import { recordRecentStudy } from '../lib/recent'

// 백엔드(Phase 2 퀴즈 세션 API) 계약 — direction은 소문자 'front_to_back' | 'back_to_front' (Direction.from)
type Direction = 'front_to_back' | 'back_to_front'

// 답 비교 기준 = 서버 QuizService.normalizeAnswer와 동일 (trim + lowercase)
const norm = (s: string) => s.trim().toLowerCase()

interface Question {
  questionId: number
  questionOrder: number
  question: string
  reading?: string | null   // 단어가 문제일 때 읽기(요미가나), 없으면 null
  choices: string[]
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
  selectedAnswer: string
  sessionEnded: boolean
}

interface Summary {
  total: number
  answered: number
  correct: number
  wrong: number
  accuracy: number
  questions: { questionId: number; question: string; correctAnswer: string | null; selectedAnswer: string | null; correct: boolean | null }[]
}

/**
 * 4지선다 퀴즈 — 세션 시작 → 문제마다 답 제출(즉시 정오 공개) → 요약.
 * 오답은 출석과 함께 서버가 기록하므로 '오답만 다시'가 가능 (wrongOnly).
 */
export default function Quiz() {
  const { deckId } = useParams()
  const [direction, setDirection] = useState<Direction>('front_to_back')
  const [count, setCount] = useState(10)
  const [wrongOnly, setWrongOnly] = useState(false)
  const [starredOnly, setStarredOnly] = useState(false)   // 별표만 — wrongOnly와 상호 배타 (백엔드 else-if 순서와 일치)
  const [choices, setChoices] = useState<4 | 5 | 6>(() => loadSettings().quizChoices)   // 선택지 수 — 설정에 기억
  const [cardCount, setCardCount] = useState<number | null>(null)   // 문제 수 상한 = 덱 카드 수

  useEffect(() => {
    api<{ cardCount: number }>(`/decks/${deckId}`).then((d) => setCardCount(d.cardCount)).catch(() => {})
  }, [deckId])

  const [session, setSession] = useState<StartResp | null>(null)
  const [idx, setIdx] = useState(0)
  const [picked, setPicked] = useState<AnswerResp | null>(null)   // 현재 문제의 제출 결과
  const [correctCount, setCorrectCount] = useState(0)
  const [summary, setSummary] = useState<Summary | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  // 두 가지 '오답'을 분리 (Codex 검산 2026-08-23):
  //  - 설정 화면의 '오답만'      = 누적 오답 (서버가 세션 장부 + 구형 장부를 합쳐 봄)
  //  - 결과 화면의 '이번 오답 다시' = 방금 끝난 세션(sourceSessionId)에서 틀린 카드만
  async function start(opts?: { sourceSessionId?: number }) {
    if (busy) return
    setBusy(true)
    setError('')
    try {
      const body = opts?.sourceSessionId
        ? { direction, total: count, choiceCount: choices, sourceSessionId: opts.sourceSessionId }
        : { direction, total: count, choiceCount: choices, wrongOnly, starredOnly }
      const res = await api<StartResp>(`/decks/${deckId}/quiz-sessions`, { method: 'POST', body: JSON.stringify(body) })
      if (deckId) recordRecentStudy(deckId, 'quiz') // 홈 '최근 학습 덱' 재료
      setSummary(null)   // 성공한 뒤에만 요약을 치운다 — 실패하면 결과 화면 유지
      setSession(res)
      setIdx(0)
      setPicked(null)
      setCorrectCount(0)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  async function choose(choice: string) {
    if (!session || picked || busy) return   // 이미 답한 문제는 잠금 (더블클릭·연타 방어)
    setBusy(true)
    try {
      const q = session.questions[idx]
      const res = await api<AnswerResp>(`/decks/${deckId}/quiz-sessions/${session.sessionId}/answers`, {
        method: 'POST',
        body: JSON.stringify({ questionId: q.questionId, selectedAnswer: choice }),
      })
      setPicked(res)
      if (res.correct) setCorrectCount((n) => n + 1)
      if (loadSettings().quizAutoAdvance) {
        // 설정: 답하면 1초 뒤 자동 다음 — 최신 next를 ref로 호출 (타이머가 옛 클로저를 잡지 않게)
        if (advanceTimer.current) window.clearTimeout(advanceTimer.current)   // 이전 문제의 타이머가 이번 피드백을 일찍 넘기지 않게
        advanceTimer.current = window.setTimeout(() => nextRef.current(), 1000)
      }
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  async function next() {
    if (!session || !picked) return   // 답한 상태에서만 — 자동 넘김 타이머와 수동 Enter가 겹쳐도 한 번만 진행
    if (advanceTimer.current) { window.clearTimeout(advanceTimer.current); advanceTimer.current = null }
    if (idx + 1 < session.questions.length) {
      setIdx(idx + 1)
      setPicked(null)
      return
    }
    try {
      setSummary(await api<Summary>(`/decks/${deckId}/quiz-sessions/${session.sessionId}/summary`))
    } catch (e) {
      setError((e as Error).message)
    }
  }

  const advanceTimer = useRef<number | null>(null)
  const nextRef = useRef(next)
  nextRef.current = next

  // 키보드: 1~4 선택, Enter/Space 다음
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (!session || summary) return
      const q = session.questions[idx]
      if (!picked && /^[1-6]$/.test(e.key) && q.choices[Number(e.key) - 1] !== undefined) {
        choose(q.choices[Number(e.key) - 1])
      } else if (picked && (e.key === 'Enter' || e.key === ' ')) {
        e.preventDefault()
        next()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  })

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

        {/* 설정 — 세션 전 */}
        {!session && (
          <div className="quiz-setup">
            <h2>퀴즈</h2>
            <p className="muted">4~6지선다 · 틀린 문제는 기록돼서 나중에 오답만 다시 풀 수 있어요</p>
            <div className="setup-row">
              <span className="setup-label">방향</span>
              <div className="sort-tabs">
                <button className={direction === 'front_to_back' ? 'active' : ''} disabled={busy} onClick={() => setDirection('front_to_back')}>
                  단어 → 뜻
                </button>
                <button className={direction === 'back_to_front' ? 'active' : ''} disabled={busy} onClick={() => setDirection('back_to_front')}>
                  뜻 → 단어
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
                  <button key={n} className={count === n ? 'active' : ''} disabled={busy} onClick={() => setCount(n)}>
                    {n}
                  </button>
                ))}
                {cardCount !== null && cardCount > 0 && (
                  <button className={count === cardCount ? 'active' : ''} disabled={busy} onClick={() => setCount(cardCount)}>
                    전체 {cardCount}
                  </button>
                )}
              </div>
            </div>
            <div className="setup-row">
              <span className="setup-label">선택지</span>
              <div className="sort-tabs">
                {([4, 5, 6] as const).map((n) => (
                  <button
                    key={n}
                    className={choices === n ? 'active' : ''}
                    disabled={busy}
                    onClick={() => {
                      setChoices(n)
                      saveSettings({ ...loadSettings(), quizChoices: n })   // 다음에도 기억
                    }}
                  >
                    {n}지선다
                  </button>
                ))}
              </div>
            </div>
            <div className="setup-row">
              <span className="setup-label">범위</span>
              <div className="sort-tabs">
                <button className={!wrongOnly && !starredOnly ? 'active' : ''} disabled={busy} onClick={() => { setWrongOnly(false); setStarredOnly(false) }}>전체</button>
                <button className={wrongOnly ? 'active' : ''} disabled={busy} onClick={() => { setWrongOnly(true); setStarredOnly(false) }}>오답만</button>
                <button className={starredOnly ? 'active' : ''} disabled={busy} onClick={() => { setStarredOnly(true); setWrongOnly(false) }}>⭐ 별표만</button>
              </div>
            </div>
            <button className="cta" style={{ marginTop: 8 }} disabled={busy} onClick={() => start()}>
              {busy ? '준비 중...' : '시작'}
            </button>
            <p className="muted" style={{ fontSize: 12.5 }}>덱의 서로 다른 답이 모자라면 선택지가 그만큼만 나와요 (최소 2). '오답만'은 지금까지 틀린 카드, '별표만'은 ★ 표시한 카드예요</p>
          </div>
        )}

        {/* 문제 */}
        {session && q && !summary && (
          <>
            <div className="progress-track" role="progressbar" aria-valuemin={0} aria-valuemax={total} aria-valuenow={idx}>
              <div className="progress-fill" style={{ width: `${((idx + (picked ? 1 : 0)) / total) * 100}%` }} />
            </div>
            <div className="quiz-card">
              <p className="quiz-kicker">{direction === 'front_to_back' ? '이 단어의 뜻은?' : '이 뜻의 단어는?'}</p>
              <p className="quiz-question">
                {/* 읽기는 답의 절반(한자→읽기 회상) — 채점 후에만 공개. 복습 카드(8/28)와 같은 규칙 */}
                {picked !== null && q.reading && <span className="reading">{q.reading}</span>}
                {q.question} <SpeakButton text={q.reading || q.question} />
              </p>
              <div className="choice-grid">
                {q.choices.map((c, i) => {
                  let cls = 'choice'
                  if (picked) {
                    // 서버 채점과 같은 자(trim + lowercase)로 비교 — 대소문자·공백만 다른 답이 엇갈려 칠해지지 않게
                    if (norm(c) === norm(picked.correctAnswer)) cls += ' correct'
                    else if (norm(c) === norm(picked.selectedAnswer)) cls += ' wrong'
                    else cls += ' dim'
                  }
                  return (
                    <button key={c} className={cls} disabled={!!picked || busy} onClick={() => choose(c)}>
                      <span className="choice-num">{i + 1}</span>
                      {c}
                    </button>
                  )
                })}
              </div>
              {picked && (
                <div className="quiz-feedback">
                  <p className={picked.correct ? 'ok' : 'ng'}>
                    {picked.correct ? '정답!' : `오답 — 정답은 "${picked.correctAnswer}"`}
                  </p>
                  <button className="btn-primary" onClick={next}>
                    {idx + 1 < total ? '다음 (Enter)' : '결과 보기'}
                  </button>
                </div>
              )}
            </div>
            <p className="muted" style={{ textAlign: 'center', fontSize: 12.5 }}>숫자 키 1~6으로 고를 수 있어요</p>
          </>
        )}

        {/* 요약 */}
        {summary && (
          <div className="result-panel">
            <h2>퀴즈 완료 {summary.accuracy >= 80 ? '🎉' : ''}</h2>
            <p className="result-line">
              {summary.total}문제 중 <b>정답 {summary.correct}</b> · <b>오답 {summary.wrong}</b> · 정답률 {summary.accuracy}%
            </p>
            {summary.wrong > 0 && (
              <div className="word-list" style={{ marginTop: 18, textAlign: 'left' }}>
                {summary.questions.filter((x) => x.correct === false).map((x) => (
                  <div key={x.questionId} className="word-row">
                    <span className="word-front">{x.question}</span>
                    <span className="word-back">
                      <s style={{ color: '#b0485c' }}>{x.selectedAnswer}</s> → {x.correctAnswer}
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
