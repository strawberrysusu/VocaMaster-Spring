// 테스트 러너 없이 src/lib/*.ts 를 불러오는 작은 로더 — 이미 있는 typescript로 JS로 바꿔 data: URL로 import 한다.
// 상대 경로 import는 풀 수 없으니, 불러올 .ts 파일은 다른 모듈을 값으로 import 하지 않아야 한다(import type은 지워져서 괜찮다).
import { readFileSync } from 'node:fs'
import ts from 'typescript'

export async function loadTs(url) {
  const source = readFileSync(url, 'utf8')
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2020 },
  })
  return import('data:text/javascript;base64,' + Buffer.from(outputText).toString('base64'))
}
