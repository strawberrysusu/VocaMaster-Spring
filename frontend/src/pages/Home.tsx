import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import TopNav from '../components/TopNav'

interface TodaySummary {
  dueCount: number
  reviewedTodayCount: number
  studyCount: number
  streak: number
}

interface BoxCount {
  box: number
  count: number
}

interface Deck {
  id: number
  title: string
  visibility: string
  cardCount: number
}

const SECONDS_PER_CARD = 25 // 예상 시간 어림값 — 카드당 평균 답변 시간

export default function Home() {
  const [summary, setSummary] = useState<TodaySummary | null>(null)
  const [boxes, setBoxes] = useState<BoxCount[] | null>(null)
  const [decks, setDecks] = useState<Deck[]>([])
  const [error, setError] = useState('')

  useEffect(() => {
    api<TodaySummary>('/reviews/today-summary').then(setSummary).catch((e) => setError(e.message))
    // 구버전 백엔드(엔드포인트 없음)면 차트만 조용히 숨김
    api<BoxCount[]>('/reviews/box-distribution').then(setBoxes).catch(() => setBoxes(null))
    api<Deck[]>('/decks').then(setDecks).catch(() => {})
  }, [])

  const today = new Date().toLocaleDateString('ko-KR', { month: 'long', day: 'numeric', weekday: 'long' })
  const due = summary?.dueCount ?? 0
  const dueMinutes = Math.max(1, Math.round((due * SECONDS_PER_CARD) / 60))
  const maxBox = boxes ? Math.max(1, ...boxes.map((b) => b.count)) : 1

  return (
    <>
      <TopNav streak={summary?.streak} />
      <div className="shell">
        <div className="section-head">
          <h1>오늘의 복습</h1>
          <span className="date">{today}</span>
        </div>

        {error && <p className="error">{error}</p>}

        <section className="hero">
          <div className="hero-main">
            <div>
              <div className="hero-count">
                <span className="num big">{due}</span>
                <span className="unit">장</span>
              </div>
              {due > 0 ? (
                <>
                  <p className="hero-copy">복습 시간이 된 카드가 기다리고 있어요.</p>
                  <p className="hero-sub">약 {dueMinutes}분 예상</p>
                </>
              ) : (
                <>
                  <p className="hero-copy">지금은 복습할 카드가 없어요.</p>
                  <p className="hero-sub">새 단어를 추가하거나, 잠시 쉬어가요 ☕</p>
                </>
              )}
            </div>
            <div className="hero-actions">
              {due > 0 ? (
                <Link to="/study" className="cta">오늘 복습 시작</Link>
              ) : (
                <span className="cta disabled">오늘 복습 시작</span>
              )}
              <Link to="/decks" className="hero-secondary">내 덱 보기</Link>
            </div>
          </div>

          {boxes && (
            <div className="ladder">
              <div className="ladder-head">
                <span className="title">라이트너 박스 분포</span>
                <span className="hint">오른쪽으로 갈수록 복습 간격이 길어져요</span>
              </div>
              <div className="ladder-bars">
                {boxes.map((b) => (
                  <div key={b.box} className="ladder-col">
                    <span className="count">{b.count}</span>
                    <div
                      className="bar"
                      style={{
                        height: `${Math.max(6, Math.round((b.count / maxBox) * 46))}px`,
                        background: b.box <= 2 ? 'var(--a)' : 'var(--bar-rest)',
                      }}
                    />
                    <span className="label">{b.box}</span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </section>

        {summary && (
          <div className="stats-2col">
            <div className="stat-card">
              <p className="label">오늘 학습한 카드</p>
              <div className="value">
                <span className="num big">{summary.studyCount}</span>
                <span className="unit">장</span>
              </div>
              <p className="foot">복습으로 외운 카드 {summary.reviewedTodayCount}장</p>
            </div>
            <div className="stat-card">
              <p className="label">연속 학습</p>
              <div className="value">
                <span className="num big">{summary.streak}</span>
                <span className="unit">일</span>
              </div>
              <div className="streak-dots">
                {Array.from({ length: 14 }, (_, i) => (
                  <div key={i} style={{ background: i < Math.min(summary.streak, 14) ? 'var(--a)' : '#EFEFF3' }} />
                ))}
              </div>
            </div>
          </div>
        )}

        <div className="section-head" style={{ margin: '44px 0 16px' }}>
          <h2>내 덱</h2>
          <Link to="/decks" className="link">전체 보기</Link>
        </div>
        <div className="deck-grid">
          {decks.slice(0, 3).map((d) => (
            <Link key={d.id} to={`/decks/${d.id}`} className="deck-card">
              <span className="tag">{d.visibility}</span>
              <p className="title">{d.title}</p>
              <p className="meta">카드 {d.cardCount}장</p>
            </Link>
          ))}
          {decks.length === 0 && <p className="muted">아직 덱이 없어요 — 첫 단어장을 만들어보세요.</p>}
        </div>
      </div>
    </>
  )
}
