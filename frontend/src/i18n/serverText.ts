// 서버 오류 문구 번역의 순수한 부분 (ADR-056 2단계) — 사전을 받아서 번역만 한다.
// 다른 모듈을 값으로 가져오지 않아서 Node 단위 테스트(scripts/test-i18n.mjs)가 직접 불러 검사한다.
//
// · 통째로 사전에 있으면 번역
// · 숫자 같은 값이 끼는 문구('현재 1개')는 사전의 {n} 틀('현재 {n}개')과 모양을 맞춰 보고, 값을 그 자리에 넣는다
// · 입력 검사 오류('nickname: 닉네임은 1~30자, password: …')는 조각마다 — 필드 이름은 그대로
// · 모르는 문구는 그대로 둔다 — 뜻이 사라지는 것보다 한국어라도 보이는 게 낫다

type Dict = Record<string, string>

interface Template {
  re: RegExp
  key: string
  names: string[]
}

// 틀은 열쇠 목록마다 한 번만 만든다 (같은 사전이면 같은 목록)
const cache = new WeakMap<object, Template[]>()

function templatesOf(dict: Dict): Template[] {
  let list = cache.get(dict)
  if (!list) {
    list = Object.keys(dict)
      .filter((key) => key.includes('{'))
      .map((key) => {
        const names: string[] = []
        const source = key
          .split(/(\{\w+\})/)
          .map((part) => {
            const hole = /^\{(\w+)\}$/.exec(part)
            if (hole) {
              names.push(hole[1])
              return '(.+?)'
            }
            return part.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
          })
          .join('')
        return { re: new RegExp('^' + source + '$'), key, names }
      })
    cache.set(dict, list)
  }
  return list
}

function fill(text: string, vars: Record<string, string>): string {
  return text.replace(/\{(\w+)\}/g, (whole, name: string) => (name in vars ? vars[name] : whole))
}

function lookup(text: string, dict: Dict): string | null {
  if (Object.prototype.hasOwnProperty.call(dict, text)) return dict[text]
  for (const { re, key, names } of templatesOf(dict)) {
    const m = re.exec(text)
    if (m) return fill(dict[key], Object.fromEntries(names.map((name, i) => [name, m[i + 1]])))
  }
  return null
}

/** 서버가 보낸 한국어 문구를 dict(열쇠 = 한국어, 값 = 고른 언어)로 번역 */
export function translateServerMessage(message: string, dict: Dict): string {
  if (!message) return message
  const whole = lookup(message, dict)
  if (whole !== null) return whole
  return message
    .split(', ')
    .map((part) => {
      const field = /^([A-Za-z_][\w.[\]]*): (.+)$/.exec(part)
      if (field) return `${field[1]}: ${lookup(field[2], dict) ?? field[2]}`
      return lookup(part, dict) ?? part
    })
    .join(', ')
}

/** 가져오기 실패 줄 끝에 서버가 붙이는 사유 (ImportService — MAX_TEXT 255·MAX_READING 200. test-i18n이 서버 코드와 같은지 본다) */
export const TOO_LONG_SUFFIX = '  (너무 김: 단어·뜻 255자, 읽기 200자 이내)'

/**
 * 가져오기 실패 줄 — 줄 내용은 사용자가 쓴 글이라 그대로 두고, 서버가 끝에 붙인 '너무 김' 사유만 번역한다.
 * 사유가 없거나 사전에 없으면 그대로
 */
export function translateImportLine(content: string, dict: Dict): string {
  if (!content.endsWith(TOO_LONG_SUFFIX) || !Object.prototype.hasOwnProperty.call(dict, TOO_LONG_SUFFIX)) return content
  return content.slice(0, content.length - TOO_LONG_SUFFIX.length) + dict[TOO_LONG_SUFFIX]
}
