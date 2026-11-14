// 개발 서버(Vite) 프록시 검사 — 화면 코드가 부르는 API 주소의 첫 칸이 vite.config.ts의 apiPrefixes에 다 있는가 (11/14).
// 빠지면 'npm run dev'에서 그 API만 404가 된다(배포 빌드는 jar 하나라 멀쩡해서 늦게 안다).
// 실제로 /rankings·/users·/folders가 빠져 있었다 (Codex 4차 검산 — 랭킹이 개발 서버에서 404)
//   npm test --prefix frontend
import { readdirSync, readFileSync, statSync } from 'node:fs'

const here = (p) => new URL(p, import.meta.url)
const config = readFileSync(here('../vite.config.ts'), 'utf8')
const listText = /apiPrefixes\s*=\s*\[([^\]]*)\]/.exec(config)?.[1] ?? ''
const proxied = new Set([...listText.matchAll(/'(\/[\w-]+)'/g)].map((m) => m[1]))

// api('/decks…') · api<T>(`/rankings…`) · fetch('/auth…') — 글자 그대로 쓴 주소의 첫 칸
const CALL = /(?:\bapi\s*(?:<[^()]*>)?\s*\(\s*|\bfetch\s*\(\s*)[`'"](\/[\w-]+)/g
const used = new Map()   // 첫 칸 → 처음 본 파일
function walk(dir) {
  for (const name of readdirSync(dir)) {
    const path = new URL(name, dir)
    if (statSync(path).isDirectory()) walk(new URL(name + '/', dir))
    else if (/\.(ts|tsx)$/.test(name)) {
      for (const m of readFileSync(path, 'utf8').matchAll(CALL)) if (!used.has(m[1])) used.set(m[1], path.pathname.split('/src/')[1])
    }
  }
}
walk(here('../src/'))

const missing = [...used].filter(([prefix]) => !proxied.has(prefix))
if (proxied.size === 0 || used.size === 0) {
  console.error('FAIL 프록시 목록이나 API 호출을 하나도 못 찾았다 — 검사식이 낡았다')
  process.exit(1)
}
if (missing.length) {
  for (const [prefix, file] of missing) console.error(`FAIL 개발 서버 프록시에 없음: ${prefix} (${file}) → vite.config.ts apiPrefixes에 추가`)
  process.exit(1)
}
console.log(`proxy: API 주소 ${used.size}종 모두 개발 서버 프록시에 있음`)
