// 브라우저 내장 TTS (ADR-017 개정, 2026-08-23): 서버·Redis·외부 API 없음.
// 데스크톱 Chrome엔 "Google US English" 같은 구글 네트워크 음성이, Edge엔 Microsoft Natural 음성이 내장돼 있어
// 비용 0·약관 문제 0으로 구글 계열 목소리를 쓴다. 공개 서비스로 커지면 speak() 한 곳만 공식 Cloud TTS로 교체.

const VOICE_PRIORITY = ['Google', 'Natural', 'Microsoft'] // 이름에 포함되면 우선 (앞일수록 우선)

let voicesCache: SpeechSynthesisVoice[] = []

function loadVoices(): SpeechSynthesisVoice[] {
  if (typeof speechSynthesis === 'undefined') return []
  const v = speechSynthesis.getVoices()
  if (v.length) voicesCache = v
  return voicesCache
}

if (typeof speechSynthesis !== 'undefined') {
  loadVoices()
  speechSynthesis.onvoiceschanged = loadVoices // Chrome은 목록을 비동기로 채움
}

export function isTtsSupported(): boolean {
  return typeof speechSynthesis !== 'undefined' && typeof SpeechSynthesisUtterance !== 'undefined'
}

// 텍스트로 언어 판별 — 덱에 언어 필드가 없어서 휴리스틱 (가나·한자 → 일본어, 한글 → 한국어, 그 외 → 영어)
export function detectLang(text: string): string {
  if (/[぀-ヿ一-鿿]/.test(text)) return 'ja-JP'
  if (/[가-힯]/.test(text)) return 'ko-KR'
  return 'en-US'
}

function pickVoice(lang: string): SpeechSynthesisVoice | undefined {
  const base = lang.slice(0, 2)
  const candidates = loadVoices().filter((v) => v.lang.toLowerCase().startsWith(base))
  for (const key of VOICE_PRIORITY) {
    const hit = candidates.find((v) => v.name.includes(key))
    if (hit) return hit
  }
  return candidates[0]
}

export function speak(text: string, lang = detectLang(text)): void {
  if (!isTtsSupported() || !text.trim()) return
  speechSynthesis.cancel() // 연타 시 겹침 방지
  const u = new SpeechSynthesisUtterance(text)
  u.lang = lang
  const voice = pickVoice(lang)
  if (voice) u.voice = voice
  u.rate = 0.95
  speechSynthesis.speak(u)
}
