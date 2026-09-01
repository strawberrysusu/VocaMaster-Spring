import { useCallback, useEffect, useRef, useState } from 'react'
import type { MouseEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import { fetchAllCards } from '../api/cards'
import { recordRecentStudy } from '../lib/recent'
import TopNav from '../components/TopNav'
import SpeakButton from '../components/SpeakButton'

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

  // 초안 키는 입구별로 분리 — 덱 학습과 전체 복습의 답안이 섞이면 안 된다
  const draftKey = `vm.study.draft.${deckId ?? 'due'}${starredOnly ? '.starred' : ''}`
  const draftLoaded = useRef(false)
  const cardRef = useRef<HTMLButtonElement>(null)
  // 카드 전환 잠금 — 알아요를 빠르게 두 번 누르면 setIdx가 두 번 돌아 카드를 건너뛰고,
  // idx가 total을 넘으면 어느 블록도 안 그려져 화면이 하얘진다 (Codex 검산 2026-09-01)
  const advancing = useRef(false)

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
    if (card) cardRef.current?.focus({ preventScroll: true })
  }, [idx, reviewing, card])

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
    setRevealed(false)
    if (idx + 1 >= total) setReviewing(true)   // 마지막 카드를 지나면 제출 전 검토
    else setIdx((i) => i + 1)
  }

  function goPrev() {
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
              <button className="answer-no" onClick={discardDraft}>
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
            <div
              className="progress-track"
              role="progressbar"
              aria-valuemin={0}
              aria-valuemax={total}
              aria-valuenow={answeredInQueue}
            >
              <div className="progress-fill" style={{ width: `${(answeredInQueue / total) * 100}%` }} />
            </div>

            {/* 카드는 앞↔뒤 토글. 뜻을 본 뒤 다시 앞면으로 돌려 스스로 떠올려 볼 수 있어야 한다 */}
            <button ref={cardRef} className="study-card" onClick={() => setRevealed((r) => !r)}>
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
              <SpeakButton
                text={card.reading || card.front} // 읽기가 있으면 읽기를 읽는다 — 한자 TTS 오독 방지
                size="lg"
                className="study-action-btn"
                label="다시 듣기"
              />
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
              </p>
            )}

            <div className="study-nav">
              <button className="nav-btn" onClick={goPrev} disabled={idx === 0}>← 이전</button>
              <span className="muted" style={{ fontSize: 12.5 }}>
                {picked === undefined ? '아직 답하지 않음' : picked ? '알아요로 표시함' : '몰라요로 표시함'}
              </span>
              <button className="nav-btn" onClick={goNext}>다음 →</button>
            </div>
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
