import { useCallback, useEffect, useRef, useState } from 'react'
import type { MouseEvent, PointerEvent as ReactPointerEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import { fetchAllCards } from '../api/cards'
import { recordRecentStudy } from '../lib/recent'
import { loadSettings, saveSettings } from '../lib/settings'
import { isTtsSupported, speak, stopSpeaking } from '../lib/tts'
import TopNav from '../components/TopNav'

interface StudyCard {
  cardId: number
  front: string
  back: string
  reading?: string | null
  starred: boolean
}

interface BatchResult {
  total: number
  known: number
  unknown: number
  alreadySubmitted: boolean
}

/** cardId → 알아요(true) / 몰라요(false). 여기 없는 카드는 '미응답' */
type Answers = Record<number, boolean>

interface Draft {
  submissionId: string
  answers: Answers
  /**
   * 실제로 POST를 시도한 순간 동결된 답안.
   * 이게 있으면 편집을 막고, 재시도는 <b>오직 이것만</b> 보낸다.
   * 응답이 유실돼도 무엇을 보냈는지 잃지 않기 위한 장치 — 없으면 재시도가
   * '지금 답안'을 보내게 되어 이미 반영된 카드까지 다시 세어진다.
   */
  attempted?: Answers
}

/**
 * 초안 복원 — JSON 파싱 성공은 '모양이 맞다'는 뜻이 아니다.
 * 여기서 걸러내지 못한 쓰레기는 그대로 서버 payload가 되어 400을 맞는다.
 */
function parseAnswers(v: unknown): Answers | null {
  if (!v || typeof v !== 'object' || Array.isArray(v)) return null
  const clean: Answers = {}
  for (const [key, value] of Object.entries(v as Record<string, unknown>)) {
    const cardId = Number(key)
    if (!Number.isInteger(cardId) || cardId <= 0) return null
    if (typeof value !== 'boolean') return null
    clean[cardId] = value
  }
  return clean
}

function parseDraft(raw: string): Draft | null {
  try {
    const d: unknown = JSON.parse(raw)
    if (!d || typeof d !== 'object') return null
    const { submissionId, answers, attempted } = d as {
      submissionId?: unknown; answers?: unknown; attempted?: unknown
    }
    if (typeof submissionId !== 'string' || submissionId.length === 0 || submissionId.length > 36) return null

    const cleanAnswers = parseAnswers(answers)
    if (!cleanAnswers) return null

    if (attempted === undefined) return { submissionId, answers: cleanAnswers }
    const cleanAttempted = parseAnswers(attempted)
    if (!cleanAttempted || Object.keys(cleanAttempted).length === 0) return null
    return { submissionId, answers: cleanAnswers, attempted: cleanAttempted }
  } catch {
    return null
  }
}

/**
 * Leitner 복습 학습 — 두 입구, 한 흐름:
 * - /study            → 오늘 복습 (due 카드 전체)
 * - /study?deckId=N   → 그 덱의 전 카드 (새 카드는 첫 답변으로 박스 1 입장)
 *
 * 설계 결정(2026-09-01, ADR-050): 세션 도중의 알아요/몰라요는 <b>프론트의 임시 답안</b>이고,
 * '학습 완료'를 누를 때 /reviews/answers/batch 한 번으로 전체가 확정된다.
 *
 * 왜 즉시 저장을 버렸나 — 답할 때마다 Leitner 박스를 움직이면 이전 카드로 돌아가 답을 고칠 수 없다.
 * 되돌리려면 박스를 되돌려야 하는데, 오답은 boxLevel을 1로 풀 리셋해서 이전 값이 어디에도 안 남는다.
 * 임시 답안으로 두면 '되돌리기'가 아니라 '제출 전 답안 수정'이 되어 문제 자체가 사라진다.
 *
 * 잃은 것: 답변 직후의 "직전 카드 → 박스 N" 표시. 박스는 완료 시점에만 움직이므로
 * 세션 도중에는 보여줄 값이 없다. 완료 화면의 합계로 대체한다.
 *
 * 이전 방식(카드마다 POST /reviews/cards/{id}/answer)은 서버에 그대로 남아 있다 — 다른 진입점의 계약이라 건드리지 않는다.
 */
export default function Study() {
  const [params] = useSearchParams()
  const deckId = params.get('deckId')
  const starredOnly = params.get('starredOnly') === '1'   // /study?deckId=N&starredOnly=1 (덱 상세 '⭐만' 진입)

  const [queue, setQueue] = useState<StudyCard[] | null>(null)
  const [idx, setIdx] = useState(0)
  const [revealed, setRevealed] = useState(false)
  const [answers, setAnswers] = useState<Answers>({})
  const [submissionId, setSubmissionId] = useState('')
  const [reviewing, setReviewing] = useState(false)   // 마지막 카드를 지나 '제출 전 검토' 화면
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<BatchResult | null>(null)
  const [starring, setStarring] = useState(false)
  // POST를 시도한 순간 동결된 답안. null이 아니면 '보냈는데 결과를 모르는 상태'
  const [attempted, setAttempted] = useState<Answers | null>(null)
  // 같은 ID에 다른 답안이 갔다는 서버 판정 — 정상 흐름에선 나올 수 없다(항상 동결본만 보내므로).
  // 나오면 초안이 손상됐거나 프론트 버그다. 재시도는 무의미하니 버리는 길만 남긴다
  const [retryBlocked, setRetryBlocked] = useState(false)
  const [error, setError] = useState('')
  // 9/17 뒤집으면 자동 발음 — 설정(localStorage)과 같은 값. 이 화면의 🔊 스위치가 설정을 바꾼다
  const [autoSpeak, setAutoSpeak] = useState(() => loadSettings().autoSpeak)
  // 9/17 스와이프 — 카드가 손가락을 따라간 거리(px). 0이면 제자리
  const [dragX, setDragX] = useState(0)
  const [dragging, setDragging] = useState(false)

  // 초안 키는 입구별로 분리 — 덱 학습과 전체 복습의 답안이 섞이면 안 된다
  const draftKey = `vm.study.draft.${deckId ?? 'due'}${starredOnly ? '.starred' : ''}`
  const draftLoaded = useRef(false)
  const cardRef = useRef<HTMLButtonElement>(null)
  // 카드 전환 잠금 — 알아요를 빠르게 두 번 누르면 setIdx가 두 번 돌아 카드를 건너뛰고,
  // idx가 total을 넘으면 어느 블록도 안 그려져 화면이 하얘진다 (Codex 검산 2026-09-01)
  const advancing = useRef(false)
  // 스와이프 진행 상태. axis는 첫 12px 이동으로 가로/세로를 한 번만 판정 — 세로면 스크롤에 양보한다
  const swipe = useRef<{ id: number; x: number; y: number; axis: 'h' | 'v' | null } | null>(null)
  // 밀었다 놓으면 브라우저가 click까지 쏜다 — 그 click이 카드를 뒤집지 않게 한 번 삼킨다
  const suppressClick = useRef(false)
  // 카드가 화면 밖으로 날아가는 중 — 그 220ms 동안은 입력을 받지 않는다
  const flying = useRef(false)
  const [flyingCls, setFlyingCls] = useState(false)

  function newSubmissionId() {
    // crypto.randomUUID는 보안 컨텍스트(https/localhost)에서만 있다 — 없으면 충분히 흩어지는 대체값
    return typeof crypto !== 'undefined' && 'randomUUID' in crypto
      ? crypto.randomUUID()
      : `s-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
  }

  const loadQueue = useCallback(() => {
    setQueue(null)
    setIdx(0)
    setRevealed(false)
    setReviewing(false)
    setResult(null)
    setError('')
    setAttempted(null)
    setRetryBlocked(false)
    advancing.current = false

    // 새로고침 복구 — 서버 성공 응답을 받은 뒤에만 지우므로, 여기 남아 있으면 아직 미제출이다
    let draft: Draft | null = null
    const raw = (() => {
      try {
        return sessionStorage.getItem(draftKey)
      } catch {
        return null
      }
    })()
    if (raw) draft = parseDraft(raw)   // 손상된 초안은 조용히 버린다 — 학습을 막을 이유가 없다
    setAnswers(draft?.answers ?? {})
    setAttempted(draft?.attempted ?? null)   // 있으면 아래에서 편집을 막고 재시도만 시킨다
    setSubmissionId(draft?.submissionId ?? newSubmissionId())
    draftLoaded.current = true

    if (deckId) {
      fetchAllCards(deckId)
        .then(({ cards }) => {
          const picked = starredOnly ? cards.filter((c) => c.starred) : cards
          setQueue(
            picked.map((c) => ({
              cardId: c.id,
              front: c.front,
              back: c.back,
              reading: c.reading,
              starred: !!c.starred,
            })),
          )
          recordRecentStudy(deckId) // 홈 '최근 학습 덱' 재료
        })
        .catch((e) => setError(e.message))
    } else {
      // due 응답에도 starred가 실려 온다 — 없으면 별이 항상 꺼진 채로 뜬다
      api<StudyCard[]>('/reviews/due')
        .then((cards) => setQueue(cards.map((c) => ({ ...c, starred: !!c.starred }))))
        .catch((e) => setError(e.message))
    }
  }, [deckId, starredOnly, draftKey])

  useEffect(loadQueue, [loadQueue])

  /** 초안 쓰기 — submit()이 네트워크보다 먼저 부를 수 있게 동기 함수로 둔다 */
  const persistDraft = useCallback(
    (d: Draft) => {
      try {
        sessionStorage.setItem(draftKey, JSON.stringify(d))
      } catch {
        /* 저장 실패(용량·프라이빗 모드)로 학습을 막지는 않는다 */
      }
    },
    [draftKey],
  )

  const clearDraft = useCallback(() => {
    try {
      sessionStorage.removeItem(draftKey)
    } catch {
      /* 못 지워도 화면 상태는 진행한다 */
    }
  }, [draftKey])

  // 임시 답안을 매번 남긴다 — 새로고침에서 살아남는 방어선
  useEffect(() => {
    if (!draftLoaded.current || !submissionId) return
    if (result) return   // 제출이 확정된 뒤에는 초안을 다시 쓰지 않는다
    if (Object.keys(answers).length === 0 && !attempted) {
      clearDraft()
      return
    }
    persistDraft({ submissionId, answers, ...(attempted ? { attempted } : {}) })
  }, [answers, attempted, result, submissionId, persistDraft, clearDraft])

  const total = queue?.length ?? 0
  const card = queue && idx < total ? queue[idx] : null

  // 카드가 실제로 바뀐 뒤에야 잠금을 푼다 — 타이머로 풀면 느린 기기에서 여전히 두 칸 넘어간다.
  // 겸사겸사 포커스를 카드로 옮겨 Enter 연타가 방금 누른 버튼을 다시 때리지 않게 한다.
  useEffect(() => {
    advancing.current = false
    setDragX(0)   // 화살표·버튼으로 넘어온 경우의 안전장치 (스와이프는 flyOut이 pick 전에 이미 0으로 돌린다)
    setFlyingCls(false)
    if (card) cardRef.current?.focus({ preventScroll: true })
  }, [idx, reviewing, card])

  // 화면을 떠나면 읽던 발음을 끊는다
  useEffect(() => () => stopSpeaking(), [])

  /** 초안에 담긴 답의 수. queue와 무관하다 — 응답 유실 후 queue가 비어도 이 값은 남는다 */
  const draftCount = Object.keys(answers).length
  const answeredInQueue = queue ? queue.filter((c) => answers[c.cardId] !== undefined).length : 0

  /** 답을 고른다. 이미 고른 카드도 그대로 덮어쓴다 — 이게 '제출 전 답안 수정'의 전부다 */
  function pick(correct: boolean) {
    // 제출을 한 번 시도한 뒤에는 답을 고칠 수 없다. 동결본과 달라지면
    // 재시도가 '다른 답안'이 되어 서버가 409로 거절한다
    if (!card || advancing.current || attempted) return
    advancing.current = true
    setAnswers((prev) => ({ ...prev, [card.cardId]: correct }))
    goNext()
  }

  function goNext() {
    stopSpeaking()   // 이전 카드 발음이 다음 카드 위에서 계속 나오지 않게
    setRevealed(false)
    if (idx + 1 >= total) setReviewing(true)   // 마지막 카드를 지나면 제출 전 검토
    else setIdx((i) => i + 1)
  }

  function goPrev() {
    stopSpeaking()
    setRevealed(false)
    advancing.current = false
    if (reviewing) setReviewing(false)
    else if (idx > 0) setIdx((i) => i - 1)
  }

  /**
   * 일괄 제출. 핵심은 <b>네트워크보다 먼저 payload를 동결</b>한다는 것.
   *
   * <p>응답이 유실되면 서버가 처리했는지 클라이언트는 알 수 없다. 그때 '지금 답안'을 다시 보내면
   * 그 사이 늘어난 답까지 섞여 들어가 이미 반영된 카드가 한 번 더 세어진다(박스 과승급).
   * 보내기 직전의 payload를 그대로 저장해 두고 <b>같은 submissionId + 같은 내용</b>으로만 재시도하면,
   * 서버의 unique 제약과 payload_hash가 비로소 제 역할을 한다 —
   * 이미 처리됐으면 멱등 응답, 아직이면 이번에 처음 반영.</p>
   */
  async function submit() {
    if (submitting) return

    // 이미 동결된 게 있으면 그것만 보낸다. 없으면 지금 답안을 동결한다
    let frozen = attempted
    if (!frozen) {
      const current = { ...answers }
      if (Object.keys(current).length === 0) {
        setError('아직 답한 카드가 없어요')
        return
      }
      // ★ 저장이 먼저, 전송이 나중. 순서가 뒤바뀌면 응답 유실 시 '무엇을 보냈는지'를 잃는다
      persistDraft({ submissionId, answers: current, attempted: current })
      setAttempted(current)
      frozen = current
    }

    const payload = Object.entries(frozen).map(([cardId, correct]) => ({
      cardId: Number(cardId),
      correct,
    }))

    setSubmitting(true)
    setError('')
    try {
      const res = await api<BatchResult>('/reviews/answers/batch', {
        method: 'POST',
        body: JSON.stringify({ submissionId, answers: payload }),
      })
      // 서버가 받은 뒤에만 초안을 버린다 — 순서가 바뀌면 실패 시 답안이 통째로 날아간다.
      // answers까지 비우는 이유: 남겨두면 아래 자동저장 이펙트가 attempted 변화에 반응해
      // 방금 지운 초안을 그대로 되살린다 (브라우저 스모크에서 실측)
      clearDraft()
      setAttempted(null)
      setAnswers({})
      setResult(res)
    } catch (e) {
      // 동결본만 보내므로 정상 흐름에서는 나올 수 없는 판정이다.
      // 나왔다면 초안이 손상됐거나 프론트 버그 — 재시도해봐야 계속 409다
      if (e instanceof ApiError && e.code === 'SUBMISSION_MISMATCH') setRetryBlocked(true)
      setError((e as Error).message)
    } finally {
      setSubmitting(false)
    }
  }

  /** 초안을 버리고 새 세션으로 시작한다 (재시도가 막혔거나 사용자가 포기할 때) */
  function discardDraft() {
    clearDraft()
    setAnswers({})
    setAttempted(null)
    setRetryBlocked(false)
    setSubmissionId(newSubmissionId())
    setError('')
    setReviewing(false)
    setIdx(0)
  }

  /**
   * ⭐ 토글 — 학습 결과와 무관하게 즉시 저장한다(일괄 제출에 끼지 않는다).
   * 큐에서 빼지는 않는다: starredOnly 세션에서 별을 떼는 순간 카드가 사라지면
   * 진행 중인 목록이 발밑에서 흔들린다. 해제는 '다음 세션'부터 반영된다.
   */
  async function toggleStar() {
    if (starring || !card) return
    const next = !card.starred
    const patch = (v: boolean) =>
      setQueue((q) => (q ? q.map((c) => (c.cardId === card.cardId ? { ...c, starred: v } : c)) : q))

    setStarring(true)
    patch(next) // 낙관적 반영 — 실패하면 되돌린다
    try {
      const res = await api<{ starred: boolean }>(`/cards/${card.cardId}/star`, { method: 'PATCH' })
      patch(!!res.starred) // 서버 확정값으로 정렬
    } catch (e) {
      patch(!next)
      setError((e as Error).message)
    } finally {
      setStarring(false)
    }
  }

  const speakText = card ? card.reading || card.front : ''   // 읽기가 있으면 읽기를 읽는다 — 한자 TTS 오독 방지

  /**
   * 카드 앞↔뒤. 뒤로 넘어갈 때 자동 발음(켜져 있으면). 다시 뒤집으면 다시 읽어 준다 —
   * 그래서 별도의 '다시 듣기' 버튼이 없다 (9/17, 사용자 제안: 버튼은 켜기/끄기 스위치로).
   */
  function flip() {
    if (!card) return
    const next = !revealed
    setRevealed(next)
    if (next && autoSpeak) speak(speakText)
    else if (!next) stopSpeaking()
  }

  /** 🔊 스위치 — 설정과 같은 값(설정 화면에도 보인다). 켜는 순간 뒷면이면 바로 읽어 줘서 켜졌다는 걸 귀로 안다 */
  function toggleSound() {
    const next = !autoSpeak
    setAutoSpeak(next)
    saveSettings({ ...loadSettings(), autoSpeak: next })
    if (next && revealed && card) speak(speakText)
    if (!next) stopSpeaking()
  }

  // ── 스와이프 (9/17, Quizlet식): 오른쪽 = 알아요, 왼쪽 = 몰라요 ──
  const SWIPE_DECIDE = 12   // 이만큼 움직여야 가로/세로를 판정한다 (그 전까지는 탭)
  const SWIPE_COMMIT = 90   // 이만큼 밀고 놓으면 답으로 확정, 덜 밀면 제자리로 돌아간다

  /** 확정된 스와이프 — 밀던 방향으로 날려 보낸 뒤 답을 기록한다. 시간은 CSS .flying의 0.22s와 맞춘다 */
  function flyOut(dir: 1 | -1) {
    if (flying.current) return
    flying.current = true
    setFlyingCls(true)
    setDragX(dir * Math.max(window.innerWidth, 480))
    window.setTimeout(() => {
      flying.current = false
      // 세 호출이 한 틱에 묶여 다음 카드는 처음부터 transform 0으로 그려진다. 카드 요소는 key={cardId}라
      // 카드마다 새로 만들어지므로 날아간 자리에서 미끄러져 들어오는 전환 자체가 없다
      // (예전 방식: 한 프레임짜리 플래그 + requestAnimationFrame — 안 보이는 탭에선 rAF가 멈춰 플래그가 안 풀렸다, 9/17 실측)
      setFlyingCls(false)
      setDragX(0)
      pick(dir > 0)
    }, 220)
  }

  function onCardPointerDown(e: ReactPointerEvent<HTMLButtonElement>) {
    if (flying.current) return
    if (e.pointerType === 'mouse' && e.button !== 0) return
    swipe.current = { id: e.pointerId, x: e.clientX, y: e.clientY, axis: null }
    suppressClick.current = false
  }

  function onCardPointerMove(e: ReactPointerEvent<HTMLButtonElement>) {
    const s = swipe.current
    if (!s || s.id !== e.pointerId) return
    const dx = e.clientX - s.x
    const dy = e.clientY - s.y
    if (!s.axis) {
      if (Math.abs(dx) < SWIPE_DECIDE && Math.abs(dy) < SWIPE_DECIDE) return
      s.axis = Math.abs(dx) > Math.abs(dy) ? 'h' : 'v'   // 세로가 이기면 스크롤에 양보 (CSS touch-action: pan-y)
      if (s.axis === 'h') {
        e.currentTarget.setPointerCapture(e.pointerId)   // 손가락이 카드 밖으로 나가도 move/up을 계속 받는다
        setDragging(true)
      }
    }
    if (s.axis !== 'h') return
    suppressClick.current = true
    setDragX(dx)
  }

  function onCardPointerEnd(e: ReactPointerEvent<HTMLButtonElement>, commit: boolean) {
    const s = swipe.current
    if (!s || s.id !== e.pointerId) return
    swipe.current = null
    setDragging(false)
    if (s.axis === 'h') {
      if (e.currentTarget.hasPointerCapture(e.pointerId)) e.currentTarget.releasePointerCapture(e.pointerId)
      const dx = e.clientX - s.x
      if (commit && Math.abs(dx) >= SWIPE_COMMIT) {
        flyOut(dx > 0 ? 1 : -1)   // pick은 날아간 뒤에 — 전환 중·제출 시도 후 잠금은 pick이 그대로 적용한다
        return
      }
    }
    setDragX(0)   // 덜 밀었으면 제자리로 (CSS transition)
  }

  const backTo = deckId ? `/decks/${deckId}` : '/'

  /**
   * 탭을 닫거나 다른 사이트로 떠나려 할 때 브라우저 기본 경고.
   * 초안은 sessionStorage라 <b>탭이 닫히는 순간 함께 사라진다</b> — 새로고침은 살아남지만 탭 종료는 아니다.
   * 그래서 여기서 한 번 붙잡는 것이 마지막 방어선이다.
   */
  useEffect(() => {
    if (draftCount === 0 || result) return
    const warn = (e: BeforeUnloadEvent) => {
      e.preventDefault()
      e.returnValue = ''   // 옛 브라우저 호환 — 문구는 브라우저가 정한다
    }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [draftCount, result])

  function confirmQuit(e: MouseEvent) {
    // 미제출 답안이 있으면 확인. 탭을 통째로 닫는 경우는 beforeunload가 맡는다
    if (draftCount > 0 && !result) {
      const ok = window.confirm(
        `아직 제출하지 않은 답이 ${draftCount}개 있어요.\n지금 나가면 반영되지 않습니다. 나갈까요?`,
      )
      if (!ok) e.preventDefault()
    }
  }

  const picked = card ? answers[card.cardId] : undefined

  /**
   * 보냈는데 결과를 모르는 상태. 이때는 학습 화면을 감추고 재시도만 시킨다.
   *
   * <p>예전엔 '초안 cardId 중 큐에 없는 게 있나'로 추정했는데, 덱 학습은 큐가 항상 전 카드라
   * 감지되지 않았다. payload를 동결하면서 추정이 필요 없어졌다 — attempted가 있다는 사실 자체가 신호다.</p>
   */
  const pendingRetry = attempted !== null && !result
  const attemptedCount = attempted ? Object.keys(attempted).length : 0

  // ── 키보드 단축키 (9/17): Space 뒤집기 · A 몰라요 · D 알아요 · S 별표 · ← → 이동 ──
  // e.code를 쓴다 — 한글 IME면 e.key가 'ㅁ'·'ㅇ'으로 오지만 code는 KeyA·KeyD 그대로다 (Quizlet과 같은 배치).
  // 등록을 매 렌더마다 갈아끼우는 건 Quiz.tsx와 같은 방식 — 최신 상태를 클로저로 잡기 위해
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (!card || result || reviewing || pendingRetry || flying.current) return
      if (e.repeat || e.ctrlKey || e.metaKey || e.altKey) return   // 꾹 누름·조합키는 무시
      const tag = (e.target as HTMLElement | null)?.tagName
      if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return
      // Space·Enter가 버튼·링크 위에 있으면 브라우저의 클릭 활성화에 맡긴다 — 우리까지 처리하면 두 번 실행된다.
      // 카드 자체도 button이라, 카드에 포커스가 있을 때의 Space 뒤집기는 카드의 onClick이 담당한다
      // 실제 키보드는 code가 항상 채워져 온다. 일부 자동화·가상 키보드는 비워 보내므로 key로 보정 (9/17 실측)
      const code = e.code || ({ ' ': 'Space', a: 'KeyA', d: 'KeyD', s: 'KeyS' } as Record<string, string>)[e.key.toLowerCase()] || e.key
      if ((code === 'Space' || code === 'Enter') && (tag === 'BUTTON' || tag === 'A')) return
      switch (code) {
        case 'Space': e.preventDefault(); flip(); break   // preventDefault: 페이지 스크롤 방지
        case 'KeyA': pick(false); break
        case 'KeyD': pick(true); break
        case 'KeyS': void toggleStar(); break
        case 'ArrowLeft': if (idx > 0) goPrev(); break
        case 'ArrowRight': goNext(); break
        default: return
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  })

  return (
    <>
      <TopNav />
      <div className="shell study-shell">
        {error && <p className="error" role="alert">{error}</p>}
        {/* 몰입 모드(사이드바 없음)에서 최초 로딩이 실패하면 갇힌다 — 탈출·재시도 제공 (Codex UI 검산) */}
        {error && queue === null && (
          <p style={{ display: 'flex', gap: 14, alignItems: 'center' }}>
            <button className="btn-primary" onClick={loadQueue}>다시 시도</button>
            <Link to={backTo} className="hero-secondary">← 돌아가기</Link>
          </p>
        )}

        {queue === null && !error && <p className="muted">불러오는 중...</p>}

        {/* ── 미제출 초안 복구 ── */}
        {pendingRetry && (
          <div className="result-panel">
            <h2>{retryBlocked ? '이 답안은 보낼 수 없어요' : '보낸 결과를 확인하지 못했어요'}</h2>
            <p className="result-line">
              <b>{attemptedCount}장</b>을 보냈지만 서버에 반영됐는지 확인하지 못했어요.
            </p>
            <p className="muted" style={{ fontSize: 13.5 }}>
              {retryBlocked
                ? '저장된 답안이 서버 기록과 맞지 않아요. 이 세션은 버리고 새로 시작해야 합니다.'
                : '보낸 그대로 다시 보냅니다. 이미 반영됐다면 두 번 세어지지 않고, 아직이면 이번에 반영돼요. 확인될 때까지 답은 고칠 수 없어요.'}
            </p>
            <div className="answer-buttons" style={{ marginTop: 26 }}>
              <button className="answer-no" disabled={submitting} onClick={discardDraft}>
                버리기
              </button>
              {!retryBlocked && (
                <button className="answer-yes" disabled={submitting} onClick={submit}>
                  {submitting ? '보내는 중...' : '다시 보내기'}
                </button>
              )}
            </div>
          </div>
        )}

        {queue !== null && total === 0 && !pendingRetry && !result && (
          <div className="stub">
            <h2>{deckId ? (starredOnly ? '★ 표시한 카드가 없어요' : '이 덱에는 카드가 없어요') : '지금 복습할 카드가 없어요 🎉'}</h2>
            <p>
              <Link to={backTo} className="link" style={{ color: 'var(--a)' }}>← 돌아가기</Link>
            </p>
          </div>
        )}

        {/* ── 학습 중 (복구가 필요한 상태면 그것부터 처리시킨다) ── */}
        {!result && !reviewing && !pendingRetry && card && (
          <>
            <div className="study-top">
              <Link to={backTo} className="hero-secondary" onClick={confirmQuit}>← 그만하기</Link>
              <span className="muted" style={{ fontSize: 13.5 }}>
                {idx + 1} / {total} · 답함 {answeredInQueue}
              </span>
            </div>
            {/* 막대 = 지금 위치(idx+1 / total). 답해서 넘어가든 화살표로 넘어가든 오르고, 이전으로 가면 준다.
                답한 수는 위 '답함 N'과 제출 전 검토 화면이 보여준다 — 아는 카드를 답 없이 넘기면
                답 기준 막대는 멈춰 보였다 (2026-09-15) */}
            <div
              className="progress-track"
              role="progressbar"
              aria-valuemin={0}
              aria-valuemax={total}
              aria-valuenow={idx + 1}
              aria-valuetext={`${total}장 중 ${idx + 1}번째, ${answeredInQueue}장 답함`}
            >
              <div className="progress-fill" style={{ width: `${((idx + 1) / total) * 100}%` }} />
            </div>

            {/* 카드는 앞↔뒤 토글. 뜻을 본 뒤 다시 앞면으로 돌려 스스로 떠올려 볼 수 있어야 한다 */}
            <button
              key={card.cardId}
              ref={cardRef}
              className={`study-card${dragging ? ' dragging' : ''}${flyingCls ? ' flying' : ''}`}
              style={dragX ? { transform: `translateX(${dragX}px) rotate(${dragX / 22}deg)` } : undefined}
              onClick={(e) => {
                // 밀었다 놓은 뒤에 따라오는 click은 뒤집기가 아니다
                if (suppressClick.current) { suppressClick.current = false; e.preventDefault(); return }
                flip()
              }}
              onPointerDown={onCardPointerDown}
              onPointerMove={onCardPointerMove}
              onPointerUp={(e) => onCardPointerEnd(e, true)}
              onPointerCancel={(e) => onCardPointerEnd(e, false)}
            >
              {/* 스와이프 중 방향 표시 — 민 거리에 비례해 진해진다 */}
              <span className="swipe-tag yes" aria-hidden="true" style={{ opacity: Math.min(1, Math.max(0, dragX) / SWIPE_COMMIT) }}>알아요</span>
              <span className="swipe-tag no" aria-hidden="true" style={{ opacity: Math.min(1, Math.max(0, -dragX) / SWIPE_COMMIT) }}>몰라요</span>
              {/* 읽기는 답의 절반(한자→읽기 회상 훈련) — 뜻 확인 후에만 공개. 읽기 없는 카드(영어 등)는 표시 없음 */}
              {revealed && card.reading && <span className="reading">{card.reading}</span>}
              <span className="study-word">{card.front}</span>
              {revealed ? (
                <span className="study-answer">{card.back}</span>
              ) : (
                <span className="study-hint">카드를 눌러 뜻 확인</span>
              )}
            </button>

            {/*
              🔊·⭐는 카드의 '형제'다 — 카드 안에 넣으면 button 중첩(HTML 위반) + Enter/Space가 뒤집기로 전파된다.
              예전엔 카드 우상단에 절대배치했는데 구석의 작은 표적이라 누르기 불편했다 (2026-08-31 사용자 지적).
              카드 아래 큰 버튼으로 내려 엄지가 닿는 자리에 둔다. 카드 탭은 뒤집기로 남긴다 —
              탭을 발음에 뺏기면 폰에서 카드를 뒤집을 동작이 사라진다.
            */}
            <div className="study-actions">
              {/*
                9/17: '발음 듣기'(수동 재생)를 자동 재생 스위치로. 뒤집을 때 읽어 주니 수동 버튼이 필요 없고,
                한 번 더 듣고 싶으면 카드를 다시 뒤집는다. 끄면 이 화면에서 소리가 전혀 안 난다 (설정 화면과 같은 값).
                TTS가 없는 브라우저면 스위치도 없다 — 예전 SpeakButton과 같은 처리
              */}
              {isTtsSupported() && (
                <button
                  type="button"
                  className={`study-action-btn sound-action${autoSpeak ? '' : ' off'}`}
                  onClick={toggleSound}
                  aria-pressed={autoSpeak}
                  title={autoSpeak ? '뒤집을 때 발음 자동 재생 — 누르면 끔' : '소리 꺼짐 — 누르면 켬'}
                >
                  <span aria-hidden="true">{autoSpeak ? '🔊' : '🔇'}</span>
                  <span>{autoSpeak ? '소리 켬' : '소리 끔'}</span>
                </button>
              )}
              <button
                type="button"
                className={`study-action-btn star-action${card.starred ? ' on' : ''}`}
                onClick={toggleStar}
                disabled={starring}
                aria-pressed={card.starred}
                title={card.starred ? '별표 해제' : '모르는 단어로 표시'}
              >
                <span aria-hidden="true">{card.starred ? '★' : '☆'}</span>
                <span>{card.starred ? '별표됨' : '별표'}</span>
              </button>
            </div>

            {/*
              뜻을 보지 않고도 답할 수 있다 (2026-09-01, 사용자 결정).
              원래는 revealed일 때만 눌리게 막혀 있었는데, 아는 단어마다 '뜻 확인' 탭이 하나씩
              더 붙는다. 복습 덱은 아는 카드가 대부분이라 그 한 탭이 매번 쌓인다.
              자기평가 학습이라 '떠올렸으면 바로 채점'이 자연스럽고, Quizlet도 앞면에서 바로 눌린다.
            */}
            <div className="answer-buttons">
              <button
                className={`answer-no${picked === false ? ' picked' : ''}`}
                aria-pressed={picked === false}
                onClick={() => pick(false)}
              >
                몰라요
              </button>
              <button
                className={`answer-yes${picked === true ? ' picked' : ''}`}
                aria-pressed={picked === true}
                onClick={() => pick(true)}
              >
                알아요
              </button>
            </div>
            {!revealed && (
              <p className="muted" style={{ textAlign: 'center', fontSize: 13.5, marginTop: 10 }}>
                떠올렸으면 바로 답해도 되고, 카드를 눌러 뜻을 확인해도 돼요
                <span className="only-touch"> · 옆으로 밀어서 답할 수도 있어요</span>
              </p>
            )}

            <div className="study-nav">
              <button className="nav-btn" onClick={goPrev} disabled={idx === 0}>← 이전</button>
              <span className="muted" style={{ fontSize: 12.5 }}>
                {picked === undefined ? '아직 답하지 않음' : picked ? '알아요로 표시함' : '몰라요로 표시함'}
              </span>
              <button className="nav-btn" onClick={goNext}>다음 →</button>
            </div>
            {/* 마우스·키보드 환경에서만 보인다 (CSS hover:hover) */}
            <p className="muted kbd-hint" aria-hidden="true">
              <kbd>Space</kbd> 뒤집기 · <kbd>A</kbd> 몰라요 · <kbd>D</kbd> 알아요 · <kbd>S</kbd> 별표 · <kbd>←</kbd> <kbd>→</kbd> 이동
            </p>
            <p className="muted study-foot">
              답은 아직 저장되지 않았어요. 되돌아가서 얼마든지 고칠 수 있고, 마지막에 한 번에 제출됩니다.
            </p>
          </>
        )}

        {/* ── 제출 전 검토 ── */}
        {!result && reviewing && !pendingRetry && queue !== null && total > 0 && (
          <div className="result-panel">
            <h2>제출할까요?</h2>
            <p className="result-line">
              {total}장 중 <b>{answeredInQueue}장</b> 답함
              {answeredInQueue < total && <> · 미응답 {total - answeredInQueue}장</>}
            </p>
            <p className="muted" style={{ fontSize: 13.5 }}>
              지금 제출하면 답한 카드만 Leitner 박스에 반영됩니다. 미응답 카드는 그대로 남아요.
            </p>
            <div className="answer-buttons" style={{ marginTop: 26 }}>
              <button className="answer-no" onClick={() => { setReviewing(false); setIdx(0) }}>
                돌아가서 고치기
              </button>
              <button className="answer-yes" disabled={submitting} onClick={submit}>
                {submitting ? '제출 중...' : '학습 완료'}
              </button>
            </div>
          </div>
        )}

        {/* ── 제출 완료 ── */}
        {result && (
          <div className="result-panel">
            <h2>복습 완료 🎉</h2>
            <p className="result-line">
              {result.total}장 중 <b>알아요 {result.known}</b> · <b>몰라요 {result.unknown}</b>
            </p>
            <p className="muted" style={{ fontSize: 13.5 }}>
              {result.alreadySubmitted
                ? '이미 제출된 세션이라 진행도는 다시 움직이지 않았어요.'
                : '알아요 카드는 다음 박스로 승급, 몰라요 카드는 박스 1로 — 10분 뒤 다시 만나요.'}
            </p>
            <div className="answer-buttons" style={{ marginTop: 26 }}>
              <Link to="/" className="answer-no" style={{ textDecoration: 'none', textAlign: 'center' }}>
                홈으로
              </Link>
              <button className="answer-yes" onClick={loadQueue}>
                한 번 더
              </button>
            </div>
          </div>
        )}
      </div>
    </>
  )
}
