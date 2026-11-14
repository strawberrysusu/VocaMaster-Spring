// 종이 시험지 검사 — 문항 만들기(testSheet.ts)와 Word 파일 만들기(docx.ts).
//   npm test --prefix frontend
import { loadTs } from './load-ts.mjs'

const here = (p) => new URL(p, import.meta.url)
const sheet = await loadTs(here('../src/lib/testSheet.ts'))
const docx = await loadTs(here('../src/lib/docx.ts'))

let failed = 0
let total = 0
const check = (label, cond, detail = '') => {
  total++
  if (!cond) {
    failed++
    console.error(`FAIL ${label}${detail ? ' — ' + detail : ''}`)
  }
}
const enc = new TextEncoder()
const dec = new TextDecoder()

// ── CRC32: 알려진 값으로 구현 자체를 검증 (zip이 Word에서 열리는 조건)
check('crc32 빈 값', docx.crc32(new Uint8Array()) === 0)
check('crc32 표준 예문', docx.crc32(enc.encode('The quick brown fox jumps over the lazy dog')) === 0x414fa339)

// ── 문항 만들기
const jpCards = [
  { id: 1, front: '感謝', reading: 'かんしゃ', back: '감사', starred: true },
  { id: 2, front: 'ざら', reading: null, back: '아주 흔함', starred: false },
  { id: 3, front: 'ジャンケン', reading: 'じゃんけん', back: '가위바위보', starred: true },
  { id: 4, front: '(開催が)危ぶまれる', reading: '(かいさいが)あやぶまれる', back: '(개최가) 우려되다', starred: false },
  { id: 5, front: '滅私奉公', reading: 'めっしほうこう', back: '멸사봉공 (사사로운 이익을 버리고 나라·조직을 위해 힘씀)', starred: false },
  { id: 6, front: '会議', reading: 'かいぎ', back: '회의', memo: '여럿이 모여 의논함', starred: false },
]
const enCards = [
  { id: 6, front: 'apple', reading: null, back: 'n. 사과', starred: false },
  { id: 7, front: 'bottom', reading: null, back: 'n. 바닥, 아래', starred: true },
]
const all = sheet.buildSheet(jpCards, { scope: 'all', count: 'all', shuffle: false })
check('일본어 덱 판정', all.japanese === true)
check('전체 문항 수', all.rows.length === 6)
check('번호는 1부터', all.rows.map((r) => r.no).join(',') === '1,2,3,4,5,6')
check('한자 단어는 읽기 칸', all.rows[0].needsReading === true && all.rows[4].needsReading === true)
check('가나만 있는 단어는 읽기 칸 없음', all.rows[1].needsReading === false && all.rows[2].needsReading === false)
check('예문 괄호 단어도 한자가 있으면 읽기 칸', all.rows[3].needsReading === true)
check('정답지 읽기·뜻', all.rows[0].reading === 'かんしゃ' && all.rows[0].meaning === '감사')
check('카드 id 유지', all.rows.map((r) => r.cardId).join(',') === '1,2,3,4,5,6')
check('설명 줄 전달', all.rows[5].memo === '여럿이 모여 의논함' && all.rows[0].memo === '')
check('읽기 없는 카드는 빈 문자열', all.rows[1].reading === '')

// 괄호 속 문맥의 한자는 읽기 칸을 만들지 않는다 — 시험 보는 낱말(괄호 밖)에 한자가 있을 때만
const ctx = sheet.buildSheet(
  [
    { id: 11, front: '(話が)こじれる', reading: '(はなしが)こじれる', back: '(이야기가) 꼬이다', starred: false },
    { id: 12, front: 'めきめき(上達する)', reading: 'めきめき(じょうたつする)', back: '부쩍부쩍', starred: false },
    { id: 13, front: '危ぶまれる(開催が)', reading: 'あやぶまれる', back: '우려되다', starred: false },
  ],
  { scope: 'all', count: 'all', shuffle: false },
)
check('괄호 속 문맥에만 한자가 있으면 읽기 칸 없음', !ctx.rows[0].needsReading && !ctx.rows[1].needsReading)
check('괄호 밖 낱말에 한자가 있으면 읽기 칸', ctx.rows[2].needsReading === true)

