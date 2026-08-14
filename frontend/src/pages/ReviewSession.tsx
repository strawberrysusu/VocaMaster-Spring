import { Link } from 'react-router-dom'
import TopNav from '../components/TopNav'

// 다음 시공 예정 화면 — 목업의 플립 카드(알아요/몰라요, 박스 이동 표시)가 들어올 자리.
// 가짜 UI로 채우지 않고 공사중임을 정직하게 표시한다.
export default function ReviewSession() {
  return (
    <>
      <TopNav />
      <div className="shell stub">
        <h2>플래시카드 복습 화면 — 공사 중 🚧</h2>
        <p>다음으로 입힐 화면이에요. 그때까지 복습은 기존 화면(8080)에서 할 수 있어요.</p>
        <p>
          <Link to="/" className="link" style={{ color: 'var(--a)' }}>
            ← 홈으로
          </Link>
        </p>
      </div>
    </>
  )
}
