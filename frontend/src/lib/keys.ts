// 입력기(한글·일본어) 변환을 확정하는 Enter는 '저장·다음 칸'으로 치지 않는다.
// Chrome은 그 keydown에 isComposing=true를, Safari(WebKit)는 compositionend 뒤에 keyCode 229를 준다 (MDN 권장 검사).
// 타이핑 모드(Typing.tsx)가 먼저 쓰던 규칙을 한곳에 모았다 — 11/14 검토: 맥·아이폰에서 변환 확정 Enter가
// 온라인 시험의 다음 칸으로 넘어가고, 덱 화면에선 설명을 다 쓰기 전에 카드를 저장했다
export function isPlainEnter(e: { key: string; keyCode: number; nativeEvent: { isComposing?: boolean } }): boolean {
  return e.key === 'Enter' && !e.nativeEvent.isComposing && e.keyCode !== 229
}
