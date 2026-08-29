import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import TopNav from '../components/TopNav'

interface FailedLine {
  line: number
  content: string
}

interface FileResult {
  name: string
  deckId?: number
  imported?: number
  skipped?: number
  failedCount?: number
  failed?: FailedLine[]
  error?: string
}

// 실물 파일 세 형식을 그대로 받기 위한 전처리 (백로그 ㉒ — 사용자 vocajapanese 폴더 실측):
// ① 파일 첫머리 BOM 제거 + CRLF(\r\n) 개행 — \r이 줄 끝에 남으면 JS 정규식의 `.`이
//    \r을 매치하지 않아 아래 규칙이 전멸한다 (8/29 실측: day07+ 8천 장 오염의 근본 원인)
// ② day01형: 탭 3칸 `単語 \t （よみ） \t 뜻` → 읽기 양끝 전각 괄호（）만 벗김
// ③ day02형: `単語（よみ）, 뜻` (탭 없음) → `単語 \t よみ \t 뜻` 3칸으로 재구성
//    — 뜻 안의 반각 괄호("경탄(놀라며 감탄함)")는 건드리지 않는다
// ④ n1모음형: 탭 2칸 `単語 \t （よみ） 뜻` (읽기와 뜻이 한 칸에) → 3칸으로 분리
//    — 전각（）선두일 때만. 반각 (…) 선두는 정당한 뜻 주석일 수 있어 건드리지 않는다
function preprocess(raw: string): string {
  return raw
    .replace(/^﻿/, '')
    .split(/\r?\n/)
    .map((line) => {
      const cols = line.split('\t')
      if (cols.length === 3) {
        cols[1] = cols[1].trim().replace(/^（(.*)）$/, '$1')
        return cols.join('\t')
      }
      if (cols.length === 2) {
        const m2 = cols[1].trim().match(/^（([^（）]+)）\s*(.+)$/)
        if (m2) return `${cols[0].trim()}\t${m2[1].trim()}\t${m2[2].trim()}`
        return line
      }
      // 탭이 없는 줄: 単語（よみ）[,，] 뜻 — 뜻에 쉼표가 더 있어도 통째로 보존
      const m = line.match(/^(.+?)（(.+?)）\s*[,，]\s*(.+)$/)
      if (m) return `${m[1].trim()}\t${m[2].trim()}\t${m[3].trim()}`
      return line
    })
    .join('\n')
}

