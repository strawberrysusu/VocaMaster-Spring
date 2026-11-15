// 개발 서버(Vite) 프록시 검사 — 브라우저가 개발 서버(5173)에 보내는 주소 가운데 백엔드가 받아야 하는 것이
// vite.config.ts 프록시에 다 있는가. 빠지면 'npm run dev'에서 그 주소만 404가 된다(배포 빌드는 jar 하나라 멀쩡해서 늦게 안다).
//   ① 화면 코드가 부르는 API 주소의 첫 칸 — 11/14 /rankings·/users·/folders가 빠져 있었다 (Codex 4차 검산)
//   ② 화면이 통째로 옮겨 가는 백엔드 주소 — <a href="/privacy.html">, location.href = '/oauth2/authorization/google'
//   ③ 구글 로그인에서 돌아오는 콜백 /login/oauth2/code/{이름} — 화면 코드에는 안 나오지만(스프링이 정한다) 프록시가 changeOrigin: false라
//      백엔드가 제 주소를 5173으로 알고 구글도 5173의 콜백으로 돌려보낸다. 11/16 이게 빠져 개발 서버에서 구글 로그인이 404였다 (Codex 5차 검산)
//   npm test --prefix frontend
import { readdirSync, readFileSync, statSync } from 'node:fs'

const here = (p) => new URL(p, import.meta.url)
const config = readFileSync(here('../vite.config.ts'), 'utf8')
// vite.config.ts의 목록 두 개(apiPrefixes · serverPaths)를 글자로 읽는다
function listOf(name) {
  const start = config.indexOf(name + ' = [')
  if (start < 0) return []
  const text = config.slice(start, config.indexOf(']', start))
  return [...text.matchAll(/'([/][^']+)'/g)].map((m) => m[1])
}
const apiPrefixes = listOf('apiPrefixes')
const proxied = [...apiPrefixes, ...listOf('serverPaths')]
// Vite 프록시는 '주소가 이 글자로 시작하면' 백엔드로 넘긴다
const isProxied = (path) => proxied.some((prefix) => path.startsWith(prefix))

// api('/decks…') · api<T>(`/rankings…`) · fetch('/auth…') — 글자 그대로 쓴 주소의 첫 칸
const CALL = /(?:\bapi\s*(?:<[^()]*>)?\s*\(\s*|\bfetch\s*\(\s*)[`'"](\/[\w-]+)/g
const used = new Map()   // 첫 칸 → 처음 본 파일
// 화면이 통째로 옮겨 가는 주소: 그냥 <a href="/…">(화면 안 이동인 <Link to>는 해당 없음) · location.href = '/…' · window.open('/…')
const NAV = /(?:href[ ]*=[ ]*|window[.]open[(][ ]*)[`'"]([/][^`'"?# ]+)/g
const navUsed = new Map()   // 주소 → 처음 본 파일 (/app/…은 화면 자신의 주소라 뺀다)
function walk(dir) {
  for (const name of readdirSync(dir)) {
    const path = new URL(name, dir)
    if (statSync(path).isDirectory()) walk(new URL(name + '/', dir))
    else if (/\.(ts|tsx)$/.test(name)) {
      const text = readFileSync(path, 'utf8')
      const file = path.pathname.split('/src/')[1]
      for (const m of text.matchAll(CALL)) if (!used.has(m[1])) used.set(m[1], file)
      for (const m of text.matchAll(NAV)) if (!m[1].startsWith('/app/') && !navUsed.has(m[1])) navUsed.set(m[1], file)
    }
  }
}
walk(here('../src/'))

if (apiPrefixes.length === 0 || used.size === 0 || navUsed.size === 0) {
  console.error('FAIL 프록시 목록이나 API 호출·화면 이동 주소를 하나도 못 찾았다 — 검사식이 낡았다')
  process.exit(1)
}

const problems = []
for (const [prefix, file] of used)
  if (!isProxied(prefix)) problems.push(`개발 서버 프록시에 없음: ${prefix} (${file}) → vite.config.ts apiPrefixes에 추가`)
for (const [path, file] of navUsed) {
  if (!isProxied(path)) problems.push(`개발 서버 프록시에 없음: ${path} (${file}) → vite.config.ts serverPaths에 추가`)
  // 구글 로그인을 시작하는 주소가 있으면, 구글에서 돌아오는 콜백 주소도 백엔드로 가야 한다
  const oauth = /^[/]oauth2[/]authorization[/]([A-Za-z0-9_-]+)$/.exec(path)
  if (oauth && !isProxied(`/login/oauth2/code/${oauth[1]}`))
    problems.push(`개발 서버 프록시에 없음: /login/oauth2/code/${oauth[1]} (${path}로 시작한 로그인이 돌아오는 주소) → vite.config.ts serverPaths에 '/login/oauth2' 추가`)
}
if (problems.length) {
  for (const line of problems) console.error('FAIL ' + line)
  process.exit(1)
}
console.log(`proxy: API 주소 ${used.size}종 · 백엔드로 가는 화면 주소 ${navUsed.size}종 · 구글 로그인 콜백 모두 개발 서버 프록시에 있음`)
