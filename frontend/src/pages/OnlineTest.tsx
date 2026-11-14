import { useEffect, useMemo, useRef, useState, type KeyboardEvent } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import { fetchAllCards, setStarred, type CardDto } from '../api/cards'
import TopNav from '../components/TopNav'
import { buildSheet, type SheetRow, type SheetScope } from '../lib/testSheet'
import { matchesAnswer, matchesReading } from '../lib/answerMatch'

// 온라인 시험지 (3단계, 11/14) — 종이 시험지와 같은 문항을 화면에서 풀고 한 번에 채점.
//  · 읽기(한자가 있는 단어만): 자동 채점, 가타카나로 써도 정답, 읽기가 두 가지면 하나만
//  · 뜻: 타이핑 모드와 같은 규칙(ADR-051) + 비슷하게 쓴 답은 '맞음으로'
//  · 채점하면 틀리거나 비워 둔 단어는 자동 ⭐ (사용자 결정). '맞음으로'를 누르면 이 시험이 켠 ⭐만 끈다
//  · 복습 일정(라이트너)은 건드리지 않는다. 답은 새로고침해도 남는다(sessionStorage, 채점하면 지움)

interface Answer {
  reading: string
  meaning: string
}

interface RowResult {
  /** null = 읽기는 묻지 않은 문항 */
  readingOk: boolean | null
  meaningOk: boolean
  /** '맞음으로' 눌러 정답 처리 */
  override: boolean
}

interface Draft {
  v: 1
  optsKey: string
  order: number[]
  answers: Answer[]
}

const rowOk = (r: RowResult) => r.override || ((r.readingOk ?? true) && r.meaningOk)
const askReading = (r: SheetRow) => r.needsReading && r.reading !== ''
const emptyAnswer = (): Answer => ({ reading: '', meaning: '' })
const withIds = (prev: Set<number>, ids: number[]) => (ids.length ? new Set([...prev, ...ids]) : prev)
const withoutId = (prev: Set<number>, id: number) => {
  const next = new Set(prev)
  next.delete(id)
  return next
}

function readDraft(key: string): Draft | null {
  try {
    const d = JSON.parse(sessionStorage.getItem(key) ?? 'null')
    return d && d.v === 1 && Array.isArray(d.order) && Array.isArray(d.answers) ? (d as Draft) : null
  } catch {
    return null
  }
}

