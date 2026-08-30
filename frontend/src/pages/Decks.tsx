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
  folderId: number | null
}

interface Folder {
  id: number
  name: string
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
  // 페이지 나누기 (8/28) — 덱 수십 개를 한 화면에 쏟으면 과부하. 목록은 이미 다 받아오므로 화면에서만 나눈다
  const [page, setPage] = useState(0)
  const PAGE_SIZE = 30
  // 📁 폴더 (8/29, 동결 전 마지막 기능) — 'all'=전체, 'none'=미분류, number=그 폴더
  const [folders, setFolders] = useState<Folder[]>([])
  const [activeFolder, setActiveFolder] = useState<'all' | 'none' | number>('all')
  // 폴더에 덱 담기 (8/30) — "폴더 만들고 들어가면 빈 화면에서 덱을 가져올 방법이 없다"는 UX 구멍 수리.
  // 보내기(선택 모드→폴더로 이동)의 역방향: 폴더 화면에서 기존 덱을 골라 가져온다. 같은 이동 API.
  const [pickerOpen, setPickerOpen] = useState(false)
  const [picked, setPicked] = useState<Set<number>>(new Set())
  const [pickerQuery, setPickerQuery] = useState('')

  function load() {
    api<Deck[]>('/decks').then(setDecks).catch((e) => setError(e.message))
    api<Folder[]>('/folders').then(setFolders).catch((e) => setError(e.message))
  }

  useEffect(load, [])

  async function createFolder() {
    const name = window.prompt('새 폴더 이름 (예: JLPT N1)')
    if (!name?.trim()) return
    try {
      await api('/folders', { method: 'POST', body: JSON.stringify({ name: name.trim() }) })
      load()
    } catch (e) {
      setError((e as Error).message)
    }
  }

  async function renameFolder(f: Folder) {
    const name = window.prompt('폴더 이름 변경', f.name)
    if (!name?.trim() || name.trim() === f.name) return
    try {
      await api(`/folders/${f.id}`, { method: 'PATCH', body: JSON.stringify({ name: name.trim() }) })
      load()
    } catch (e) {
      setError((e as Error).message)
    }
  }

  async function removeFolder(f: Folder) {
    if (!window.confirm(`"${f.name}" 폴더를 삭제할까요?\n안의 덱들은 삭제되지 않고 미분류로 이동해요.`)) return
    try {
      await api(`/folders/${f.id}`, { method: 'DELETE' })
      if (activeFolder === f.id) setActiveFolder('all')
      load()
    } catch (e) {
      setError((e as Error).message)
    }
  }

  // 선택한 덱들을 폴더로 이동 — 기존 이동 API 순차 호출 (일괄 공개와 같은 패턴)
  async function moveSelected(folderId: number | null) {
    if (busy || selected.size === 0) return
    setBusy(true)
    setError('')
    try {
      for (const id of selected) {
        await api(`/decks/${id}/folder`, { method: 'PATCH', body: JSON.stringify({ folderId }) })
      }
      setSelected(new Set())
      setSelectMode(false)
      load()
    } catch (e) {
      setError((e as Error).message)
      load()
    } finally {
      setBusy(false)
    }
  }

  function openPicker() {
    setPicked(new Set())
    setPickerQuery('')
    setPickerOpen(true)
  }

  function togglePick(id: number) {
    setPicked((s) => {
      const n = new Set(s)
      if (n.has(id)) n.delete(id)
      else n.add(id)
      return n
    })
  }

  async function addPickedToFolder() {
    if (busy || picked.size === 0 || typeof activeFolder !== 'number') return
    setBusy(true)
    setError('')
    let done = 0
    try {
      for (const id of picked) {
        await api(`/decks/${id}/folder`, { method: 'PATCH', body: JSON.stringify({ folderId: activeFolder }) })
        done++
      }
      setPickerOpen(false)
      load()
    } catch (e) {
      setError(`${done}개 담은 후 중단: ${(e as Error).message} — 목록을 새로고침했어요`)
      load()
    } finally {
      setBusy(false)
    }
  }

