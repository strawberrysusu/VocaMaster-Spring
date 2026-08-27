// 후리가나 표시 — 읽기가 있으면 단어 위에 작게 얹는다 (HTML 표준 <ruby>)
// 목록 계열에서 사용. 학습·퀴즈 화면은 큰 카드라 위 별도 표시(.reading)를 유지
export default function Ruby({ front, reading }: { front: string; reading?: string | null }) {
  if (!reading) return <>{front}</>
  return (
    <ruby>
      {front}
      <rt>{reading}</rt>
    </ruby>
  )
}
