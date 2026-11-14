import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, clearToken, getToken, logout as clientLogout } from '../api/client'
import TopNav from '../components/TopNav'
import { gap, getLang, t } from '../i18n'
import { jwtEmail } from '../lib/jwt'
import { ACCENTS, loadSettings, saveSettings, type AccentKey, type Settings as SettingsT } from '../lib/settings'
import { isTtsSupported, speak, voicesFor } from '../lib/tts'

// 들어보기 견본 — 그 언어 음성으로 읽을 말이라 화면 언어와 상관없이 그대로 둔다 (번역하지 않음)
const SAMPLE: Record<'en' | 'ja' | 'ko', string> = { en: 'meticulous', ja: 'かいぎ', ko: '안녕하세요' }
const LANG_LABEL: Record<'en' | 'ja' | 'ko', string> = { en: t('영어'), ja: t('일본어'), ko: t('한국어') }

function emailFromToken(): string {
  return jwtEmail(getToken())   // UTF-8로 읽는다 — 한글 이메일이면 탈퇴 확인이 늘 실패하던 것 (11/14 검토)
}

// 설정 — 전부 이 브라우저에만 저장. 목업의 스위치 4개(accent·deckColumns·quizAutoAdvance·음성)를 실물로.
export default function Settings() {
  const navigate = useNavigate()
  const [s, setS] = useState<SettingsT>(loadSettings)
  const [voiceTick, setVoiceTick] = useState(0)   // 음성 목록은 비동기로 채워져서 한 번 더 그린다
  const [provider, setProvider] = useState<string | null>(null)   // null = 조회 전
  const [rankingMasked, setRankingMasked] = useState<boolean | null>(null)   // 학습 랭킹 닉네임 가리기 (null = 조회 전)
  const [rankSaving, setRankSaving] = useState(false)
  const [rankError, setRankError] = useState('')
  const [pwCur, setPwCur] = useState('')
  const [pwNew, setPwNew] = useState('')
  const [pwNew2, setPwNew2] = useState('')
  const [pwBusy, setPwBusy] = useState(false)
  const [pwError, setPwError] = useState('')
  const [withdrawing, setWithdrawing] = useState(false)
  const [withdrawError, setWithdrawError] = useState('')

  useEffect(() => {
    if (!isTtsSupported()) return
    const refresh = () => setVoiceTick((n) => n + 1)
    speechSynthesis.addEventListener('voiceschanged', refresh)
    const timer = setTimeout(refresh, 800)
    return () => {
      speechSynthesis.removeEventListener('voiceschanged', refresh)
      clearTimeout(timer)
    }
  }, [])

  // 구글 가입자는 password가 null이라(AuthService) 비밀번호 변경 폼 자체가 의미 없다.
  // TopNav 프로필 팝오버가 쓰는 것과 같은 /users/me의 provider로 가른다
  useEffect(() => {
    api<{ provider: string; rankingMasked: boolean }>('/users/me')
      .then((me) => {
        setProvider(me.provider)
        setRankingMasked(me.rankingMasked)
      })
      .catch(() => setProvider('local'))   // 조회 실패로 폼을 감추면 바꿀 길이 없어진다 — 보이는 쪽으로 실패
  }, [])

  // 학습 랭킹에서 닉네임 가리기 (ADR-059) — 서버에 저장한다. 랭킹 응답을 서버가 가려서 보내니 이 브라우저 설정이 아니다
  // 누르면 바로 바꿔 보이고(서버 응답을 기다리면 그동안 '안 눌린 것'처럼 보인다), 실패하면 되돌린다
  async function toggleRankingMask(masked: boolean) {
    const before = rankingMasked
    setRankingMasked(masked)
    setRankSaving(true)
    setRankError('')
    try {
      const me = await api<{ rankingMasked: boolean }>('/users/me/ranking', { method: 'PATCH', body: JSON.stringify({ masked }) })
      setRankingMasked(me.rankingMasked)
    } catch (e) {
      setRankingMasked(before)
      setRankError((e as Error).message)
    } finally {
      setRankSaving(false)
    }
  }

  function update(patch: Partial<SettingsT>) {
    const next = { ...s, ...patch }
    setS(next)
    saveSettings(next)   // 즉시 저장 + 즉시 적용 (저장 버튼 없음)
  }

  /**
   * 비밀번호 변경. 서버가 성공 시 <b>모든 refresh 토큰을 폐기</b>하므로 (UserService.changePassword)
   * 지금 세션도 곧 끊긴다. 어중간하게 남겨두면 다음 갱신에서 영문 모를 로그아웃이 되니
   * 여기서 명시적으로 로그인 화면으로 보낸다.
   */
  async function changePassword(e: FormEvent) {
    e.preventDefault()
    setPwError('')
    if (pwNew.length < 8) {
      setPwError(t('새 비밀번호는 8자 이상이어야 해요'))          // 서버 @Size(min=8)와 같은 기준
      return
    }
    if (pwNew !== pwNew2) {
      setPwError(t('새 비밀번호가 서로 달라요'))
      return
    }
    if (pwNew === pwCur) {
      setPwError(t('새 비밀번호가 현재 비밀번호와 같아요'))
      return
    }
    setPwBusy(true)
    try {
      await api('/users/me/password', {
        method: 'PATCH',
        body: JSON.stringify({ currentPassword: pwCur, newPassword: pwNew }),
      })
      setPwCur(''); setPwNew(''); setPwNew2('')
      window.alert(t('비밀번호를 바꿨어요.\n이 기기에서는 로그아웃됩니다. 다른 기기도 로그인 토큰이 만료되면 다시 로그인해야 해요.'))
      clearToken()
      navigate('/login', { replace: true })
    } catch (err) {
      setPwError((err as Error).message)
    } finally {
      setPwBusy(false)
    }
  }

  /**
   * 회원 탈퇴 — 되돌릴 수 없으므로 두 번 확인한다.
   * 1차는 무슨 일이 벌어지는지, 2차는 이메일을 직접 입력해 '내 계정이 맞다'를 확인.
   * 덱 삭제(window.confirm 한 번)보다 한 겹 더 두는 이유: 계정은 복구 경로가 UI에 없다.
   */
  async function withdraw() {
    setWithdrawError('')   // 취소하고 다시 눌렀을 때 이전 실패 문구가 남지 않게
    const email = emailFromToken()
    if (!window.confirm(t('회원 탈퇴하면 다시 로그인할 수 없어요.\n계속할까요?'))) return

    const typed = window.prompt(t('확인을 위해 이메일을 그대로 입력해 주세요.\n\n{email}', { email }))
    if (typed === null) return                        // 취소
    if (typed.trim() !== email) {
      setWithdrawError(t('이메일이 일치하지 않아 탈퇴를 진행하지 않았어요.'))
      return
    }

    setWithdrawing(true)
    setWithdrawError('')
    try {
      await api('/users/me', { method: 'DELETE' })
      clearToken()                                    // 서버가 refresh 토큰도 전부 폐기한다
      navigate('/login', { replace: true })
    } catch (e) {
      setWithdrawError((e as Error).message)
    } finally {
      setWithdrawing(false)
    }
  }

  async function logout() {
    await clientLogout()   // 진행 중인 토큰 갱신이 끝난 뒤 로그아웃하고 토큰을 지운다 (A1)
    navigate('/login')
  }

  return (
    <>
      <TopNav />
      <div className="shell">
        <div className="page-head">
          <div>
            <h1>{t('설정')}</h1>
            <p className="sub">{t('이 브라우저에 저장돼요 · 바꾸면 바로 적용')}</p>
          </div>
        </div>

        <section className="stat-card settings-section">
          <h2>{t('언어')}</h2>
          <p className="muted" style={{ margin: 0 }}>{t('언어는 화면 오른쪽 위 🌐에서 바꿔요')}</p>
        </section>

        <section className="stat-card settings-section">
          <h2>{t('테마 색')}</h2>
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
          <h2>{t('🔊 발음 음성')}</h2>
          <p className="muted" style={{ margin: '0 0 12px', fontSize: 13 }}>
            {t('이 기기·브라우저에 설치된 음성 중에서 골라요 (Chrome은 Google, Edge는 Microsoft Natural 음성이 좋아요). 비워두면 자동 선택.')}
          </p>
          {!isTtsSupported() && <p className="error">{t('이 브라우저는 음성 합성을 지원하지 않아요.')}</p>}
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
                    <option value="">{t('자동 ({n}개 중)', { n: voices.length })}</option>
                    {voices.map((v) => (
                      <option key={v.name} value={v.name}>
                        {v.name} {v.localService ? '' : t('(온라인)')}
                      </option>
                    ))}
                  </select>
                  <button type="button" className="mode-btn" onClick={() => speak(SAMPLE[lang], lang === 'en' ? 'en-US' : lang === 'ja' ? 'ja-JP' : 'ko-KR')}>
                    {t('들어보기')}
                  </button>
                </div>
              )
            })}
        </section>

        <section className="stat-card settings-section">
          <h2>{t('학습')}</h2>
          <div className="setup-row" style={{ marginBottom: 12 }}>
            <span className="setup-label">{t('퀴즈')}</span>
            <label className="toggle-row">
              <input type="checkbox" checked={s.quizAutoAdvance} onChange={(e) => update({ quizAutoAdvance: e.target.checked })} />
              {t('답하면 1초 뒤 자동으로 다음 문제')}
            </label>
          </div>
          <div className="setup-row" style={{ marginBottom: 12 }}>
            <span className="setup-label">{t('복습')}</span>
            <label className="toggle-row">
              <input type="checkbox" checked={s.autoSpeak} onChange={(e) => update({ autoSpeak: e.target.checked })} />
              {t('카드를 뒤집으면 발음 자동 재생')}
            </label>
            <span className="muted" style={{ fontSize: 12.5 }}>{t('복습 화면의 🔊 버튼으로도 켜고 끌 수 있어요 (같은 설정)')}</span>
          </div>
          <div className="setup-row" style={{ marginBottom: 12 }}>
            <span className="setup-label">{t('선택지 수')}</span>
            <div className="sort-tabs">
              {([4, 5, 6] as const).map((n) => (
                <button key={n} className={s.quizChoices === n ? 'active' : ''} onClick={() => update({ quizChoices: n })}>
                  {t('{n}지선다', { n })}
                </button>
              ))}
            </div>
            <span className="muted" style={{ fontSize: 12.5 }}>{t('퀴즈 시작 화면에서도 바꿀 수 있어요 (같은 설정)')}</span>
          </div>
          <div className="setup-row">
            <span className="setup-label">{t('덱 열 수')}</span>
            <div className="sort-tabs">
              {([2, 3, 4] as const).map((n) => (
                <button key={n} className={s.deckColumns === n ? 'active' : ''} onClick={() => update({ deckColumns: n })}>
                  {t('{n}열', { n })}
                </button>
              ))}
            </div>
          </div>
        </section>

        <section className="stat-card settings-section">
          <h2>{t('랭킹')}</h2>
          <label className="toggle-row">
            <input
              type="checkbox"
              checked={rankingMasked === true}
              disabled={rankingMasked === null || rankSaving}
              onChange={(e) => void toggleRankingMask(e.target.checked)}
            />
            {t('랭킹에서 닉네임 가리기')}
          </label>
          <p className="muted" style={{ fontSize: 12.5, margin: '6px 0 0' }}>{t('켜면 랭킹에 qjw***처럼 앞 몇 글자만 보여요')}</p>
          {rankError && <p className="error" role="alert">{rankError}</p>}
        </section>

        <section className="stat-card settings-section">
          <h2>{t('계정')}</h2>
          <p className="muted" style={{ margin: '0 0 12px' }}>{emailFromToken() || t('로그인 정보 없음')}</p>
          <button className="mode-btn" onClick={logout}>{t('로그아웃')}</button>

          {provider === 'google' && (
            <div className="pw-form">
              <h3>{t('비밀번호')}</h3>
              <p className="muted pw-note">
                {t('구글로 가입한 계정이라 이 서비스에는 비밀번호가 없어요. 비밀번호는 구글 계정에서 관리합니다.')}
              </p>
            </div>
          )}

          {provider === 'local' && (
          <form className="pw-form" onSubmit={changePassword}>
            <h3>{t('비밀번호 변경')}</h3>
            <input type="password" autoComplete="current-password" placeholder={t('현재 비밀번호')}
                   value={pwCur} onChange={(e) => setPwCur(e.target.value)} required />
            <input type="password" autoComplete="new-password" placeholder={t('새 비밀번호 (8자 이상)')}
                   value={pwNew} onChange={(e) => setPwNew(e.target.value)} required />
            <input type="password" autoComplete="new-password" placeholder={t('새 비밀번호 확인')}
                   value={pwNew2} onChange={(e) => setPwNew2(e.target.value)} required />
            {pwError && <p className="error" role="alert">{pwError}</p>}
            <p className="muted pw-note">
              {t('바꾸면 이 기기에서 로그아웃돼요. 다른 기기도 로그인 토큰이 만료되면 다시 로그인해야 해요.')}
            </p>
            <button className="btn-primary" type="submit" disabled={pwBusy}>
              {pwBusy ? t('변경 중...') : t('비밀번호 변경')}
            </button>
          </form>
          )}

          <div className="danger-zone">
            <h3>{t('회원 탈퇴')}</h3>
            {/*
              문구는 privacy.html의 '보관 및 파기'와 같은 말을 해야 한다 —
              소프트 삭제라 계정이 비활성화될 뿐 남은 데이터가 즉시 파기되지는 않는다.
              "모든 데이터가 삭제됩니다"라고 쓰면 코드가 하지 않는 약속이 된다.
            */}
            <p className="muted">
              {t('탈퇴하면 계정이 즉시 비활성화되어 로그인할 수 없고, 공개한 단어장도 탐색에 노출되지 않아요. 남은 데이터의 완전 삭제를 원하시면')}{gap()}
              <a className="doc-link" href="/privacy.html" target="_blank" rel="noreferrer">{getLang() === 'ko' ? t('개인정보처리방침') : t('개인정보처리방침 (한국어)')}</a>{t('의 메일로 요청해 주세요.')}
            </p>
            {withdrawError && <p className="error" role="alert">{withdrawError}</p>}
            <button className="danger-btn" disabled={withdrawing} onClick={withdraw}>
              {withdrawing ? t('처리 중...') : t('회원 탈퇴')}
            </button>
          </div>
        </section>
      </div>
    </>
  )
}
