// 최근 학습 덱 기록 (8/30, 홈 대시보드 2차) — 서버 API 없이 이 브라우저의 localStorage로.
// 기기별로 따로 쌓이는 "이어하기" 편의 기능이라 서버 동기화 대상이 아니다 (백엔드 동결 준수).

const KEY = 'vm.recentStudy'
const LEGACY_KEY = 'vm.lastStudyDeckId' // 구버전: 마지막 1개만 저장하던 키
const MAX = 5

export interface RecentEntry {
  id: number
  at: number // epoch ms, 0 = 시각 미상(구버전 키에서 승격)
}

export function recordRecentStudy(deckId: number | string) {
  const id = Number(deckId)
  if (!Number.isFinite(id)) return
  try {
    const list = getRecentStudy().filter((e) => e.id !== id)
    list.unshift({ id, at: Date.now() })
    localStorage.setItem(KEY, JSON.stringify(list.slice(0, MAX)))
  } catch {
    /* 저장 불가(시크릿 모드 등)여도 학습은 계속 */
  }
}

export function getRecentStudy(): RecentEntry[] {
  try {
    const raw = localStorage.getItem(KEY)
    const list: RecentEntry[] = raw ? JSON.parse(raw) : []
    const legacy = Number(localStorage.getItem(LEGACY_KEY))
    if (Number.isFinite(legacy) && legacy > 0 && !list.some((e) => e.id === legacy)) {
      list.push({ id: legacy, at: 0 })
    }
    return list.filter((e) => Number.isFinite(e?.id))
  } catch {
    return []
  }
}

// "오늘 / 어제 / N일 전" — 달력 날짜 기준
export function agoLabel(at: number): string {
  if (!at) return ''
  const day = (t: number) => {
    const d = new Date(t)
    return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
  }
  const days = Math.round((day(Date.now()) - day(at)) / 86400000)
  if (days <= 0) return '오늘'
  if (days === 1) return '어제'
  return `${days}일 전`
}
