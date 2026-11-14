import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import type { CardDto } from '../api/cards'
import { buildSheet, estimatePages, poolSize, sheetFileName, type SheetScope } from '../lib/testSheet'
import { buildTestSheetDocx, DOCX_MIME } from '../lib/docx'

// 덱 화면 "시험지" — 같은 문항을 종이(Word 내려받기, 2단계) 또는 온라인(3단계)으로 푼다 (11/14).
// Word는 서버를 거치지 않는다: 덱 화면이 이미 불러온 카드로 브라우저에서 .docx를 만든다. 종이는 채점·별표를 직접.
// 온라인은 /test/:deckId — 채점하면 틀린 단어가 자동 ⭐.

const COUNT_CHOICES = [10, 20, 30, 50, 100]

interface Props {
  deckId: string | number
  deckTitle: string
  cards: CardDto[]
  onClose: () => void
}

export default function TestSheetDialog({ deckId, deckTitle, cards, onClose }: Props) {
  const navigate = useNavigate()
  const starredN = poolSize(cards, 'starred')
  const [scope, setScope] = useState<SheetScope>('all')
  const [count, setCount] = useState<number | 'all'>('all')
  const [shuffle, setShuffle] = useState(true)
  const [answerKey, setAnswerKey] = useState(true)

  const pool = poolSize(cards, scope)
  const n = count === 'all' ? pool : Math.min(count, pool)
  const pages = estimatePages(n, answerKey)
  const japanese = useMemo(() => buildSheet(cards, { scope, count: 'all', shuffle: false }).japanese, [cards, scope])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  function download() {
    const model = buildSheet(cards, { scope, count, shuffle })
    if (model.rows.length === 0) return
    const subtitle = [scope === 'starred' ? '별표만' : '전체', `${model.rows.length}문항`, shuffle ? '순서 섞음' : '덱 순서'].join(' · ')
    const bytes = buildTestSheetDocx(model, { deckTitle, subtitle, answerKey })
    const url = URL.createObjectURL(new Blob([bytes], { type: DOCX_MIME }))
    const a = document.createElement('a')
    a.href = url
    a.download = sheetFileName(deckTitle)
    document.body.appendChild(a)
    a.click()
    a.remove()
    // 다운로드가 시작될 시간을 두고 해제 (바로 해제하면 일부 브라우저에서 빈 파일)
    setTimeout(() => URL.revokeObjectURL(url), 2000)
    onClose()
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-panel sheet-panel" role="dialog" aria-modal="true" aria-label="시험지 만들기" onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <h2>📝 시험지 만들기</h2>
          <button className="modal-close" aria-label="닫기" onClick={onClose}>✕</button>
        </div>

        <p className="muted sheet-lead">
          종이: Word로 받아 인쇄하고 손으로 쓴 뒤 맨 뒤 정답지로 채점해요.
          <br />
          온라인: 바로 풀고 채점하면 틀린 단어가 자동으로 ⭐ 돼요.
        </p>

        <div className="sheet-form">
          <div className="sheet-field">
            <span className="sheet-label">범위</span>
            <label className="sheet-choice">
              <input type="radio" name="sheet-scope" checked={scope === 'all'} onChange={() => { setScope('all'); setCount('all') }} />
              전체 {cards.length}장
            </label>
            <label className={`sheet-choice${starredN === 0 ? ' off' : ''}`} title={starredN === 0 ? '별표한 카드가 없어요' : undefined}>
              <input type="radio" name="sheet-scope" checked={scope === 'starred'} disabled={starredN === 0} onChange={() => { setScope('starred'); setCount('all') }} />
              ⭐ 별표만 {starredN}장
            </label>
          </div>

          <label className="sheet-field">
            <span className="sheet-label">문제 수</span>
            <select
              className="sheet-select"
              value={count === 'all' ? 'all' : String(count)}
              onChange={(e) => setCount(e.target.value === 'all' ? 'all' : Number(e.target.value))}
            >
              <option value="all">전부 ({pool}문항)</option>
              {COUNT_CHOICES.filter((c) => c < pool).map((c) => (
                <option key={c} value={c}>{c}문항</option>
              ))}
            </select>
          </label>

          <label className="sheet-field sheet-check">
            <input type="checkbox" checked={shuffle} onChange={(e) => setShuffle(e.target.checked)} />
            순서 섞기
          </label>
          <label className="sheet-field sheet-check">
            <input type="checkbox" checked={answerKey} onChange={(e) => setAnswerKey(e.target.checked)} />
            맨 뒤에 정답지 붙이기 (Word)
          </label>
        </div>

        <p className="muted sheet-note">
          {japanese
            ? '칸: 번호 · 단어 · 읽기 · 뜻 — 한자가 없는 단어는 읽기 칸을 회색으로 막아 둬요'
            : '칸: 번호 · 단어 · 뜻'}
        </p>

        <div className="modal-foot">
          <span className="muted">
            {n > 0 ? `A4 약 ${pages.sheet}쪽${answerKey ? ` + 정답지 ${pages.answerKey}쪽` : ''}` : '문항이 없어요'}
          </span>
          <span className="sheet-actions">
            <button
              className="btn-ghost-link"
              disabled={n === 0}
              onClick={() => navigate(`/test/${deckId}?scope=${scope}&count=${count}&shuffle=${shuffle ? 1 : 0}`)}
            >
              온라인으로 풀기
            </button>
            <button className="btn-primary" disabled={n === 0} onClick={download}>
              Word 파일 받기
            </button>
          </span>
        </div>
      </div>
    </div>
  )
}