export default function OnlineTest() {
  const { deckId } = useParams()
  const [params] = useSearchParams()
  const scope: SheetScope = params.get('scope') === 'starred' ? 'starred' : 'all'
  const countParam = params.get('count')
  const count: number | 'all' = countParam && /^\d+$/.test(countParam) ? Number(countParam) : 'all'
  const shuffle = params.get('shuffle') !== '0'
  const optsKey = `${scope}|${count}|${shuffle ? 1 : 0}`
  const draftKey = `vm.onlineTest.${deckId}`

  const [title, setTitle] = useState('')
  const [rows, setRows] = useState<SheetRow[]>([])
  const [japanese, setJapanese] = useState(false)
  const [answers, setAnswers] = useState<Answer[]>([])
  const [results, setResults] = useState<RowResult[] | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  // 별표: 이 시험이 켰거나 켜려고 보낸 카드 — '맞음으로'는 이 카드의 ⭐만 끈다.
  // 시험을 열 때 이미 ⭐였던 카드(starredAtLoad)는 넣지 않으니 '맞음으로'를 눌러도 그대로 남는다.
  // 요청을 보내기 '전에' 넣는다: 서버엔 저장됐는데 응답만 끊기면, 다시 시도의 changedIds가 비어
  // 이 시험이 켠 ⭐인지 알 길이 없어진다 (Codex 검산 11/14). 실제로 안 켜졌다면 끄기 요청은 아무것도 안 바꾼다(멱등)
  const [addedStars, setAddedStars] = useState<Set<number>>(new Set())
  const starredAtLoad = useRef<Set<number>>(new Set())
  const [starState, setStarState] = useState<'idle' | 'saving' | 'done' | 'error'>('idle')
  // '맞음으로'·'다시 오답으로' 요청 중인 카드 — 이 동안은 '다시 풀기'를 막는다
  const [busyCards, setBusyCards] = useState<Set<number>>(new Set())
  // 몇 번째 풀이인지 — 문항이 바뀐 뒤(다시 풀기·다른 덱) 늦게 온 응답이 새 결과를 건드리지 않게
  const attemptRef = useRef(0)
  const sheetRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    Promise.all([api<{ title: string }>(`/decks/${deckId}`), fetchAllCards(deckId!)])
      .then(([deck, { cards }]) => {
        if (cancelled) return
        setTitle(deck.title)
        starredAtLoad.current = new Set(cards.filter((c) => c.starred).map((c) => c.id))
        attemptRef.current += 1
        setAddedStars(new Set())
        setStarState('idle')
        let built: { rows: SheetRow[]; japanese: boolean } | null = null
        let restored: Answer[] | null = null
        const draft = readDraft(draftKey)
        if (draft && draft.optsKey === optsKey) {
          const byId = new Map<number, CardDto>(cards.map((c) => [c.id, c]))
          const ordered = draft.order.map((id) => byId.get(id)).filter((c): c is CardDto => !!c)
          if (ordered.length === draft.order.length && draft.answers.length === ordered.length) {
            built = buildSheet(ordered, { scope: 'all', count: 'all', shuffle: false })
            restored = draft.answers
          }
        }
        if (!built) built = buildSheet(cards, { scope, count, shuffle })
        setRows(built.rows)
        setJapanese(built.japanese)
        setAnswers(restored ?? built.rows.map(emptyAnswer))
        setResults(null)
      })
      .catch((e) => !cancelled && setError((e as Error).message))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [deckId, draftKey, optsKey, scope, count, shuffle])

  // 채점 전 답안 임시 저장 — 새로고침·실수로 나가도 남는다
  useEffect(() => {
    if (results || rows.length === 0) return
    try {
      const draft: Draft = { v: 1, optsKey, order: rows.map((r) => r.cardId), answers }
      sessionStorage.setItem(draftKey, JSON.stringify(draft))
    } catch {
      /* 저장 공간이 막혀도 시험은 계속 */
    }
  }, [answers, rows, results, optsKey, draftKey])

  const blanks = useMemo(
    () => rows.reduce((n, r, i) => n + ((askReading(r) && !answers[i]?.reading.trim()) || !answers[i]?.meaning.trim() ? 1 : 0), 0),
    [rows, answers],
  )
  const score = results ? results.filter(rowOk).length : 0
  const wrongCount = results ? results.length - score : 0

  function update(i: number, field: keyof Answer, value: string) {
    setAnswers((prev) => prev.map((a, j) => (j === i ? { ...a, [field]: value } : a)))
  }

  // Enter = 다음 칸 (일본어 입력기 변환 중의 Enter는 건드리지 않는다)
  function onEnter(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key !== 'Enter' || e.nativeEvent.isComposing) return
    e.preventDefault()
    const inputs = Array.from(sheetRef.current?.querySelectorAll<HTMLInputElement>('input.otest-input') ?? [])
    const next = inputs[inputs.indexOf(e.currentTarget) + 1]
    next?.focus()
  }

  async function starWrong(res: RowResult[]) {
    const attempt = attemptRef.current
    const wrongIds = rows.filter((_, i) => !rowOk(res[i])).map((r) => r.cardId)
    if (wrongIds.length === 0) {
      setStarState('done')
      return
    }
    setAddedStars((prev) => withIds(prev, wrongIds.filter((id) => !starredAtLoad.current.has(id))))
    setStarState('saving')
    try {
      // 500장씩 나눠 보낸다 (api/cards.ts) — '전체' 시험은 오답이 500개를 넘을 수 있다
      const r = await setStarred(deckId!, wrongIds, true)
      // 시험을 연 뒤 다른 곳에서 꺼진 ⭐를 다시 켠 경우까지 — changedIds는 서버가 실제로 바꾼 카드
      setAddedStars((prev) => withIds(prev, r.changedIds))
      if (attemptRef.current === attempt) setStarState('done')
    } catch (e) {
      if (attemptRef.current !== attempt) return
      setStarState('error')
      setError(`틀린 단어 별표를 못 했어요 — ${(e as Error).message}`)
    }
  }

  function grade() {
    const res: RowResult[] = rows.map((r, i) => ({
      readingOk: askReading(r) ? matchesReading(answers[i].reading, r.reading) : null,
      meaningOk: matchesAnswer(answers[i].meaning, r.meaning),
      override: false,
    }))
    setResults(res)
    setError('')
    try {
      sessionStorage.removeItem(draftKey)
    } catch {
      /* 무시 */
    }
    void starWrong(res)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  // '맞음으로' ↔ '다시 오답으로' — 이 시험이 켠 ⭐만 끄고, 되돌리면 다시 켠다
  async function toggleOverride(i: number) {
    if (!results) return
    const attempt = attemptRef.current
    const cardId = rows[i].cardId
    const turningOn = !results[i].override
    setBusyCards((prev) => withIds(prev, [cardId]))
    setError('')
    try {
      if (turningOn) {
        if (addedStars.has(cardId)) {
          await setStarred(deckId!, [cardId], false)
          setAddedStars((prev) => withoutId(prev, cardId))
        }
      } else {
        if (!starredAtLoad.current.has(cardId)) setAddedStars((prev) => withIds(prev, [cardId]))
        const r = await setStarred(deckId!, [cardId], true)
        setAddedStars((prev) => withIds(prev, r.changedIds))
      }
      // 그사이 문항이 바뀌었으면(다른 덱 등) i번은 다른 단어다 — 결과 표시는 건드리지 않는다
      if (attemptRef.current !== attempt) return
      setResults((prev) => prev && prev.map((r, j) => (j === i ? { ...r, override: turningOn } : r)))
    } catch (e) {
      if (attemptRef.current === attempt) setError(`별표를 바꾸지 못했어요 — ${(e as Error).message}`)
    } finally {
      setBusyCards((prev) => withoutId(prev, cardId))
    }
  }

  // 별표 저장이 끝나기 전에는 다시 풀 수 없다 — 늦게 온 응답이 새 문항(같은 번호의 다른 단어)을 바꾸던 문제 (Codex 검산 11/14)
  const starBusy = starState === 'saving' || busyCards.size > 0

  function retryWrong() {
    if (!results || starBusy) return
    attemptRef.current += 1
    const wrongRows = rows.filter((_, i) => !rowOk(results[i])).map((r, i) => ({ ...r, no: i + 1 }))
    setRows(wrongRows)
    setAnswers(wrongRows.map(emptyAnswer))
    setResults(null)
    setStarState('idle')
    window.scrollTo({ top: 0 })
  }

  const scopeLabel = scope === 'starred' ? '⭐ 별표만' : '전체'

  return (
    <>
      <TopNav />
      <div className="shell">
        <p style={{ margin: '0 0 14px' }}>
          <Link to={`/decks/${deckId}`} className="hero-secondary">← 덱으로</Link>
        </p>
        <div className="page-head">
          <div>
            <h1>📝 온라인 시험{title ? ` — ${title}` : ''}</h1>
            <p className="sub">
              {scopeLabel} · {rows.length}문항 · 틀리거나 비워 둔 단어는 채점하면 자동으로 ⭐ 돼요 (복습 일정은 그대로)
            </p>
          </div>
        </div>

        {error && <p className="error" role="alert">{error}</p>}
        {loading && <p className="muted">문제 만드는 중...</p>}
        {!loading && rows.length === 0 && !error && <p className="muted">문항이 없어요 — 덱으로 돌아가 범위를 바꿔 보세요.</p>}

        {results && (
          <div className="otest-summary" role="status">
            <p className="result-line" style={{ margin: 0 }}>
              점수 <b>{score}/{rows.length}</b>
              {wrongCount > 0 && (
                <span className="otest-star-note">
                  {starState === 'saving' && ' · 틀린 단어 ⭐ 하는 중...'}
                  {starState === 'done' && ` · 틀린 ${wrongCount}개 ⭐ 완료 (새로 켠 별표 ${addedStars.size}개)`}
                  {starState === 'error' && (
                    <>
                      {' · '}
                      <button className="btn-ghost-link" onClick={() => void starWrong(results)}>별표 다시 시도</button>
                    </>
                  )}
                </span>
              )}
            </p>
          </div>
        )}

        {rows.length > 0 && (
          <div className={`otest-sheet${japanese ? ' jp' : ''}`} ref={sheetRef}>
            <div className="otest-head" aria-hidden="true">
              <span>번호</span>
              <span>단어</span>
              {japanese && <span>읽기</span>}
              <span>뜻</span>
            </div>
            {rows.map((r, i) => {
              const res = results?.[i]
              const ok = res ? rowOk(res) : null
              return (
                <div key={r.cardId} className={`otest-row${ok === true ? ' ok' : ok === false ? ' wrong' : ''}`}>
                  <span className="otest-no">{r.no}</span>
                  <span className={`otest-word${japanese ? ' jp' : ''}`}>{r.word}</span>
                  {japanese &&
                    (askReading(r) ? (
                      <input
                        className={`otest-input${res && res.readingOk === false && !res.override ? ' bad' : ''}`}
                        aria-label={`${r.no}번 읽기`}
                        placeholder="읽기"
                        lang="ja"
                        value={answers[i]?.reading ?? ''}
                        disabled={!!results}
                        onChange={(e) => update(i, 'reading', e.target.value)}
                        onKeyDown={onEnter}
                      />
                    ) : (
                      <span className="otest-noread" title="한자가 없는 단어는 읽기를 쓰지 않아요">—</span>
                    ))}
                  <input
                    className={`otest-input${res && !res.meaningOk && !res.override ? ' bad' : ''}`}
                    aria-label={`${r.no}번 뜻`}
                    placeholder="뜻"
                    value={answers[i]?.meaning ?? ''}
                    disabled={!!results}
                    onChange={(e) => update(i, 'meaning', e.target.value)}
                    onKeyDown={onEnter}
                  />
                  {res && (
                    <div className="otest-answer">
                      <span className="otest-mark">{ok ? '✅' : '❌'}</span>
                      <span className="otest-correct">
                        {askReading(r) && <span lang="ja">{r.reading} · </span>}
                        {r.meaning}
                        {r.memo && <span className="otest-memo"> — {r.memo}</span>}
                      </span>
                      {(!((res.readingOk ?? true) && res.meaningOk) || res.override) && (
                        <button className="btn-ghost-link otest-override" disabled={busyCards.has(r.cardId) || starState === 'saving'} onClick={() => void toggleOverride(i)}>
                          {res.override ? '다시 오답으로' : '맞음으로'}
                        </button>
                      )}
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        )}

        {rows.length > 0 && (
          <div className={`answer-buttons${!results || wrongCount === 0 ? ' single' : ''}`} style={{ marginTop: 20 }}>
            {results ? (
              <>
                {wrongCount > 0 && (
                  <button
                    className="answer-no"
                    disabled={starBusy}
                    title={starBusy ? '별표 저장이 끝나면 누를 수 있어요' : undefined}
                    onClick={retryWrong}
                  >
                    틀린 {wrongCount}개만 다시 풀기
                  </button>
                )}
                <Link to={`/study?deckId=${deckId}&starredOnly=1`} className="answer-yes" style={{ textDecoration: 'none', textAlign: 'center' }}>
                  ⭐ 별표 복습 하러 가기
                </Link>
              </>
            ) : (
              <button className="answer-yes" onClick={grade}>
                채점하기{blanks > 0 ? ` (빈칸 ${blanks}문항은 모름으로)` : ''}
              </button>
            )}
          </div>
        )}
      </div>
    </>
  )
}
