import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { fetchAllCards, type CardDto } from '../api/cards'
import TopNav from '../components/TopNav'
import { isTtsSupported, speakTimes } from '../lib/tts'

// 듣기 받아쓰기 (백로그 ⑭) — 어학원 시험 스타일: 단어를 3회 들려주고 스펠링·뜻을 받아쓴다.
// v1 = 연습 모드 (학습 기록 저장 없음 — 세션 저장은 백로그). 채점은 화면에서:
//  · 스펠링: 단어(front) 또는 읽기(reading) 어느 쪽을 쳐도 정답 (일본어는 かな 입력이 자연스러움)
//  · 뜻: 쉼표로 나뉜 후보 중 하나면 정답 (타이핑 모드와 같은 규칙, ADR-026)

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
  const [idx, setIdx] = useState(0)
  const [phase, setPhase] = useState<'setup' | 'play' | 'done'>('setup')
  const [count, setCount] = useState(10)
  const [ordered, setOrdered] = useState(false)   // true=1번부터 순서대로, false=무작위
  const [rate, setRate] = useState(0.92)          // 재생 속도
  const [times, setTimes] = useState(3)           // 반복 횟수
  const [gapSec, setGapSec] = useState(1.8)       // 반복 간격(초)
  const [spelling, setSpelling] = useState('')
  const [meaning, setMeaning] = useState('')
  const [revealed, setRevealed] = useState(false)
  const [error, setError] = useState('')
  const stopRef = useRef<() => void>(() => {})

  useEffect(() => {
    fetchAllCards(deckId!)
      .then(({ cards }) => setPool(cards))
      .catch((e) => setError(e.message))
  }, [deckId])

  // 화면을 떠나면 재생 즉시 중단 (늦은 재생 방지 — ⑰ 교훈 선반영)
  useEffect(() => () => stopRef.current(), [])

  // 듣기 설정은 브라우저에 기억 — 다음에도 같은 속도·간격으로
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
    } catch { /* 시크릿 모드 등 저장 불가 환경 무시 */ }
  }, [ordered, rate, times, gapSec])

  function playCurrent(q: Item[], i: number) {
    stopRef.current()
    const c = q[i].card
    stopRef.current = speakTimes(c.reading || c.front, { times, gapMs: gapSec * 1000, rate })
  }

  function start() {
    const n = Math.max(1, Math.min(count, pool.length))
    const base = ordered ? [...pool] : [...pool].sort(() => Math.random() - 0.5)
    const shuffled = base.slice(0, n)
    const q = shuffled.map((card) => ({ card, spelling: '', meaning: '' }))
    setQueue(q)
    setIdx(0)
    setRevealed(false)
    setSpelling('')
    setMeaning('')
    setPhase('play')
    playCurrent(q, 0)
  }

  function submit() {
    if (revealed) return
    const q = [...queue]
    q[idx] = {
      ...q[idx],
      spelling,
      meaning,
      spellingOk: spellingMatch(spelling, q[idx].card),
      meaningOk: meaningMatch(meaning, q[idx].card.back),
    }
    setQueue(q)
    setRevealed(true)
    stopRef.current()
  }

  function next() {
    if (idx + 1 >= queue.length) {
      stopRef.current()
      setPhase('done')
      return
    }
    const ni = idx + 1
    setIdx(ni)
    setSpelling('')
    setMeaning('')
    setRevealed(false)
    playCurrent(queue, ni)
  }

  const cur = queue[idx]
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
            <p className="sub">단어를 3번 들려드려요 — 스펠링(단어 또는 읽기)과 뜻을 받아쓰세요 · 연습 모드(기록 저장 없음)</p>
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
                max={pool.length || 1}
                value={count}
                onChange={(e) => setCount(Number(e.target.value))}
              />
              <span className="muted" style={{ fontSize: 13 }}>/ 카드 {pool.length}장</span>
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
              <button className="answer-yes" disabled={pool.length === 0 || !isTtsSupported()} onClick={start}>
                🔊 듣기 시작
              </button>
            </div>
          </div>
        )}

        {phase === 'play' && cur && (
          <div className="quiz-setup">
            <p className="muted" style={{ margin: 0 }}>{idx + 1} / {queue.length}</p>
            <div className="deck-progress" style={{ margin: '4px 0 10px' }}>
              <div className="progress-fill started" style={{ width: `${((idx + (revealed ? 1 : 0)) / queue.length) * 100}%` }} />
            </div>

            <div className="answer-buttons" style={{ marginTop: 0 }}>
              <button className="answer-no" onClick={() => playCurrent(queue, idx)}>🔊 다시 듣기 ({times}회)</button>
            </div>

            <input
              placeholder="스펠링 — 단어 또는 읽기"
              value={spelling}
              autoFocus
              disabled={revealed}
              onChange={(e) => setSpelling(e.target.value)}
            />
            <input
              placeholder="뜻"
              value={meaning}
              disabled={revealed}
              onChange={(e) => setMeaning(e.target.value)}
              onKeyDown={(e) => {
                if (e.key !== 'Enter' || e.nativeEvent.isComposing) return
                if (revealed) next()
                else submit()
              }}
            />

            {revealed && (
              <div className="listen-result">
                <p style={{ margin: '4px 0' }}>
                  스펠링 {cur.spellingOk ? '✅' : '❌'} — 정답: <b>{cur.card.front}</b>
                  {cur.card.reading && <span className="muted">（{cur.card.reading}）</span>}
                </p>
                <p style={{ margin: '4px 0' }}>
                  뜻 {cur.meaningOk ? '✅' : '❌'} — 정답: <b>{cur.card.back}</b>
                </p>
              </div>
            )}

            <div className="answer-buttons">
              {revealed ? (
                <button className="answer-yes" onClick={next}>
                  {idx + 1 >= queue.length ? '결과 보기' : '다음 →'}
                </button>
              ) : (
                <button className="answer-yes" disabled={!spelling.trim() && !meaning.trim()} onClick={submit}>
                  제출
                </button>
              )}
            </div>
          </div>
        )}

        {phase === 'done' && (
          <div className="result-panel">
            <h2>듣기 완료 🎧</h2>
            <p className="result-line">
              스펠링 <b>{spellScore}/{queue.length}</b> · 뜻 <b>{meanScore}/{queue.length}</b>
            </p>
            <div className="word-list" style={{ marginTop: 14, textAlign: 'left' }}>
              {queue.map((it, i) => (
                <div key={i} className="word-row">
                  <span className="word-idx">{i + 1}</span>
                  <span className="word-front">
                    {it.spellingOk ? '✅' : '❌'} {it.card.front}
                    {it.card.reading && <span className="muted">（{it.card.reading}）</span>}
                  </span>
                  <span className="word-back">{it.meaningOk ? '✅' : '❌'} {it.card.back}</span>
                </div>
              ))}
            </div>
            <div className="answer-buttons" style={{ marginTop: 26 }}>
              <button className="answer-no" onClick={() => setPhase('setup')}>다시 듣기</button>
              <Link to={`/decks/${deckId}`} className="answer-yes" style={{ textDecoration: 'none', textAlign: 'center' }}>덱으로</Link>
            </div>
          </div>
        )}
      </div>
    </>
  )
}
