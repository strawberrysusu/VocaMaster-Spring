import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import TopNav from '../components/TopNav'

// 백엔드(Phase 1 ImportService) 계약 — 구분자 자동 감지(탭·|·:·,·-), 2칸(단어|뜻) / 3칸(단어|읽기|뜻), 1000줄, 중복 스킵
interface PreviewResp {
  cards: { front: string; back: string; reading?: string }[]
  failed: { line: number; content: string }[]
  totalParsed: number
  failedCount: number
}

interface ImportResp {
  imported: number
  skipped: number
  failed: { line: number; content: string }[]
  failedCount: number
}

const SEPARATORS: { label: string; value: string }[] = [
  { label: '자동 감지', value: '' },
  { label: '탭 (엑셀·시트 복사)', value: '\t' },
  { label: '쉼표 ,', value: ',' },
  { label: '세로막대 |', value: '|' },
  { label: '콜론 :', value: ':' },
  { label: '하이픈 -', value: '-' },
]

const SAMPLE = `会議 | かいぎ | 회의
実際 | じっさい | 실제
meticulous | very careful`

export default function ImportCards() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [text, setText] = useState('')
  const [separator, setSeparator] = useState('')
  const [preview, setPreview] = useState<PreviewResp | null>(null)
  const [result, setResult] = useState<ImportResp | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function doPreview() {
    if (busy || !text.trim()) return
    setBusy(true)
    setError('')
    setResult(null)
    try {
      setPreview(await api<PreviewResp>('/import/preview', { method: 'POST', body: JSON.stringify({ text, separator }) }))
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  async function doImport() {
    if (busy || !preview || preview.cards.length === 0) return
    setBusy(true)
    setError('')
    try {
      setResult(await api<ImportResp>(`/decks/${id}/import`, { method: 'POST', body: JSON.stringify({ text, separator }) }))
      setPreview(null)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  const lineCount = text.split('\n').filter((l) => l.trim()).length

  return (
    <>
      <TopNav />
      <div className="shell">
        <p style={{ margin: '0 0 14px' }}>
          <Link to={`/decks/${id}`} className="hero-secondary">← 덱으로</Link>
        </p>
        <div className="page-head">
          <div>
            <h1>일괄 가져오기</h1>
            <p className="sub">한 줄에 카드 하나 · 단어 | 뜻 또는 단어 | 읽기 | 뜻 · 최대 1000줄 · 이미 있는 단어는 건너뜀</p>
          </div>
        </div>

        {error && <p className="error" role="alert">{error}</p>}

        {result ? (
          <div className="result-panel">
            <h2>가져오기 완료 🎉</h2>
            <p className="result-line">
              <b>{result.imported}장 등록</b>
              {result.skipped > 0 && <> · 중복 {result.skipped}장 건너뜀</>}
              {result.failedCount > 0 && <> · 실패 {result.failedCount}줄</>}
            </p>
            {result.failedCount > 0 && (
              <div className="word-list" style={{ marginTop: 14, textAlign: 'left' }}>
                {result.failed.map((f) => (
                  <div key={f.line} className="word-row">
                    <span className="word-idx">{f.line}</span>
                    <span className="word-back">{f.content}</span>
                  </div>
                ))}
              </div>
            )}
            <div className="answer-buttons" style={{ marginTop: 26 }}>
              <button className="answer-no" onClick={() => { setResult(null); setText('') }}>더 가져오기</button>
              <button className="answer-yes" onClick={() => navigate(`/decks/${id}`)}>덱 보기</button>
            </div>
          </div>
        ) : (
          <div className="quiz-setup">
            <textarea
              className="import-text"
              value={text}
              onChange={(e) => { setText(e.target.value); setPreview(null) }}
              placeholder={SAMPLE}
              rows={12}
              spellCheck={false}
            />
            <div className="setup-row">
              <span className="setup-label">구분자</span>
              <div className="sort-tabs">
                {SEPARATORS.map((s) => (
                  <button key={s.label} className={separator === s.value ? 'active' : ''} disabled={busy} onClick={() => { setSeparator(s.value); setPreview(null) }}>
                    {s.label}
                  </button>
                ))}
              </div>
            </div>
            <p className="muted" style={{ margin: 0, fontSize: 12.5 }}>
              팁: 엑셀·구글시트에서 두(세) 열을 복사해 붙여넣으면 탭으로 구분돼요. 스페이스는 뜻 안에도 들어가서 구분자로 쓰지 않아요.
            </p>
            <div className="answer-buttons">
              <button className="answer-no" disabled={busy || !text.trim()} onClick={doPreview}>
                미리보기 ({lineCount}줄)
              </button>
              <button className="answer-yes" disabled={busy || !preview || preview.cards.length === 0} onClick={doImport}>
                {preview ? `${preview.cards.length}장 등록` : '먼저 미리보기'}
              </button>
            </div>
          </div>
        )}

        {preview && !result && (
          <div style={{ marginTop: 18 }}>
            <p className="muted" style={{ fontSize: 13.5 }}>
              파싱 {preview.totalParsed}장{preview.failedCount > 0 && <> · <span style={{ color: '#b0485c' }}>실패 {preview.failedCount}줄</span></>}
            </p>
            <div className="word-list">
              {preview.cards.map((c, i) => (
                <div key={i} className="word-row">
                  <span className="word-idx">{i + 1}</span>
                  <span className="word-front">{c.reading && <span className="reading-inline">{c.reading}</span>}{c.front}</span>
                  <span className="word-back">{c.back}</span>
                </div>
              ))}
              {preview.failed.map((f) => (
                <div key={`f${f.line}`} className="word-row" style={{ background: '#fdf1f4' }}>
                  <span className="word-idx" style={{ color: '#b0485c' }}>{f.line}</span>
                  <span className="word-back" style={{ color: '#b0485c' }}>구분 실패: {f.content}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </>
  )
}
