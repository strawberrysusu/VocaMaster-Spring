import { test, expect, makeDeck, listCards, answerCards } from './support'

// Codex 7차 검산(11/16, ADR-061): 답을 저장한 요청이 DB 연결을 쥔 채, 커밋 직후의 인기 점수 리스너가 연결을 하나 더 요구했다.
// 풀(기본 10개)만큼의 요청이 동시에 그 자리에 오면 모두가 남의 연결만 기다려 30초 동안 앱 전체가 멈췄고,
// 답의 일부는 401·500으로 돌아왔다. 실제 jar(운영과 같은 application.yml·기본 풀 크기)에 한꺼번에 보내 본다
test('답 30개를 한꺼번에 보내도 전부 바로 저장된다 (DB 연결을 서로 기다리지 않는다)', async ({ request }) => {
  const deckId = await makeDeck(request, '동시제출', Array.from({ length: 31 }, (_, i) => `burst${i}|뜻${i}`))
  const ids = (await listCards(request, deckId)).map((c) => c.id)

  // 오늘의 통계 줄이 이미 있는 상태(공부하던 도중)에서 몰아 보낸다 — 첫 줄을 만드는 경쟁은 이 테스트가 보려는 것이 아니다
  expect(await answerCards(request, ids.slice(0, 1))).toEqual([200])

  const started = Date.now()
  const statuses = await answerCards(request, ids.slice(1))
  const took = Date.now() - started

  expect(statuses).toEqual(ids.slice(1).map(() => 200))
  expect(took, '답 30개 동시 저장에 걸린 시간(ms)').toBeLessThan(10_000)
})
