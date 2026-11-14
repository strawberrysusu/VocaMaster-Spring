// 영어 화면의 단수·복수 (11/14) — 사전 문구는 복수형 하나('{n} cards')로 쓰고, 그 자리에 들어갈 수가 1이면 단수로 바꾼다.
// 사전 문구(틀)만 고치고 채워 넣을 값은 건드리지 않는다 — 사용자가 쓴 글('1 days' 같은 뜻)이 바뀌면 안 되니까.
// 순수 함수라 Node 단위 테스트(scripts/test-i18n.mjs)가 직접 불러 검사한다.
const SINGULAR: Record<string, string> = {
  cards: 'card',
  decks: 'deck',
  questions: 'question',
  lines: 'line',
  answers: 'answer',
  choices: 'choice',
  columns: 'column',
  days: 'day',
  pts: 'pt',   // 학습 랭킹 점수 (11/14)
}

// '{이름} 복수형' — 목록은 위 SINGULAR의 열쇠와 같다
const COUNTED = /\{(\w+)\} (cards|decks|questions|lines|answers|choices|columns|days|pts)\b/g

/** '{n} cards' 꼴에서 n이 1이면 '{n} card'로 — t()가 값을 채우기 전에 부른다 */
export function englishOne(template: string, vars: Record<string, string | number>): string {
  return template.replace(COUNTED, (whole, name: string, plural: string) =>
    Number(vars[name]) === 1 ? `{${name}} ${SINGULAR[plural]}` : whole,
  )
}
