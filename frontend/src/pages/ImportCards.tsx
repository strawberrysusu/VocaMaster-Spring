import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import TopNav from '../components/TopNav'
import Ruby from '../components/Ruby'

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

  // 미리보기한 '그 입력'을 스냅샷으로 고정 — 요청 중 입력을 바꾸면 A를 확인하고 B를 등록하는 사고 (Codex 감사)
  const [snapshot, setSnapshot] = useState<{ text: string; separator: string } | null>(null)

  async function doPreview() {
    if (busy || !text.trim()) return
    setBusy(true)
    setError('')
    setResult(null)
    const snap = { text, separator }
    try {
      const res = await api<PreviewResp>('/import/preview', { method: 'POST', body: JSON.stringify(snap) })
      setSnapshot(snap)
      setPreview(res)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  const previewStale = !!snapshot && (snapshot.text !== text || snapshot.separator !== separator)

  async function doImport() {
    if (busy || !preview || !snapshot || previewStale || preview.cards.length === 0) return
    setBusy(true)
    setError('')
    try {
      setResult(await api<ImportResp>(`/decks/${id}/import`, { method: 'POST', body: JSON.stringify(snapshot) }))   // 스냅샷 그대로
      setPreview(null)
      setSnapshot(null)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  const lineCount = text.split('\n').filter((l) => l.trim()).length
  // 등록 버튼은 위(입력 옆)·아래(미리보기 목록 끝) 두 곳 — 조건·라벨은 한 곳에서
  const importDisabled = busy || !preview || previewStale || preview.cards.length === 0
  const importLabel = preview && !previewStale ? `2. ${preview.cards.length}장 실제 등록` : '2. 실제 등록 (먼저 미리보기)'

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
                1. 미리보기 ({lineCount}줄)
              </button>
              <button className="answer-yes" disabled={importDisabled} onClick={doImport}>
                {importLabel}
              </button>
            </div>
          </div>
        )}

        {preview && !result && (
          <div style={{ marginTop: 18 }}>
            {/* 9/3: 미리보기 목록이 덱 카드 목록과 같은 모양이라 '저장됐다'로 오해한 사례(덱 172, 등록 요청 0회).
                목록 위에 '아직 저장 안 됨'을 명시하고, 긴 목록 아래에도 같은 등록 버튼을 둔다. 저장 조건·스냅샷 로직은 그대로 */}
            <p className="preview-notice" role="status">
              미리보기예요 — <b>아직 저장되지 않았습니다.</b> 「2. 실제 등록」을 눌러야 덱에 들어갑니다.
            </p>
            <p className="muted" style={{ fontSize: 13.5 }}>
              파싱 {preview.totalParsed}장{preview.failedCount > 0 && <> · <span style={{ color: '#b0485c' }}>실패 {preview.failedCount}줄</span></>}
            </p>
            <div className="word-list">
              {preview.cards.map((c, i) => (
                <div key={i} className="word-row">
                  <span className="word-idx">{i + 1}</span>
                  <span className="word-front"><Ruby front={c.front} reading={c.reading} /></span>
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
            <div className="answer-buttons" style={{ marginTop: 14 }}>
              <button className="answer-yes" disabled={importDisabled} onClick={doImport}>
                {importLabel}
              </button>
            </div>
          </div>
        )}
      </div>
    </>
  )
}
