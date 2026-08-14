import { Link, useParams } from 'react-router-dom'
import TopNav from '../components/TopNav'

// 아직 안 입힌 화면의 정직한 표지판 — 가짜 UI 대신
export default function ComingSoon() {
  const { name } = useParams()
  return (
    <>
      <TopNav />
      <div className="shell stub">
        <h2>{name} 화면 — 공사 중 🚧</h2>
        <p>목업은 이미 있어요. 순서대로 입히는 중입니다.</p>
        <p>
          <Link to="/" className="link" style={{ color: 'var(--a)' }}>← 홈으로</Link>
        </p>
      </div>
    </>
  )
}
