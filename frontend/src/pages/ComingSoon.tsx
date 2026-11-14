import { Link, useParams } from 'react-router-dom'
import TopNav from '../components/TopNav'
import { t } from '../i18n'

// 아직 안 입힌 화면의 정직한 표지판 — 가짜 UI 대신
export default function ComingSoon() {
  const { name } = useParams()
  return (
    <>
      <TopNav />
      <div className="shell stub">
        <h2>{t('{name} 화면 — 공사 중 🚧', { name: name ?? '' })}</h2>
        <p>{t('목업은 이미 있어요. 순서대로 입히는 중입니다.')}</p>
        <p>
          <Link to="/" className="link" style={{ color: 'var(--a)' }}>{t('← 홈으로')}</Link>
        </p>
      </div>
    </>
  )
}
