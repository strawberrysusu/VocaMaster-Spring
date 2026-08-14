import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, clearToken } from '../api/client'

interface Deck {
  id: number
  title: string
  description: string
  visibility: 'PRIVATE' | 'PUBLIC' | 'UNLISTED'
  cardCount: number
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

  async function logout() {
    await fetch('/auth/logout', { method: 'POST' }).catch(() => {})
    clearToken()
    window.location.href = '/login'
  }

  return (
    <div className="page">
      <header className="topbar">
        <h1>
          <Link to="/">VocaMaster</Link>
        </h1>
        <nav>
          <button className="ghost" onClick={logout}>
            로그아웃
          </button>
        </nav>
      </header>

      <div className="card create-row">
        <input
          placeholder="새 단어장 이름"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && create()}
        />
        <button className="primary" onClick={create}>
          만들기
        </button>
      </div>

      {error && <p className="error">{error}</p>}

      <section className="deck-grid">
        {decks.map((d) => (
          <div key={d.id} className="card deck-card">
            <h3>{d.title}</h3>
            <p className="muted">카드 {d.cardCount}장 · {d.visibility}</p>
            {/* 덱 상세/학습 화면은 다음 단계 */}
          </div>
        ))}
        {decks.length === 0 && <p className="muted">단어장이 없어요. 위에서 첫 단어장을 만들어보세요.</p>}
      </section>
    </div>
  )
}
