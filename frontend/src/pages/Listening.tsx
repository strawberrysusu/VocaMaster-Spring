import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { fetchAllCards, type CardDto } from '../api/cards'
import TopNav from '../components/TopNav'
import { isTtsSupported, speakTimes } from '../lib/tts'

// 듣기 받아쓰기 (백로그 ⑭, 8/28 시험지 개편) — 어학원 시험 그대로:
// 문제 N개가 세로로 쭉 깔리고, "전체 재생"이 1번부터 순서대로 불러준다.
// 학생은 Tab으로 내려가며 받아쓰고 → 마지막에 "채점하기" 한 방.
//  · 스펠링: 단어(front) 또는 읽기(reading) 어느 쪽을 쳐도 정답
//  · 뜻: 쉼표로 나뉜 후보 중 하나면 정답 (타이핑 모드와 같은 규칙, ADR-026)
// v1 = 연습 모드 (기록 저장 없음 — 세션 저장은 백로그)

interface Item {
  card: CardDto
  spelling: string
  meaning: string
  spellingOk?: boolean
  meaningOk?: boolean
}

const norm = (s: string) => s.trim().toLowerCase()

function meaningMatch(typed: string, back: string): boolean {
  if (!typed.trim()) return false
  return back.split(',').some((c) => norm(c) === norm(typed))
}

function spellingMatch(typed: string, card: CardDto): boolean {
  if (!typed.trim()) return false
  const t = norm(typed)
  return t === norm(card.front) || (!!card.reading && t === norm(card.reading))
}