const starred = sheet.buildSheet(jpCards, { scope: 'starred', count: 'all', shuffle: false })
check('별표만', starred.rows.map((r) => r.word).join(',') === '感謝,ジャンケン')
check('poolSize 별표', sheet.poolSize(jpCards, 'starred') === 2 && sheet.poolSize(jpCards, 'all') === 6)

const limited = sheet.buildSheet(jpCards, { scope: 'all', count: 3, shuffle: false })
check('문제 수 제한', limited.rows.length === 3 && limited.rows[2].no === 3)
check('문제 수가 카드보다 많으면 카드 수만큼', sheet.buildSheet(jpCards, { scope: 'all', count: 99, shuffle: false }).rows.length === 6)

// 섞기: 고정 난수로 결과가 정해지고, 원래 배열은 건드리지 않는다
let seed = 0.123
const rnd = () => ((seed = (seed * 9301 + 49297) % 233280 / 233280), seed)
const before = jpCards.map((c) => c.front).join(',')
const shuffled = sheet.buildSheet(jpCards, { scope: 'all', count: 'all', shuffle: true }, rnd)
check('섞어도 같은 카드 6장', shuffled.rows.map((r) => r.word).sort().join(',') === all.rows.map((r) => r.word).sort().join(','))
check('섞기가 원래 배열을 바꾸지 않음', jpCards.map((c) => c.front).join(',') === before)
check('섞은 뒤에도 번호는 1부터 차례로', shuffled.rows.map((r) => r.no).join(',') === '1,2,3,4,5,6')

const en = sheet.buildSheet(enCards, { scope: 'all', count: 'all', shuffle: false })
check('영어 덱 판정', en.japanese === false && en.rows.every((r) => !r.needsReading))

check('파일 이름 정리', sheet.sheetFileName('N1 Day20/p039:040?') === 'N1 Day20 p039 040_시험지.docx', sheet.sheetFileName('N1 Day20/p039:040?'))
check('빈 제목 파일 이름', sheet.sheetFileName('  ') === '단어장_시험지.docx')
const est = sheet.estimatePages(30, true)
check('쪽수 어림 (Word 실측 30문항 = 3쪽 + 정답지 2쪽)', est.sheet === 3 && est.answerKey === 2, JSON.stringify(est))

// ── zip 읽기 (EOCD → 중앙 디렉터리 → 로컬 헤더) — Word가 여는 방식 그대로 따라간다
function readZip(bytes) {
  const v = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength)
  const eocd = bytes.length - 22
  if (v.getUint32(eocd, true) !== 0x06054b50) throw new Error('EOCD 없음')
  const count = v.getUint16(eocd + 10, true)
  let p = v.getUint32(eocd + 16, true)
  const files = {}
  for (let i = 0; i < count; i++) {
    if (v.getUint32(p, true) !== 0x02014b50) throw new Error('중앙 디렉터리 서명 오류')
    const crc = v.getUint32(p + 16, true)
    const size = v.getUint32(p + 20, true)
    const nameLen = v.getUint16(p + 28, true)
    const local = v.getUint32(p + 42, true)
    const name = dec.decode(bytes.subarray(p + 46, p + 46 + nameLen))
    if (v.getUint32(local, true) !== 0x04034b50) throw new Error('로컬 헤더 서명 오류: ' + name)
    const lNameLen = v.getUint16(local + 26, true)
    const data = bytes.subarray(local + 30 + lNameLen, local + 30 + lNameLen + size)
    files[name] = { data, crcOk: docx.crc32(data) === crc }
    p += 46 + nameLen
  }
  return files
}

const info = { deckTitle: 'N1 <테스트> & Day', subtitle: '전체 6문항', answerKey: true }
const bytes = docx.buildTestSheetDocx(all, info, new Date(2026, 10, 14, 9, 30, 0))
const files = readZip(bytes)
const names = Object.keys(files)
check('zip 항목 5개', names.length === 5, names.join(' '))
check('[Content_Types].xml 이 첫 항목', names[0] === '[Content_Types].xml')
check('필수 파일', ['_rels/.rels', 'word/document.xml', 'word/styles.xml', 'word/_rels/document.xml.rels'].every((n) => n in files))
check('모든 항목 CRC 일치', Object.values(files).every((f) => f.crcOk))

