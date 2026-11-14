// 텍스트로 발음 언어 판별 — 덱에 언어 필드가 없어서 휴리스틱 (가나·한자 → 일본어, 한글 → 한국어, 그 외 → 영어)
// tts.ts에서 떼어 냄: 다른 모듈을 import 하지 않아야 Node 단위 테스트(scripts/test-text.mjs)가 불러올 수 있다 (11/14)
// · 일본어: 々〆 3005–3006 + 가나 3040–30FF + 한자(testSheet.ts의 KANJI와 같은 범위: 확장 A 3400–4DBF·통합 4E00–9FFF·
//   호환 F900–FAFF·BMP 밖 20000–3FFFF — 𠮷·𩸽·㐂처럼 한자만 있는 단어도, u 플래그 필요)
// · \u 표기: 글자를 그대로 쓰면 도구의 정규화로 범위가 바뀔 수 있다 (testSheet.ts의 KANJI와 같은 이유)
const JA = /[\u3005\u3006\u3040-\u30FF\u3400-\u4DBF\u4E00-\u9FFF\uF900-\uFAFF\u{20000}-\u{3FFFF}]/u
const KO = /[\uAC00-\uD7AF]/

export function detectLang(text: string): string {
  if (JA.test(text)) return 'ja-JP'
  if (KO.test(text)) return 'ko-KR'
  return 'en-US'
}
