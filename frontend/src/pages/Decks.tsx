import { useEffect, useState } from 'react'
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
  const [error, setError] = useState('')

  function load() {
    api<Deck[]>('/decks').then(setDecks).catch((e) => setError(e.message))
  }

  useEffect(load, [])

  async function create() {
    if (!title.trim()) return
    await api('/decks', { method: 'POST', body: JSON.stringify({ title }) })
    setTitle('')
    load()
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
            placeholder="새 덱 이름을 입력하세요"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && create()}
          />
          <button className="btn-primary" disabled={!title.trim()} onClick={create}>
            덱 만들기
          </button>
        </div>

        {error && <p className="error">{error}</p>}

        <div className="deck-grid">
          {decks.map((d) => (
            <div key={d.id} className="deck-card">
              <span className="tag">{VISIBILITY_LABEL[d.visibility]}</span>
              <p className="title">{d.title}</p>
              <p className="meta">카드 {d.cardCount}장{d.description ? ` · ${d.description}` : ''}</p>
              {/* 덱 상세(카드 추가/학습 시작)는 다음 시공 화면 */}
            </div>
          ))}
          {decks.length === 0 && <p className="muted">덱이 없어요. 위에서 첫 덱을 만들어보세요.</p>}
        </div>
      </div>
    </>
  )
}