  // 일괄 삭제 — 되돌릴 수 없는 작업이라 "정확히 뭘 지우는지" 이름까지 확인창에 (Codex 검산 8/29:
  // 화면 밖 덱이 선택에 남는 사고는 폴더 전환 시 초기화로 막고, 이름 나열은 최후 방어선)
  async function deleteSelected() {
    if (busy || selected.size === 0) return
    const names = decks.filter((d) => selected.has(d.id)).map((d) => d.title)
    const preview = names.slice(0, 5).map((n) => `· ${n}`).join('\n') + (names.length > 5 ? `\n… 외 ${names.length - 5}개` : '')
    if (!window.confirm(`선택한 ${selected.size}개 덱을 삭제할까요?\n\n${preview}\n\n각 덱의 카드와 학습·퀴즈·타이핑 기록이 전부 사라지고 되돌릴 수 없어요.`)) return
    setBusy(true)
    setError('')
    let done = 0
    try {
      for (const id of selected) {
        await api(`/decks/${id}`, { method: 'DELETE' })
        done++
      }
      setSelected(new Set())
      setSelectMode(false)
      load()
    } catch (e) {
      // 순차 처리라 중간 실패 시 앞부분만 지워짐 — 어디까지 됐는지 명시하고 실상태 재조회
      setError(`${done}개 삭제 후 중단: ${(e as Error).message} — 목록을 새로고침했어요`)
      load()
    } finally {
      setBusy(false)
    }
  }

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
  const filtered = decks.filter((d) =>
    activeFolder === 'all' ? true : activeFolder === 'none' ? d.folderId === null : d.folderId === activeFolder,
  )
  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const safePage = Math.min(page, totalPages - 1)   // 삭제·폴더 전환으로 페이지 수가 줄어도 빈 화면 안 되게
  const visible = filtered.slice(safePage * PAGE_SIZE, (safePage + 1) * PAGE_SIZE)
  const unfiledCount = decks.filter((d) => d.folderId === null).length
  // 담기 후보 = 지금 보는 폴더에 없는 모든 덱 (다른 폴더 소속 포함 — 담으면 옮겨진다고 모달에서 안내)
  const activeFolderObj = typeof activeFolder === 'number' ? folders.find((f) => f.id === activeFolder) : undefined
  const candidates = typeof activeFolder === 'number' ? decks.filter((d) => d.folderId !== activeFolder) : []
  const shownCandidates = candidates.filter((d) => d.title.toLowerCase().includes(pickerQuery.trim().toLowerCase()))

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

        <div className="folder-bar">
          <button className={`folder-chip ${activeFolder === 'all' ? 'active' : ''}`} onClick={() => { setActiveFolder('all'); setPage(0); setSelected(new Set()) }}>
            전체 {decks.length}
          </button>
          <button className={`folder-chip ${activeFolder === 'none' ? 'active' : ''}`} onClick={() => { setActiveFolder('none'); setPage(0); setSelected(new Set()) }}>
            📂 미분류 {unfiledCount}
          </button>
          {folders.map((f) => (
            <span key={f.id} className={`folder-chip ${activeFolder === f.id ? 'active' : ''}`}>
              <button className="folder-chip-name" onClick={() => { setActiveFolder(f.id); setPage(0); setSelected(new Set()) }}>
                📁 {f.name} {decks.filter((d) => d.folderId === f.id).length}
              </button>
              {activeFolder === f.id && (
                <>
                  <button className="folder-chip-op" title="이름 변경" aria-label={`${f.name} 이름 변경`} onClick={() => renameFolder(f)}>✎</button>
                  <button className="folder-chip-op" title="폴더 삭제 (덱은 미분류로)" aria-label={`${f.name} 삭제`} onClick={() => removeFolder(f)}>🗑</button>
                </>
              )}
            </span>
          ))}
          <button className="folder-chip folder-add" onClick={createFolder}>+ 새 폴더</button>
        </div>

        {selectMode && (
          <div className="bulk-bar">
            <span>{selected.size}개 선택</span>
            <button className="btn-ghost-link" disabled={busy} onClick={() => setSelected(new Set(filtered.map((d) => d.id)))}>
              전체 선택 ({filtered.length}개{activeFolder === 'all' ? ', 모든 페이지' : ' — 현재 폴더'})
            </button>
            <span className="bulk-sep">→</span>
            <button className="btn-ghost-link" disabled={busy || selected.size === 0} onClick={() => applyVisibility('PUBLIC')}>🌐 전체 공개</button>
            <button className="btn-ghost-link" disabled={busy || selected.size === 0} onClick={() => applyVisibility('UNLISTED')}>🔗 링크 공개</button>
            <button className="btn-ghost-link" disabled={busy || selected.size === 0} onClick={() => applyVisibility('PRIVATE')}>🔒 비공개</button>
            <span className="bulk-sep">·</span>
            <select
              className="bulk-move"
              disabled={busy || selected.size === 0}
              value=""
              aria-label="선택한 덱을 폴더로 이동"
              onChange={(e) => {
                if (e.target.value === '') return
                moveSelected(e.target.value === 'none' ? null : Number(e.target.value))
              }}
            >
              <option value="">📁 폴더로 이동…</option>
              <option value="none">📂 미분류로</option>
              {folders.map((f) => (
                <option key={f.id} value={f.id}>📁 {f.name}</option>
              ))}
            </select>
            <button className="btn-ghost-link bulk-danger" disabled={busy || selected.size === 0} onClick={deleteSelected}>🗑 삭제</button>
            {busy && <span className="muted">적용 중…</span>}
          </div>
        )}

