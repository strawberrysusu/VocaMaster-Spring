import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import TopNav from '../components/TopNav'

interface Deck {
  id: number
  title: string
  description: string
  visibility: 'PRIVATE' | 'PUBLIC' | 'UNLISTED'
  cardCount: number
}

const VISIBILITY_LABEL: Record<Deck['visibility'], string> = {
  PRIVATE: '비공개',
  PUBLIC: '공개',
  UNLISTED: '링크 공유',
}

export default function Decks() {
  const [decks, setDecks] = useState<Deck[]>([])
  const [title, setTitle] = useState('')
  const [creating, setCreating] = useState(false)
  const [error, setError] = useState('')
  // 일괄 공개 전환 (8/28, 동결 예외 3조건 통과: 실사용 필요·기존 API 재사용·운영 부담 0)
  const [selectMode, setSelectMode] = useState(false)
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [busy, setBusy] = useState(false)

  function load() {
    api<Deck[]>('/decks').then(setDecks).catch((e) => setError(e.message))
  }

  useEffect(load, [])

  async function create() {
    if (creating || !title.trim()) return // 더블클릭/엔터 연타 중복 생성 방어
    setCreating(true)
    try {
      await api('/decks', { method: 'POST', body: JSON.stringify({ title }) })
      setTitle('')
      load()
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setCreating(false)
    }
  }

  function toggleSelect(id: number) {
    setSelected((s) => {
      const n = new Set(s)
      if (n.has(id)) n.delete(id)
      else n.add(id)
      return n
    })
  }

  // 랭킹 훅(공개 전환 시 ZSET 등록) 때문에 DB 한 방이 아니라 기존 API를 덱마다 호출한다
  async function applyVisibility(v: Deck['visibility']) {
    if (busy || selected.size === 0) return
    if (v === 'PUBLIC' && !window.confirm(`선택한 ${selected.size}개 덱을 전체 공개할까요?\n탐색에 노출되고 누구나 검색·복사할 수 있어요.`)) return
    setBusy(true)
    setError('')
    try {
      for (const id of selected) {
        await api(`/decks/${id}/visibility`, { method: 'PATCH', body: JSON.stringify({ visibility: v }) })
      }
      setSelected(new Set())
      setSelectMode(false)
      load()
    } catch (e) {
      setError((e as Error).message)
      load()   // 일부만 바뀐 경우 실제 상태로 갱신
    } finally {
      setBusy(false)
    }
  }

  const totalCards = decks.reduce((a, d) => a + d.cardCount, 0)

  return (
    <>
      <TopNav />
      <div className="shell">
        <div className="page-head">
          <div>
            <h1>내 덱</h1>
            <p className="sub">{decks.length}개의 덱 · 전체 {totalCards}장</p>
          </div>
        </div>

        <div className="create-row">
          <input
            aria-label="새 덱 이름"
            placeholder="새 덱 이름을 입력하세요"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && create()}
          />
          <button className="btn-primary" disabled={creating || !title.trim()} onClick={create}>
            {creating ? '만드는 중...' : '덱 만들기'}
          </button>
          <Link to="/import-files" className="btn-ghost-link">📁 파일로 만들기</Link>
          <button
            className={`btn-ghost-link select-toggle ${selectMode ? 'on' : ''}`}
            onClick={() => {
              setSelectMode(!selectMode)
              setSelected(new Set())
            }}
          >
            {selectMode ? '✕ 선택 취소' : '☑ 선택'}
          </button>
        </div>

        {selectMode && (
          <div className="bulk-bar">
            <span>{selected.size}개 선택</span>
            <button className="btn-ghost-link" disabled={busy} onClick={() => setSelected(new Set(decks.map((d) => d.id)))}>
              전체 선택
            </button>
            <span className="bulk-sep">→</span>
            <button className="btn-ghost-link" disabled={busy || selected.size === 0} onClick={() => applyVisibility('PUBLIC')}>🌐 전체 공개</button>
            <button className="btn-ghost-link" disabled={busy || selected.size === 0} onClick={() => applyVisibility('UNLISTED')}>🔗 링크 공개</button>
            <button className="btn-ghost-link" disabled={busy || selected.size === 0} onClick={() => applyVisibility('PRIVATE')}>🔒 비공개</button>
            {busy && <span className="muted">적용 중…</span>}
          </div>
        )}

        {error && <p className="error" role="alert">{error}</p>}

        <div className="deck-grid">
          {decks.map((d) => (
            <Link
              key={d.id}
              to={`/decks/${d.id}`}
              className={`deck-card ${selectMode && selected.has(d.id) ? 'selected' : ''}`}
              onClick={(e) => {
                if (selectMode) {
                  e.preventDefault()
                  toggleSelect(d.id)
                }
              }}
            >
              {selectMode && <span className="pick-mark">{selected.has(d.id) ? '☑' : '☐'}</span>}
              <span className="tag">{VISIBILITY_LABEL[d.visibility]}</span>
              <p className="title">{d.title}</p>
              <p className="meta">카드 {d.cardCount}장{d.description ? ` · ${d.description}` : ''}</p>
            </Link>
          ))}
          {decks.length === 0 && <p className="muted">덱이 없어요. 위에서 첫 덱을 만들어보세요.</p>}
        </div>
      </div>
    </>
  )
}
