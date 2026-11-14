import { useEffect, useRef, useState } from 'react'

/**
 * 펼침 메뉴 하나 — 바깥을 누르거나 Esc면 닫힌다.
 * 위쪽 바의 + 메뉴·프로필 메뉴, 덱 화면의 낱말카드 메뉴가 같이 쓴다 (11/14).
 * ref는 버튼과 메뉴를 감싼 칸에 단다 — 그 칸 밖을 누르면 닫힌다
 */
export function useDropdown() {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!open) return
    const onDown = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])
  return { open, setOpen, ref }
}
