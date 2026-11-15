import { useEffect, useRef, useState } from 'react'

/**
 * 펼침 메뉴 하나 — 바깥을 누르거나, Esc를 누르거나, Tab으로 칸 밖으로 나가면 닫힌다
 * (포커스가 나가도 안 닫으면 검색 목록·+ 메뉴·프로필 메뉴가 겹쳐 열렸다, 11/15 점검).
 * 메뉴(role="menu")면 ↑↓·Home·End로 항목 사이를 옮기고, Esc로 닫으면 포커스를 여는 버튼으로 돌려준다 —
 * role="menu"라고 알렸으면 화면 낭독기·키보드 사용자는 이 키들이 될 거라 기대한다 (11/15 점검).
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
      const box = ref.current
      const inside = !!box && box.contains(document.activeElement)
      if (e.key === 'Escape') {
        // 메뉴 안에 포커스가 있었으면 여는 버튼으로 돌려준다 — 안 그러면 항목이 사라지면서 포커스가 문서 맨 위로 떨어진다
        if (inside) box.querySelector<HTMLElement>('[aria-haspopup]')?.focus()
        setOpen(false)
        return
      }
      if (!inside || !['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(e.key)) return
      const items = Array.from(box.querySelectorAll<HTMLElement>('[role="menuitem"]:not([aria-disabled="true"])'))
      if (items.length === 0) return   // 메뉴가 아닌 칸(검색 목록)은 그 칸이 직접 다룬다
      e.preventDefault()   // 화살표가 화면을 스크롤하지 않게
      const at = items.indexOf(document.activeElement as HTMLElement)   // 여는 버튼에 있으면 -1
      const next =
        e.key === 'Home' ? 0
        : e.key === 'End' ? items.length - 1
        : e.key === 'ArrowDown' ? (at + 1) % items.length
        : at <= 0 ? items.length - 1 : at - 1
      items[next].focus()
    }
    const onFocus = (e: FocusEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    document.addEventListener('focusin', onFocus)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
      document.removeEventListener('focusin', onFocus)
    }
  }, [open])
  return { open, setOpen, ref }
}
