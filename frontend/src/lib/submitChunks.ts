// 일괄 제출 나누기 (11/15 점검) — 서버는 한 요청에 답 1000개까지만 받는다 (BatchAnswerRequest.MAX_ANSWERS).
// 복습할 카드가 1000장 넘게 쌓였거나 큰 덱의 전체 복습이면 답이 1000개를 넘는데, 예전엔 통째로 보내 400이 났고
// '다시 보내기'도 계속 400이라 답을 전부 버려야 했다 → 1000개씩 나눠 차례로 보낸다.
// 순수 함수라 Node 단위 테스트(scripts/test-submit.mjs)가 직접 불러 검사한다.

export const SUBMIT_BATCH = 1000

export interface AnswerItem {
  cardId: number
  correct: boolean
}

export interface SubmitChunk {
  submissionId: string
  answers: AnswerItem[]
}

/**
 * 답안을 size개씩 나누고 묶음마다 제출 ID를 붙인다.
 * · 첫 묶음은 원래 ID 그대로 — 1000개 이하면 예전과 똑같은 요청 하나다
 * · 둘째 묶음부터는 '원래 ID 앞 32자 + ~ + 묶음 번호 3자리' (서버가 ID를 36자까지만 받아 뒤에 덧붙일 수 없다.
 *   ~는 원래 ID(UUID·대체값)에 나올 수 없는 글자라 어떤 원래 ID와도 겹치지 않는다)
 * · 카드 번호 순으로 자르므로 같은 답안이면 언제 불러도 같은 묶음·같은 ID가 나온다 →
 *   중간에 끊겨 다시 보내도 서버가 묶음마다 '이미 받은 제출'로 알아본다 (같은 ID + 같은 내용)
 */
export function splitSubmission(submissionId: string, answers: Record<number, boolean>, size = SUBMIT_BATCH): SubmitChunk[] {
  const items = Object.entries(answers)
    .map(([cardId, correct]) => ({ cardId: Number(cardId), correct }))
    .sort((a, b) => a.cardId - b.cardId)
  const chunks: SubmitChunk[] = []
  for (let start = 0; start < items.length; start += size) {
    const n = start / size
    chunks.push({
      submissionId: n === 0 ? submissionId : `${submissionId.slice(0, 32)}~${n.toString(36).padStart(3, '0')}`,
      answers: items.slice(start, start + size),
    })
  }
  return chunks
}
