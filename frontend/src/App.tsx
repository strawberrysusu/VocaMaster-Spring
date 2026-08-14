import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import type { ReactElement } from 'react'
import Login from './pages/Login'
import Home from './pages/Home'
import Decks from './pages/Decks'
import ReviewSession from './pages/ReviewSession'
import { getToken } from './api/client'

function RequireAuth({ children }: { children: ReactElement }) {
  return getToken() ? children : <Navigate to="/login" replace />
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/" element={<RequireAuth><Home /></RequireAuth>} />
        <Route path="/decks" element={<RequireAuth><Decks /></RequireAuth>} />
        <Route path="/review" element={<RequireAuth><ReviewSession /></RequireAuth>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
