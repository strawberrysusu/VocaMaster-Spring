import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import { applySettings } from './lib/settings'

applySettings()   // 저장된 테마 색·덱 열 수를 첫 렌더 전에 CSS 변수로
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
