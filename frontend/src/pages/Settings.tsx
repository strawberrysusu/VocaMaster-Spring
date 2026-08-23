import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { clearToken, getToken } from '../api/client'
import TopNav from '../components/TopNav'
import { ACCENTS, loadSettings, saveSettings, type AccentKey, type Settings as SettingsT } from '../lib/settings'
import { isTtsSupported, speak, voicesFor } from '../lib/tts'

const SAMPLE: Record<'en' | 'ja' | 'ko', string> = { en: 'meticulous', ja: 'かいぎ', ko: '안녕하세요' }
const LANG_LABEL: Record<'en' | 'ja' | 'ko', string> = { en: '영어', ja: '일본어', ko: '한국어' }

function emailFromToken(): string {
  const token = getToken()
  if (!token) return ''
  try {
    return JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))).email ?? ''
  } catch {
    return ''
  }
}

// 설정 — 전부 이 브라우저에만 저장. 목업의 스위치 4개(accent·deckColumns·quizAutoAdvance·음성)를 실물로.
export default function Settings() {
  const navigate = useNavigate()
  const [s, setS] = useState<SettingsT>(loadSettings)
  const [voiceTick, setVoiceTick] = useState(0)   // 음성 목록은 비동기로 채워져서 한 번 더 그린다

  useEffect(() => {
    if (!isTtsSupported()) return
    const refresh = () => setVoiceTick((n) => n + 1)
    speechSynthesis.addEventListener('voiceschanged', refresh)
    const t = setTimeout(refresh, 800)
    return () => {
      speechSynthesis.removeEventListener('voiceschanged', refresh)
      clearTimeout(t)
    }
  }, [])

  function update(patch: Partial<SettingsT>) {
    const next = { ...s, ...patch }
    setS(next)
    saveSettings(next)   // 즉시 저장 + 즉시 적용 (저장 버튼 없음)
  }

  async function logout() {
    await fetch('/auth/logout', { method: 'POST' }).catch(() => {})
    clearToken()
    navigate('/login')
  }

  return (
    <>
      <TopNav />
      <div className="shell">
        <div className="page-head">
          <div>
            <h1>설정</h1>
            <p className="sub">이 브라우저에 저장돼요 · 바꾸면 바로 적용</p>
          </div>
        </div>

        <section className="stat-card settings-section">
          <h2>테마 색</h2>
          <div className="swatches">
            {(Object.keys(ACCENTS) as AccentKey[]).map((k) => (
              <button
                key={k}
                className={`swatch ${s.accent === k ? 'on' : ''}`}
                style={{ background: ACCENTS[k].a }}
                onClick={() => update({ accent: k })}
                aria-label={ACCENTS[k].label}
                aria-pressed={s.accent === k}
                title={ACCENTS[k].label}
              />
            ))}
            <span className="muted" style={{ fontSize: 13 }}>{ACCENTS[s.accent].label}</span>
          </div>
        </section>

        <section className="stat-card settings-section">
          <h2>🔊 발음 음성</h2>
          <p className="muted" style={{ margin: '0 0 12px', fontSize: 13 }}>
            이 기기·브라우저에 설치된 음성 중에서 골라요 (Chrome은 Google, Edge는 Microsoft Natural 음성이 좋아요). 비워두면 자동 선택.
          </p>
          {!isTtsSupported() && <p className="error">이 브라우저는 음성 합성을 지원하지 않아요.</p>}
          {isTtsSupported() &&
            (['en', 'ja', 'ko'] as const).map((lang) => {
              const voices = voicesFor(lang)
              void voiceTick
              return (
                <div key={lang} className="setup-row" style={{ marginBottom: 10 }}>
                  <span className="setup-label">{LANG_LABEL[lang]}</span>
                  <select
                    className="voice-select"
                    value={s.voices[lang] ?? ''}
                    onChange={(e) => update({ voices: { ...s.voices, [lang]: e.target.value || undefined } })}
                  >
                    <option value="">자동 ({voices.length}개 중)</option>
                    {voices.map((v) => (
                      <option key={v.name} value={v.name}>
                        {v.name} {v.localService ? '' : '(온라인)'}
                      </option>
                    ))}
                  </select>
                  <button type="button" className="mode-btn" onClick={() => speak(SAMPLE[lang], lang === 'en' ? 'en-US' : lang === 'ja' ? 'ja-JP' : 'ko-KR')}>
                    들어보기
                  </button>
                </div>
              )
            })}
        </section>

        <section className="stat-card settings-section">
          <h2>학습</h2>
          <div className="setup-row" style={{ marginBottom: 12 }}>
            <span className="setup-label">퀴즈</span>
            <label className="toggle-row">
              <input type="checkbox" checked={s.quizAutoAdvance} onChange={(e) => update({ quizAutoAdvance: e.target.checked })} />
              답하면 1초 뒤 자동으로 다음 문제
            </label>
          </div>
          <div className="setup-row">
            <span className="setup-label">덱 열 수</span>
            <div className="sort-tabs">
              {([2, 3, 4] as const).map((n) => (
                <button key={n} className={s.deckColumns === n ? 'active' : ''} onClick={() => update({ deckColumns: n })}>
                  {n}열
                </button>
              ))}
            </div>
          </div>
        </section>

        <section className="stat-card settings-section">
          <h2>계정</h2>
          <p className="muted" style={{ margin: '0 0 12px' }}>{emailFromToken() || '로그인 정보 없음'}</p>
          <button className="mode-btn" onClick={logout}>로그아웃</button>
        </section>
      </div>
    </>
  )
}
