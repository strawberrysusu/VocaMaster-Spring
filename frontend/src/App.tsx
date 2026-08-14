import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import type { ReactElement } from 'react'
import Login from './pages/Login'
import Home from './pages/Home'
import Decks from './pages/Decks'
import DeckDetail from './pages/DeckDetail'
import Study from './pages/Study'
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
        <Route path="/soon/:name" element={<RequireAuth><ComingSoon /></RequireAuth>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