const xml = dec.decode(files['word/document.xml'].data)
const count = (s) => xml.split(s).length - 1
check('행 수 = (머리줄 + 6) × 2', count('<w:tr>') === 14, String(count('<w:tr>')))
check('머리줄은 쪽마다 반복', count('<w:tblHeader/>') === 2)
check('여는·닫는 태그 짝', count('<w:tr>') === count('</w:tr>') && count('<w:tc>') === count('</w:tc>') && count('<w:tbl>') === count('</w:tbl>'))
check('문제지 칸 높이 1.8cm', count('<w:trHeight w:val="1020"/>') === 6)
// 회색 칸: 문제지 2(ざら, ジャンケン) + 정답지 2
check('가나만 단어의 읽기 칸은 회색', count('w:fill="D9D9D9"') === 4, String(count('w:fill="D9D9D9"')))
check('정답지 앞 쪽 나눔', count('<w:br w:type="page"/>') === 1)
check('제목 이스케이프', xml.includes('N1 &lt;테스트&gt; &amp; Day 시험지'))
check('정답지에 읽기·뜻', xml.includes('かんしゃ') && xml.includes('멸사봉공 (사사로운 이익을 버리고 나라·조직을 위해 힘씀)'))
check('일본어 글꼴', xml.includes('w:eastAsia="Yu Gothic"'))
check('A4 용지·사용자 양식 여백', xml.includes('<w:pgSz w:w="11906" w:h="16838"/>') && xml.includes('w:top="1701"'))
check('점수 칸 문항 수', xml.includes('/ 6'))
check('정답지에 설명 줄 (작은 회색)', xml.includes('여럿이 모여 의논함') && xml.includes('<w:color w:val="777777"/><w:sz w:val="15"/>'))
check('문제지에는 설명 줄 없음', xml.split('<w:br w:type="page"/>')[0].indexOf('여럿이 모여 의논함') === -1)
// eslint-disable-next-line no-control-regex -- 제어 문자가 남지 않았는지 일부러 찾는다
check('XML 제어 문자 없음', !/[\u0000-\u0008\u000B\u000C\u000E-\u001F]/.test(xml))

const noKey = dec.decode(readZip(docx.buildTestSheetDocx(all, { ...info, answerKey: false }))['word/document.xml'].data)
// 화면 언어가 중국어면 제목·머리줄·뜻의 글꼴과 문서 동아시아 언어도 그 언어로 (11/14). 한국어는 맑은 고딕·ko-KR 그대로
const zhLabels = { ...docx.SHEET_DOC_LABELS_KO, font: 'Microsoft YaHei', eastAsiaLang: 'zh-CN' }
const zhFiles = readZip(docx.buildTestSheetDocx(all, { ...info, labels: zhLabels }))
const zhStyles = dec.decode(zhFiles['word/styles.xml'].data)
const zhDoc = dec.decode(zhFiles['word/document.xml'].data)
check('중국어 화면: 문서 기본 글꼴·언어', zhStyles.includes('w:eastAsia="Microsoft YaHei"') && zhStyles.includes('w:eastAsia="zh-CN"'))
check('중국어 화면: 뜻·머리줄 글꼴', zhDoc.includes('w:ascii="Microsoft YaHei"') && !zhDoc.includes('Malgun Gothic'))
check('중국어 화면: 일본어 단어는 그대로 Yu Gothic', zhDoc.includes('w:eastAsia="Yu Gothic"'))
const koStyles = dec.decode(files['word/styles.xml'].data)
check('한국어: 맑은 고딕·ko-KR', koStyles.includes('w:eastAsia="Malgun Gothic"') && koStyles.includes('w:eastAsia="ko-KR"'))
check('정답지 끄면 표 하나', noKey.split('<w:tbl>').length - 1 === 1 && !noKey.includes('w:type="page"'))

const enXml = dec.decode(readZip(docx.buildTestSheetDocx(en, { ...info, answerKey: false }))['word/document.xml'].data)
check('영어 덱은 3칸(번호·단어·뜻)', enXml.split('<w:gridCol ').length - 1 === 3 && !enXml.includes('>읽기<'))
check('xmlText 제어 문자 제거', docx.xmlText('a\u0001b<') === 'ab&lt;')

if (failed) {
  console.error(`\n${failed}/${total} 실패`)
  process.exit(1)
}
console.log(`testSheet·docx: ${total}개 검사 모두 통과`)