export default function ImportFiles() {
  const [files, setFiles] = useState<File[]>([])
  const [prefix, setPrefix] = useState('')   // 덱 이름 접두어 — "N1 — " + day01 식으로 출처 구분 (폴더 기능 전까지의 정리 수단)
  const [busy, setBusy] = useState(false)
  const [current, setCurrent] = useState('')
  const [results, setResults] = useState<FileResult[]>([])

  async function run() {
    if (busy || files.length === 0) return
    setBusy(true)
    setResults([])
    const out: FileResult[] = []
    // 순차 처리 — 파일 수십 개를 동시에 쏘면 서버(1GB)가 고생한다. 하나 끝나면 다음
    for (const f of files) {
      setCurrent(f.name)
      const title = (prefix.trim() ? `${prefix.trim()} ` : '') + f.name.replace(/\.[^.]+$/, '')
      try {
        const text = preprocess(await f.text())
        if (!text.trim()) throw new Error('빈 파일')
        // 덱 생성+카드 등록 단일 트랜잭션 API — 등록이 실패하면 빈 덱도 안 남는다 (Codex 검산 8/29)
        const res = await api<{ deckId: number; imported: number; skipped: number; failedCount: number; failed: FailedLine[] }>(
          '/decks/import-file',
          { method: 'POST', body: JSON.stringify({ title, text, separator: '' }) },
        )
        out.push({ name: f.name, ...res })
      } catch (e) {
        // 한 파일이 실패해도 나머지는 계속 — 결과표에 이유 표시
        out.push({ name: f.name, error: (e as Error).message })
      }
      setResults([...out])
    }
    setCurrent('')
    setBusy(false)
    setFiles([])
  }

  const totalImported = results.reduce((s, r) => s + (r.imported ?? 0), 0)
  const done = !busy && results.length > 0

  return (
    <>
      <TopNav />
      <div className="shell">
        <p style={{ margin: '0 0 14px' }}>
          <Link to="/decks" className="hero-secondary">← 내 덱</Link>
        </p>
        <div className="page-head">
          <div>
            <h1>파일로 덱 만들기</h1>
            <p className="sub">
              txt 파일 여러 개를 고르면 <b>파일명이 곧 덱 이름</b>이 됩니다 · 한 줄에 카드 하나
              (단어|뜻 또는 단어|읽기|뜻, 탭·쉼표 자동 감지) · 읽기의 전각 괄호（）는 자동으로 벗겨요
            </p>
          </div>
        </div>

        <div className="quiz-setup">
          <div className="setup-row">
            <span className="setup-label">이름 앞에 붙일 말</span>
            <input
              placeholder="예: N1 (선택)"
              value={prefix}
              disabled={busy}
              maxLength={30}
              onChange={(e) => setPrefix(e.target.value)}
              style={{ maxWidth: 220 }}
            />
            {prefix.trim() && files.length > 0 && (
              <span className="muted" style={{ fontSize: 12.5 }}>
                → "{prefix.trim()} {files[0].name.replace(/\.[^.]+$/, '')}" 식으로 만들어져요
              </span>
            )}
          </div>

          <label className="file-pick">
            <input
              type="file"
              multiple
              accept=".txt,.csv,.tsv"
              disabled={busy}
              onChange={(e) => {
                setFiles(Array.from(e.target.files ?? []))
                setResults([])
              }}
            />
            📁 파일 선택 (여러 개 가능)
          </label>

          {files.length > 0 && (
            <p className="muted" style={{ margin: 0, fontSize: 13 }}>
              선택됨: {files.map((f) => f.name).join(', ')}
            </p>
          )}

          <p className="muted" style={{ margin: 0, fontSize: 12.5 }}>
            ⚠️ 같은 파일을 두 번 넣으면 같은 이름의 덱이 하나 더 생깁니다 (합치기 아님)
          </p>

          <div className="answer-buttons">
            <button className="answer-yes" disabled={busy || files.length === 0} onClick={run}>
              {busy ? `등록 중… (${current})` : `${files.length}개 파일로 덱 만들기`}
            </button>
          </div>
        </div>

        {results.length > 0 && (
          <div style={{ marginTop: 18 }}>
            {done && (
              <p className="result-line" style={{ fontSize: 15 }}>
                <b>완료 🎉 덱 {results.filter((r) => r.deckId).length}개 · 카드 {totalImported}장 등록</b>
              </p>
            )}
            <div className="word-list">
              {results.map((r) => (
                <div key={r.name}>
                  <div className="word-row">
                    <span className="word-front">
                      {r.deckId ? <Link to={`/decks/${r.deckId}`}>{r.name.replace(/\.[^.]+$/, '')}</Link> : r.name}
                    </span>
                    <span className="word-back" style={r.error ? { color: '#b0485c' } : undefined}>
                      {r.error
                        ? `실패: ${r.error}`
                        : <>{r.imported}장 등록{(r.skipped ?? 0) > 0 && <> · 중복 {r.skipped}</>}{(r.failedCount ?? 0) > 0 && <> · 실패 {r.failedCount}줄</>}</>}
                    </span>
                  </div>
                  {/* 실패 줄 상세 — 서버가 원래 주던 목록인데 그동안 화면이 버리고 숫자만 보여줬다 (8/28) */}
                  {(r.failed ?? []).length > 0 && (
                    <div style={{ margin: '2px 0 8px 30px' }}>
                      {r.failed!.map((f) => (
                        <p key={f.line} className="muted" style={{ margin: '2px 0', fontSize: 12.5, color: '#b0485c' }}>
                          {f.line}행: {f.content}
                        </p>
                      ))}
                      <p className="muted" style={{ margin: '4px 0 0', fontSize: 12 }}>
                        실패 원인은 둘 중 하나예요 — 칸 모양이 안 맞음(구분자 기준 2~3칸 아님, 단어/뜻 빈칸) 또는 글자 수 초과(단어·뜻 255자, 읽기 200자)
                      </p>
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </>
  )
}
