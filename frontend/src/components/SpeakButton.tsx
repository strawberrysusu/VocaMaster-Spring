import type { KeyboardEvent, MouseEvent } from 'react'
import { isTtsSupported, speak } from '../lib/tts'

// 🔊 발음 듣기 — 진짜 <button>. 버튼 안에 중첩하면 HTML 규칙 위반 + Enter/Space가 부모로 전파되므로
// 호출자는 반드시 '형제'로 배치한다 (Study 카드는 wrap 안에서 absolute로 띄움)
export default function SpeakButton({ text, size = 'sm', className = '' }: { text: string; size?: 'sm' | 'lg'; className?: string }) {
  if (!isTtsSupported()) return null
  function onClick(e: MouseEvent<HTMLButtonElement>) {
    e.stopPropagation()
    speak(text)
  }
  function onKeyDown(e: KeyboardEvent<HTMLButtonElement>) {
    e.stopPropagation() // Enter/Space가 부모(카드 뒤집기 등)로 번지지 않게 — 실행은 button 기본 동작이 onClick으로
  }
  return (
    <button
      type="button"
      className={`speak-btn ${size} ${className}`}
      aria-label={`${text} 발음 듣기`}
      title="발음 듣기"
      onClick={onClick}
      onKeyDown={onKeyDown}
    >
      🔊
    </button>
  )
}
