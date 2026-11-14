import { api } from './client'

// 학습 랭킹 (ADR-059) — 답한 카드 1장 = 1점, 오늘(KST) / 전체 합계.
// name은 서버가 이미 가린 값일 수 있다(설정의 '닉네임 가리기') — 화면은 그대로 보여 주기만 한다
export interface RankEntry {
  rank: number
  name: string
  points: number
  me: boolean
}

export interface StudyRanking {
  period: 'today' | 'all'
  day: string | null
  entries: RankEntry[]
  me: RankEntry | null   // 아직 점수가 없으면 null
}

export type RankPeriod = StudyRanking['period']

export function fetchStudyRanking(period: RankPeriod, limit = 50): Promise<StudyRanking> {
  return api<StudyRanking>(`/rankings/study?${new URLSearchParams({ period, limit: String(limit) })}`)
}

/** 1~3위는 메달, 그 밖은 숫자 */
export function rankBadge(rank: number): string {
  return rank === 1 ? '🥇' : rank === 2 ? '🥈' : rank === 3 ? '🥉' : String(rank)
}