        {error && <p className="error" role="alert">{error}</p>}

        <div className="deck-grid">
          {typeof activeFolder === 'number' && !selectMode && (
            <button className="deck-card deck-add-card" onClick={openPicker}>
              <span className="add-plus">＋</span>
              <p className="title">이 폴더에 덱 담기</p>
              <p className="meta">이미 만든 덱을 골라서 가져와요</p>
            </button>
          )}
          {visible.map((d) => (
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
          {filtered.length === 0 && (
            <p className="muted">
              {decks.length === 0
                ? '덱이 없어요. 위에서 첫 덱을 만들어보세요.'
                : typeof activeFolder === 'number'
                  ? '이 폴더엔 아직 덱이 없어요 — "＋ 덱 담기" 카드로 채워보세요.'
                  : '미분류 덱이 없어요 — 전부 폴더에 정리되어 있어요.'}
            </p>
          )}
        </div>

        {totalPages > 1 && (
          <div className="pager">
            <button disabled={safePage === 0} onClick={() => setPage(safePage - 1)}>◀ 이전</button>
            {Array.from({ length: totalPages }, (_, i) => (
              <button key={i} className={i === safePage ? 'active' : ''} onClick={() => setPage(i)}>
                {i + 1}
              </button>
            ))}
            <button disabled={safePage === totalPages - 1} onClick={() => setPage(safePage + 1)}>다음 ▶</button>
          </div>
        )}

        {pickerOpen && typeof activeFolder === 'number' && (
          <div className="modal-overlay" onClick={() => !busy && setPickerOpen(false)}>
            <div className="modal-panel" role="dialog" aria-label="폴더에 덱 담기" onClick={(e) => e.stopPropagation()}>
              <div className="modal-head">
                <h2>📁 {activeFolderObj?.name ?? '폴더'}에 덱 담기</h2>
                <button className="modal-close" aria-label="닫기" disabled={busy} onClick={() => setPickerOpen(false)}>✕</button>
              </div>
              {candidates.length === 0 ? (
                <p className="muted">담을 수 있는 덱이 없어요 — 모든 덱이 이미 이 폴더에 있어요.</p>
              ) : (
                <>
                  <div className="picker-tools">
                    <input
                      aria-label="담을 덱 검색"
                      placeholder="덱 이름 검색"
                      value={pickerQuery}
                      onChange={(e) => setPickerQuery(e.target.value)}
                    />
                    <button className="btn-ghost-link" disabled={busy || shownCandidates.length === 0} onClick={() => setPicked(new Set(shownCandidates.map((d) => d.id)))}>
                      보이는 것 전체 선택
                    </button>
                    <button className="btn-ghost-link" disabled={busy || picked.size === 0} onClick={() => setPicked(new Set())}>
                      해제
                    </button>
                  </div>
                  <div className="pick-list">
                    {shownCandidates.map((d) => (
                      <label key={d.id} className="pick-row">
                        <input type="checkbox" checked={picked.has(d.id)} disabled={busy} onChange={() => togglePick(d.id)} />
                        <span className="pick-title">{d.title}</span>
                        <span className="pick-where">
                          {d.folderId === null ? '📂 미분류' : `📁 ${folders.find((f) => f.id === d.folderId)?.name ?? '다른 폴더'}`} · {d.cardCount}장
                        </span>
                      </label>
                    ))}
                    {shownCandidates.length === 0 && <p className="muted">검색과 일치하는 덱이 없어요.</p>}
                  </div>
                  <div className="modal-foot">
                    <span className="muted">다른 폴더의 덱을 담으면 그쪽에서는 빠져요 (덱은 한 폴더에만 소속)</span>
                    <button className="btn-primary" disabled={busy || picked.size === 0} onClick={addPickedToFolder}>
                      {busy ? '담는 중…' : `${picked.size}개 담기`}
                    </button>
                  </div>
                </>
              )}
            </div>
          </div>
        )}
      </div>
    </>
  )
}
