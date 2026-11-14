import { LANGS, changeLang, getLang, type Lang } from '../i18n'

// 언어 고르기 (ADR-056) — 로그인 화면과 설정 화면에 둔다.
// 언어 이름은 그 언어로 적고(자기 언어를 찾기 쉽게), 이름표는 어느 언어로 봐도 알 수 있게 🌐 + Language.
// 고르면 설정에 저장하고 새로고침한다(i18n/index.ts changeLang)
export default function LangSwitch() {
  return (
    <label className="lang-switch">
      <span aria-hidden="true">🌐</span>
      <select aria-label="Language" value={getLang()} onChange={(e) => changeLang(e.target.value as Lang)}>
        {LANGS.map((l) => (
          <option key={l.code} value={l.code}>
            {l.label}
          </option>
        ))}
      </select>
    </label>
  )
}