export default function Listening() {
  const { deckId } = useParams()
  const [pool, setPool] = useState<CardDto[]>([])
  const [queue, setQueue] = useState<Item[]>([])
  const [phase, setPhase] = useState<'setup' | 'sheet'>('setup')
  const [graded, setGraded] = useState(false)
  const [playingIdx, setPlayingIdx] = useState<number | null>(null)
  const [count, setCount] = useState(10)
  const [ordered, setOrdered] = useState(false)   // true=1번부터 순서대로, false=무작위
  const [starredOnly, setStarredOnly] = useState(false)   // 별표만 — 듣기는 서버 세션이 없어 클라이언트에서 거른다
  const [rate, setRate] = useState(0.92)
  const [times, setTimes] = useState(3)
  const [gapSec, setGapSec] = useState(1.8)
  const [error, setError] = useState('')
  const stopSpeakRef = useRef<() => void>(() => {})
  const interTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    fetchAllCards(deckId!)
      .then(({ cards }) => setPool(cards))
      .catch((e) => setError(e.message))
  }, [deckId])

  function stopAll() {
    stopSpeakRef.current()
    if (interTimerRef.current) clearTimeout(interTimerRef.current)
    setPlayingIdx(null)
  }

  // 화면을 떠나면 재생 즉시 중단 (늦은 재생 방지)
  useEffect(() => () => {
    stopSpeakRef.current()
    if (interTimerRef.current) clearTimeout(interTimerRef.current)
  }, [])

  // 듣기 설정은 브라우저에 기억
  useEffect(() => {
    try {
      const saved = JSON.parse(localStorage.getItem('vm.listening') ?? '{}')
      if (typeof saved.ordered === 'boolean') setOrdered(saved.ordered)
      if (typeof saved.rate === 'number') setRate(saved.rate)
      if (typeof saved.times === 'number') setTimes(saved.times)
      if (typeof saved.gapSec === 'number') setGapSec(saved.gapSec)
    } catch { /* 저장값 손상 시 기본값 */ }
  }, [])
  useEffect(() => {
    try {
      localStorage.setItem('vm.listening', JSON.stringify({ ordered, rate, times, gapSec }))
    } catch { /* 저장 불가 환경 무시 */ }
  }, [ordered, rate, times, gapSec])

  const wordOf = (it: Item) => it.card.reading || it.card.front

  // 한 문제만 재생 (행의 🔊)
  function playOne(q: Item[], i: number) {
    stopAll()
    setPlayingIdx(i)
    stopSpeakRef.current = speakTimes(wordOf(q[i]), { times, gapMs: gapSec * 1000, rate }, () =>
      setPlayingIdx(null),
    )
  }

  // 시험 방송: 1번부터 순서대로, 각 문제를 times회씩 — 문제 사이엔 받아쓸 여유 3초
  function playAll(q: Item[], from = 0) {
    stopAll()
    const step = (i: number) => {
      if (i >= q.length) {
        setPlayingIdx(null)
        return
      }
      setPlayingIdx(i)
      stopSpeakRef.current = speakTimes(wordOf(q[i]), { times, gapMs: gapSec * 1000, rate }, () => {
        interTimerRef.current = setTimeout(() => step(i + 1), 3000)
      })
    }
    step(from)
  }

  const activePool = starredOnly ? pool.filter((c) => c.starred) : pool

  function start() {
    const n = Math.max(1, Math.min(count, activePool.length))
    const base = ordered ? [...activePool] : [...activePool].sort(() => Math.random() - 0.5)
    const q = base.slice(0, n).map((card) => ({ card, spelling: '', meaning: '' }))
    setQueue(q)
    setGraded(false)
    setPhase('sheet')
    playAll(q, 0)
  }

  function updateItem(i: number, field: 'spelling' | 'meaning', v: string) {
    setQueue((q) => q.map((it, j) => (j === i ? { ...it, [field]: v } : it)))
  }

  function grade() {
    stopAll()
    setQueue((q) =>
      q.map((it) => ({
        ...it,
        spellingOk: spellingMatch(it.spelling, it.card),
        meaningOk: meaningMatch(it.meaning, it.card.back),
      })),
    )
    setGraded(true)
  }

  const spellScore = queue.filter((i) => i.spellingOk).length
  const meanScore = queue.filter((i) => i.meaningOk).length

  return (
    <>
      <TopNav />
      <div className="shell">
        <p style={{ margin: '0 0 14px' }}>
          <Link to={`/decks/${deckId}`} className="hero-secondary">← 덱으로</Link>
        </p>
        <div className="page-head">
          <div>
            <h1>🎧 듣기 받아쓰기</h1>
            <p className="sub">
              시험지처럼 — 전체 재생이 1번부터 불러주면 Tab으로 내려가며 받아쓰고, 마지막에 채점 ·
              연습 모드(기록 저장 없음)
            </p>
          </div>
        </div>

        {error && <p className="error" role="alert">{error}</p>}
        {!isTtsSupported() && <p className="error">이 브라우저는 음성 재생을 지원하지 않아요 — Chrome/Edge에서 열어주세요.</p>}

        {phase === 'setup' && (
          <div className="quiz-setup">
            <div className="setup-row">
              <span className="setup-label">문제 수</span>
              <input
                type="number"
                className="count-input"
                min={1}
                max={activePool.length || 1}
                value={count}
                onChange={(e) => setCount(Number(e.target.value))}
              />
              <span className="muted" style={{ fontSize: 13 }}>/ 카드 {activePool.length}장{starredOnly ? ' (별표)' : ''}</span>
            </div>
            <div className="setup-row">
              <span className="setup-label">범위</span>
              <div className="sort-tabs">
                <button className={!starredOnly ? 'active' : ''} onClick={() => setStarredOnly(false)}>전체</button>
                <button
                  className={starredOnly ? 'active' : ''}
                  disabled={pool.filter((c) => c.starred).length === 0}
                  title={pool.some((c) => c.starred) ? '' : '★ 표시한 카드가 없어요'}
                  onClick={() => setStarredOnly(true)}
                >
                  ⭐ 별표만
                </button>
              </div>
            </div>
            <div className="setup-row">
              <span className="setup-label">출제 순서</span>
              <div className="sort-tabs">
                <button className={!ordered ? 'active' : ''} onClick={() => setOrdered(false)}>무작위</button>
                <button className={ordered ? 'active' : ''} onClick={() => setOrdered(true)}>1번부터</button>
              </div>
            </div>
            <div className="setup-row">
              <span className="setup-label">속도</span>
              <div className="sort-tabs">
                <button className={rate === 0.75 ? 'active' : ''} onClick={() => setRate(0.75)}>느리게</button>
                <button className={rate === 0.92 ? 'active' : ''} onClick={() => setRate(0.92)}>보통</button>
                <button className={rate === 1.15 ? 'active' : ''} onClick={() => setRate(1.15)}>빠르게</button>
              </div>
            </div>
            <div className="setup-row">
              <span className="setup-label">반복</span>
              <div className="sort-tabs">
                {[2, 3, 5].map((n) => (
                  <button key={n} className={times === n ? 'active' : ''} onClick={() => setTimes(n)}>{n}회</button>
                ))}
              </div>
              <span className="setup-label" style={{ marginLeft: 10 }}>간격</span>
              <div className="sort-tabs">
                {[1, 1.8, 3].map((g) => (
                  <button key={g} className={gapSec === g ? 'active' : ''} onClick={() => setGapSec(g)}>{g}초</button>
                ))}
              </div>
            </div>
            <div className="answer-buttons">
              <button className="answer-yes" disabled={activePool.length === 0 || !isTtsSupported()} onClick={start}>
                🔊 시험 시작 (전체 재생)
              </button>
            </div>
          </div>
        )}

        {phase === 'sheet' && (
          <>
            <div className="listen-toolbar">
              {graded ? (
                <p className="result-line" style={{ margin: 0 }}>
                  스펠링 <b>{spellScore}/{queue.length}</b> · 뜻 <b>{meanScore}/{queue.length}</b>
                </p>
              ) : (
                <>
                  <button className="answer-no" onClick={() => playAll(queue, 0)}>▶ 전체 재생 (1번부터)</button>
                  <span className="muted" style={{ fontSize: 12.5 }}>
                    행의 🔊 = 그 문제만 다시 듣기 ({times}회)
                  </span>
                </>
              )}
            </div>

            <div className="listen-sheet">
              {queue.map((it, i) => (
                <div key={i}>
                  <div className={`listen-row ${playingIdx === i ? 'playing' : ''}`}>
                    <span className="word-idx">{i + 1}</span>
                    <button className="listen-play" title="이 문제 다시 듣기" onClick={() => playOne(queue, i)}>
                      🔊
                    </button>
                    <input
                      placeholder="스펠링 (단어 또는 읽기)"
                      value={it.spelling}
                      disabled={graded}
                      onChange={(e) => updateItem(i, 'spelling', e.target.value)}
                    />
                    <input
                      placeholder="뜻"
                      value={it.meaning}
                      disabled={graded}
                      onChange={(e) => updateItem(i, 'meaning', e.target.value)}
                    />
                    {graded && (
                      <span className="listen-marks">
                        {it.spellingOk ? '✅' : '❌'}{it.meaningOk ? '✅' : '❌'}
                      </span>
                    )}
                  </div>
                  {graded && !(it.spellingOk && it.meaningOk) && (
                    <p className="listen-answer">
                      정답: <b>{it.card.front}</b>
                      {it.card.reading && <span className="muted">（{it.card.reading}）</span>} — {it.card.back}
                    </p>
                  )}
                </div>
              ))}
            </div>

            <div className="answer-buttons" style={{ marginTop: 20 }}>
              {graded ? (
                <>
                  <button className="answer-no" onClick={() => setPhase('setup')}>다시 설정</button>
                  <Link to={`/decks/${deckId}`} className="answer-yes" style={{ textDecoration: 'none', textAlign: 'center' }}>
                    덱으로
                  </Link>
                </>
              ) : (
                <button className="answer-yes" onClick={grade}>채점하기</button>
              )}
            </div>
          </>
        )}
      </div>
    </>
  )
}
