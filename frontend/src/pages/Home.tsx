import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { getRecentStudy, agoLabel, resumePath } from '../lib/recent'
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
  folderId: number | null
}

interface Folder {
  id: number
  name: string
}

const SECONDS_PER_CARD = 25 // 예상 시간 어림값 — 카드당 평균 답변 시간

export default function Home() {
  const [summary, setSummary] = useState<TodaySummary | null>(null)
  const [boxes, setBoxes] = useState<BoxCount[] | null>(null)
  // null=로딩 중, []=정말 덱 없음 — 초기값 []로 두면 로딩 순간 기존 유저에게 온보딩이 번쩍임 (Codex 검산)
  const [decks, setDecks] = useState<Deck[] | null>(null)
  const [folders, setFolders] = useState<Folder[]>([])
  const [error, setError] = useState('')

  useEffect(() => {
    api<TodaySummary>('/reviews/today-summary').then(setSummary).catch((e) => setError(e.message))
    // 구버전 백엔드(엔드포인트 없음)면 차트만 조용히 숨김
    api<BoxCount[]>('/reviews/box-distribution').then(setBoxes).catch(() => setBoxes(null))
    api<Deck[]>('/decks')
      .then(setDecks)
      .catch((e) => setError(e.message))   // 삼키면 장애 시 영원히 '신규 유저'로 오판
    api<Folder[]>('/folders').then(setFolders).catch(() => {})   // 폴더 패널만 조용히 숨김
  }, [])

  const today = new Date().toLocaleDateString('ko-KR', { month: 'long', day: 'numeric', weekday: 'long' })
  const due = summary?.dueCount ?? 0
  const dueMinutes = Math.max(1, Math.round((due * SECONDS_PER_CARD) / 60))
  const maxBox = boxes ? Math.max(1, ...boxes.map((b) => b.count)) : 1

  // 디자인 v2: 신규 유저 온보딩 (로딩 중 null엔 판정 보류)
  const isNewUser = decks !== null && decks.length === 0

  // 리디자인 2차(8/30): 최근 학습 덱 — 4모드가 localStorage에 남긴 기록을 덱 목록과 join.
  // 삭제된 덱은 join에서 자연 탈락. 기록이 없으면 내 덱 상위 몇 개로 대신 채운다.
  const recentDecks = getRecentStudy()
    .map((e) => ({ deck: decks?.find((d) => d.id === e.id && d.cardCount > 0), at: e.at, mode: e.mode }))
    .filter((e): e is { deck: Deck; at: number; mode: ReturnType<typeof getRecentStudy>[number]['mode'] } => !!e.deck)
    .slice(0, 3)
  const fallbackDecks = recentDecks.length === 0 ? (decks ?? []).filter((d) => d.cardCount > 0).slice(0, 3) : []

  // 내 폴더 패널 — 폴더별 덱/카드 수는 이미 받은 덱 목록에서 집계 (추가 API 없음)
  const folderCards = folders.map((f) => {
    const inFolder = (decks ?? []).filter((d) => d.folderId === f.id)
    return { id: f.id as number | 'none', name: f.name, deckCount: inFolder.length, cardCount: inFolder.reduce((a, d) => a + d.cardCount, 0) }
  })
  const unfiled = (decks ?? []).filter((d) => d.folderId === null)
  if (folders.length > 0 && unfiled.length > 0) {
    folderCards.push({ id: 'none', name: '미분류', deckCount: unfiled.length, cardCount: unfiled.reduce((a, d) => a + d.cardCount, 0) })
  }

  if (isNewUser) {
    return (
      <>
        <TopNav />
        <div className="shell">
          <div className="section-head">
            <h1>시작하기</h1>
            <span className="date">{today}</span>
          </div>
          <section className="hero onboard">
            <h2 className="onboard-title">첫 단어장을 만들어볼까요?</h2>
            <p className="hero-copy">Quizlet 대신, 내 것으로. 3분이면 충분해요.</p>
            <div className="onboard-steps">
              <div className="onboard-step">
                <span className="step-num">1</span>
                <b>덱 만들기</b>
                <span>주제별 단어장 하나</span>
              </div>
              <div className="onboard-step">
                <span className="step-num">2</span>
                <b>단어 추가</b>
                <span>일단 5장이면 충분</span>
              </div>
              <div className="onboard-step">
                <span className="step-num">3</span>
                <b>오늘 복습</b>
                <span>간격은 라이트너가 알아서</span>
              </div>
            </div>
            <Link to="/decks" className="cta">첫 덱 만들기</Link>
          </section>
        </div>
      </>
    )
  }

  return (
    <>
      <TopNav streak={summary?.streak} />
      <div className="shell">
        <div className="section-head">
          <h1>오늘의 복습</h1>
          <span className="date">{today}</span>
        </div>

        {error && <p className="error" role="alert">{error}</p>}

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

        <div className="home-2col">
          <section className="home-panel">
            <div className="panel-head">
              <h2>최근 학습 덱</h2>
              <Link to="/decks" className="link">전체 보기</Link>
            </div>
            {recentDecks.map(({ deck, at, mode }) => (
              <div className="recent-row" key={deck.id}>
                <div className="recent-info">
                  <Link to={`/decks/${deck.id}`} className="recent-title">{deck.title}</Link>
                  <p className="recent-meta">카드 {deck.cardCount}장{at ? ` · 마지막 학습 ${agoLabel(at)}` : ''}</p>
                </div>
                <Link to={resumePath(deck.id, mode)} className="resume-btn">이어하기</Link>
              </div>
            ))}
            {fallbackDecks.map((d) => (
              <div className="recent-row" key={d.id}>
                <div className="recent-info">
                  <Link to={`/decks/${d.id}`} className="recent-title">{d.title}</Link>
                  <p className="recent-meta">카드 {d.cardCount}장</p>
                </div>
                <Link to={`/study?deckId=${d.id}`} className="resume-btn">학습하기</Link>
              </div>
            ))}
            {recentDecks.length === 0 && fallbackDecks.length === 0 && decks !== null && (
              <p className="muted">카드가 있는 덱이 아직 없어요 — 내 덱에서 시작해보세요.</p>
            )}
            {decks === null && <p className="muted">불러오는 중...</p>}
          </section>

          <section className="home-panel">
            <div className="panel-head">
              <h2>내 폴더</h2>
              <span className="panel-sub">덱 {decks?.length ?? 0}개</span>
            </div>
            {folderCards.map((f) => (
              <Link key={f.id} to={`/decks?folder=${f.id}`} className="folder-row">
                <span className="folder-row-icon">{f.id === 'none' ? '📂' : '📁'}</span>
                <span className="folder-row-info">
                  <span className="folder-row-name">{f.name}</span>
                  <span className="folder-row-meta">{f.deckCount}개 덱 · {f.cardCount.toLocaleString()}장</span>
                </span>
                <span className="folder-row-arrow">›</span>
              </Link>
            ))}
            {folderCards.length === 0 && (
              <p className="muted">내 덱의 "＋ 새 폴더"로 덱을 정리할 수 있어요.</p>
            )}
          </section>
        </div>

        {summary && (
          <div className="stats-2col">
            <div className="stat-card">
              {/* studyCount = 모든 학습 '답변 횟수' (고유 카드 수 아님) — 데이터 정의에 맞는 문구 (Codex 검산) */}
              <p className="label">오늘 학습 활동</p>
              <div className="value">
                <span className="num big">{summary.studyCount}</span>
                <span className="unit">회</span>
              </div>
              <p className="foot">오늘 복습한 카드 {summary.reviewedTodayCount}장</p>
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
      </div>
    </>
  )
}
