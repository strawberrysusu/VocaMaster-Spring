import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { fetchStudyRanking, rankBadge, type RankEntry, type RankPeriod, type StudyRanking } from '../api/ranking'
import TopNav from '../components/TopNav'
import { t } from '../i18n'

// 학습 랭킹 화면 (11/14 사용자 요청, ADR-059) — 왼쪽 메뉴 '랭킹'. 홈에는 오늘 상위 3명 카드가 따로 있다.
// 오늘 / 전체 탭, 상위 50명. 내가 50등 밖이면 맨 아래에 내 줄을 따로 보여 준다
export default function Ranking() {
  const [period, setPeriod] = useState<RankPeriod>('today')
  const [data, setData] = useState<StudyRanking | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let alive = true   // 탭을 빨리 바꾸면 늦게 온 옛 결과가 새 탭을 덮지 않게
    setData(null)
    setError('')
    fetchStudyRanking(period)
      .then((d) => { if (alive) setData(d) })
      .catch((e) => { if (alive) setError((e as Error).message) })
    return () => { alive = false }
  }, [period])

  const meOutside = data?.me && !data.entries.some((e) => e.me) ? data.me : null

  return (
    <>
      <TopNav />
      <div className="shell">
        <div className="page-head">
          <div>
            <h1>{t('학습 랭킹')}</h1>
            <p className="sub">{t('복습·퀴즈·타이핑·듣기에서 답한 카드 1장 = 1점')}</p>
          </div>
        </div>

        <div className="sort-tabs" style={{ marginBottom: 16 }}>
          <button className={period === 'today' ? 'active' : ''} onClick={() => setPeriod('today')}>{t('오늘')}</button>
          <button className={period === 'all' ? 'active' : ''} onClick={() => setPeriod('all')}>{t('전체')}</button>
        </div>

        {error && <p className="error" role="alert">{error}</p>}
        {data === null && !error && <p className="muted">{t('불러오는 중...')}</p>}

        {data !== null && data.entries.length === 0 && (
          <p className="muted">{period === 'today' ? t('아직 오늘 학습한 사람이 없어요') : t('아직 학습 기록이 없어요')}</p>
        )}

        {data !== null && data.entries.length > 0 && (
          <ol className="rank-list">
            {data.entries.map((e, i) => (
              <RankRow key={`${e.rank}-${i}`} entry={e} />
            ))}
            {meOutside && (
              <>
                <li className="rank-gap" aria-hidden="true">⋯</li>
                <RankRow entry={meOutside} />
              </>
            )}
          </ol>
        )}

        <p className="muted" style={{ fontSize: 13, marginTop: 18 }}>
          {t('닉네임은 설정에서 가릴 수 있어요')} <Link to="/settings" className="link">{t('설정')} →</Link>
        </p>
      </div>
    </>
  )
}

function RankRow({ entry }: { entry: RankEntry }) {
  return (
    <li className={`rank-row${entry.me ? ' me' : ''}`}>
      <span className="rank-no">{rankBadge(entry.rank)}</span>
      <span className="rank-name">
        {entry.name}
        {entry.me && <span className="rank-me">{t('나')}</span>}
      </span>
      <span className="rank-points">{t('{n}점', { n: entry.points.toLocaleString() })}</span>
    </li>
  )
}
