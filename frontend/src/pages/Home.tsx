import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'

interface TodaySummary {
  dueCount: number
  reviewedTodayCount: number
  studyCount: number
  streak: number
}

// 홈 대시보드 — 기존 Mustache 홈에 없어서 불편했던 "오늘 뭐 해야 하지"를 첫 화면에.
export default function Home() {
  const [summary, setSummary] = useState<TodaySummary | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    api<TodaySummary>('/reviews/today-summary').then(setSummary).catch((e) => setError(e.message))
  }, [])

  return (
    <div className="page">
      <header className="topbar">
        <h1>VocaMaster</h1>
        <nav>
          <Link to="/decks">내 단어장</Link>
        </nav>
      </header>

      {error && <p className="error">{error}</p>}

      {summary && (
        <section className="stats-row">
          <div className="card stat">
            <div className="stat-num accent">{summary.dueCount}</div>
            <div className="stat-label">지금 복습할 카드</div>
          </div>
          <div className="card stat">
            <div className="stat-num">{summary.reviewedTodayCount}</div>
            <div className="stat-label">오늘 복습한 카드</div>
          </div>
          <div className="card stat">
            <div className="stat-num">{summary.studyCount}</div>
            <div className="stat-label">오늘 답변 수</div>
          </div>
          <div className="card stat">
            <div className="stat-num">{summary.streak}🔥</div>
            <div className="stat-label">연속 학습일</div>
          </div>
        </section>
      )}

      <section className="cta-row">
        {/* 복습 화면은 다음 단계 — 목업 나오면 플래시카드 화면부터 입힌다 */}
        <Link to="/decks" className="button primary big">
          단어장 보러 가기
        </Link>
      </section>
    </div>
  )
}
