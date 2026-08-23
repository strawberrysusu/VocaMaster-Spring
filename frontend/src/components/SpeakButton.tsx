import type { MouseEvent } from 'react'
import { isTtsSupported, speak } from '../lib/tts'

// 🔊 발음 듣기 — 부모가 버튼(학습 카드)일 수 있어 클릭 전파를 막는다
export default function SpeakButton({ text, size = 'sm' }: { text: string; size?: 'sm' | 'lg' }) {
  if (!isTtsSupported()) return null
  function onClick(e: MouseEvent) {
    e.stopPropagation()
    e.preventDefault()
    speak(text)
  }
  return (
    <span
      role="button"
      tabIndex={0}
      className={`speak-btn ${size}`}
      aria-label={`${text} 발음 듣기`}
      title="발음 듣기"
      onClick={onClick}
      onKeyDown={(e) => e.key === 'Enter' && speak(text)}
    >
      🔊
    </span>
  )
}
