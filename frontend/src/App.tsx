import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import type { ReactElement } from 'react'
import Login from './pages/Login'
import Home from './pages/Home'
import Decks from './pages/Decks'
import DeckDetail from './pages/DeckDetail'
import Study from './pages/Study'
import Explore from './pages/Explore'
import PublicDeckDetail from './pages/PublicDeckDetail'
import ComingSoon from './pages/ComingSoon'
import { getToken } from './api/client'

function RequireAuth({ children }: { children: ReactElement }) {
  return getToken() ? children : <Navigate to="/login" replace />
}

export default function App() {
  return (
    <BrowserRouter basename="/app">
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/" element={<RequireAuth><Home /></RequireAuth>} />
        <Route path="/decks" element={<RequireAuth><Decks /></RequireAuth>} />
        <Route path="/decks/:id" element={<RequireAuth><DeckDetail /></RequireAuth>} />
        <Route path="/study" element={<RequireAuth><Study /></RequireAuth>} />
        <Route path="/explore" element={<RequireAuth><Explore /></RequireAuth>} />
        {/* 공개 덱 상세는 비로그인 열람 허용 — UNLISTED "링크 받은 사람은 본다"(ADR-030)와 일치.
            좋아요·복사는 눌렀을 때 로그인으로 유도 (화면 안에서 처리) */}
        <Route path="/explore/:id" element={<PublicDeckDetail />} />
        <Route path="/soon/:name" element={<RequireAuth><ComingSoon /></RequireAuth>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
