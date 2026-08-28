import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import TopNav from '../components/TopNav'

// 백엔드 GET /stats/overview 계약 — 화면 한 장 = 응답 하나 (2026-08-23)
interface Overview {
  days: { date: string; studyCount: number }[]   // 최근 28일, 0 포함
  streak: number
  bestStreak: number
  totalStudy: number
  activeDays: number
  boxes: { box: number; count: number }[]
  decks: { deckId: number; title: string; cardCount: number; started: number; mastered: number }[]
}

const BOX_LABEL = ['10분', '1일', '3일', '7일', '14일', '30일']

export default function Stats() {
  const [data, setData] = useState<Overview | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    api<Overview>('/stats/overview').then(setData).catch((e) => setError(e.message))
  }, [])

  const maxDay = Math.max(1, ...(data?.days.map((d) => d.studyCount) ?? [1]))
  const boxCounts = [1, 2, 3, 4, 5, 6].map((b) => data?.boxes.find((x) => x.box === b)?.count ?? 0)
  const maxBox = Math.max(1, ...boxCounts)
  const totalCards = boxCounts.reduce((a, b) => a + b, 0)
  const last7 = data?.days.slice(-7).reduce((a, d) => a + d.studyCount, 0) ?? 0

  return (
    <>
      <TopNav streak={data?.streak} />
      <div className="shell">
        <div className="page-head">
          <div>
            <h1>통계</h1>
            <p className="sub">최근 4주 활동 · 연속 학습 · 라이트너 분포 · 덱별 진행률</p>
          </div>
        </div>

        {error && <p className="error" role="alert">{error}</p>}
        {!data && !error && <p className="muted">불러오는 중...</p>}

        {data && (
          <>
            <div className="stats-grid4">
              <Tile label="현재 연속" value={data.streak} unit="일" foot={data.streak > 0 ? '오늘도 이어가는 중' : '오늘 학습하면 다시 시작'} />
              <Tile label="최고 연속" value={data.bestStreak} unit="일" foot="역대 기록" />
              <Tile label="이번 주 학습" value={last7} unit="회" foot="최근 7일 답변 수" />
              <Tile label="누적 학습" value={data.totalStudy} unit="회" foot={`활동일 ${data.activeDays}일`} />
            </div>

            <div className="stat-card" style={{ marginTop: 16 }}>
              <div className="ladder-head">
                <span className="title">최근 28일 활동</span>
                <span className="hint">막대 하나 = 하루 · 높이 = 그날 답변 수</span>
              </div>
              <div className="activity-bars" role="img" aria-label="최근 28일 일별 학습 활동">
                {data.days.map((d) => (
                  <div key={d.date} className="activity-col" title={`${d.date}: ${d.studyCount}회`}>
                    <div
                      className={`activity-bar ${d.studyCount > 0 ? 'on' : ''}`}
                      style={{ height: `${Math.max(4, (d.studyCount / maxDay) * 100)}%` }}
                    />
                  </div>
                ))}
              </div>
              <div className="activity-axis">
                <span>{data.days[0]?.date.slice(5)}</span>
                <span>오늘</span>
              </div>
            </div>

            <div className="stat-card" style={{ marginTop: 16 }}>
              <div className="ladder-head">
                <span className="title">라이트너 박스 분포 · 카드 {totalCards}장</span>
                <span className="hint">오른쪽으로 갈수록 복습 간격이 길어져요</span>
              </div>
              <div className="ladder-bars">
                {boxCounts.map((n, i) => (
                  <div key={i} className="ladder-col">
                    <span className="count">{n}</span>
                    <div
                      className="bar"
                      style={{ height: `${Math.max(6, (n / maxBox) * 100)}%`, background: i < 2 ? 'var(--a)' : 'var(--bar-rest)' }}
                    />
                    <span className="label">{i + 1} · {BOX_LABEL[i]}</span>
                  </div>
                ))}
              </div>
            </div>

            <div className="stat-card" style={{ marginTop: 16 }}>
              <div className="ladder-head">
                <span className="title">덱별 진행률</span>
                <span className="hint">진행 = 한 번이라도 답한 카드 · 숙달 = 박스 5 이상(14일+)</span>
              </div>
              {/* 학습을 시작한 덱만 — 임포트만 해둔 덱 수십 개가 0% 행으로 쏟아지는 소음 방지 (8/28 사용자 피드백) */}
              {data.decks.filter((d) => d.started > 0).length === 0 && (
                <p className="muted">아직 학습을 시작한 덱이 없어요 — 복습·퀴즈로 답하면 여기에 진행률이 쌓여요.</p>
              )}
              {data.decks.filter((d) => d.started > 0).map((d) => {
                const startedPct = d.cardCount ? Math.round((d.started / d.cardCount) * 100) : 0
                const masteredPct = d.cardCount ? Math.round((d.mastered / d.cardCount) * 100) : 0
                return (
                  <div key={d.deckId} className="deck-progress">
                    <div className="deck-progress-head">
                      <Link to={`/decks/${d.deckId}`} className="deck-progress-title">{d.title}</Link>
                      <span className="muted" style={{ fontSize: 12.5 }}>
                        {d.started}/{d.cardCount} 진행 · 숙달 {d.mastered}
                      </span>
                    </div>
                    <div className="progress-track" aria-label={`${d.title} 진행률`}>
                      <div className="progress-fill started" style={{ width: `${startedPct}%` }} />
                      <div className="progress-fill mastered" style={{ width: `${masteredPct}%` }} />
                    </div>
                  </div>
                )
              })}
              {data.decks.some((d) => d.started === 0) && data.decks.some((d) => d.started > 0) && (
                <p className="muted" style={{ margin: '10px 0 0', fontSize: 12.5 }}>
                  아직 시작 안 한 덱 {data.decks.filter((d) => d.started === 0).length}개는 숨겼어요
                </p>
              )}
            </div>
          </>
        )}
      </div>
    </>
  )
}

function Tile({ label, value, unit, foot }: { label: string; value: number; unit: string; foot: string }) {
  return (
    <div className="stat-card">
      <p className="label">{label}</p>
      <div className="value">
        <span className="big num">{value}</span>
        <span className="unit">{unit}</span>
      </div>
      <p className="foot">{foot}</p>
    </div>
  )
}
