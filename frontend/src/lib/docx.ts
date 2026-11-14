// 시험지 Word(.docx) 파일 만들기 — 라이브러리 없이 WordprocessingML XML 몇 개를 무압축 zip으로 묶는다.
// .docx = zip 안에 [Content_Types].xml · _rels/.rels · word/document.xml · word/styles.xml (+ rels)
// 양식은 사용자가 보내 준 문서1.docx를 실측해서 맞췄다: A4(11906×16838 twip), 여백 위 1701·나머지 1440,
// 표 3칸 같은 너비, 칸 높이 1020 twip(1.8cm), 표 테두리 실선. (1 twip = 1/20 pt = 1/1440 inch)
// 여기서 더한 것: 맨 앞 좁은 번호 칸, 쪽마다 반복되는 머리줄, 제목·날짜·점수 줄, 맨 뒤 정답지.
// scripts/test-test-sheet.mjs 가 zip 구조·CRC·행 수를 검사한다.
import type { SheetModel, SheetRow } from './testSheet'

// ───────────────────────── zip (무압축, store) ─────────────────────────

const CRC_TABLE = (() => {
  const t = new Uint32Array(256)
  for (let n = 0; n < 256; n++) {
    let c = n
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
    t[n] = c >>> 0
  }
  return t
})()

export function crc32(data: Uint8Array): number {
  let c = 0xffffffff
  for (let i = 0; i < data.length; i++) c = CRC_TABLE[(c ^ data[i]) & 0xff] ^ (c >>> 8)
  return (c ^ 0xffffffff) >>> 0
}

export interface ZipEntry {
  name: string
  data: Uint8Array
}

/** 무압축 zip. 이름은 ASCII만 쓴다(docx 내부 경로). date는 테스트에서 고정하려고 받는다. */
export function zipStore(entries: ZipEntry[], date: Date = new Date()): Uint8Array<ArrayBuffer> {
  const enc = new TextEncoder()
  const dosTime = (date.getHours() << 11) | (date.getMinutes() << 5) | Math.floor(date.getSeconds() / 2)
  const dosDate = ((Math.max(1980, date.getFullYear()) - 1980) << 9) | ((date.getMonth() + 1) << 5) | date.getDate()
  const metas = entries.map((e) => ({ name: enc.encode(e.name), data: e.data, crc: crc32(e.data) }))
  const localSize = metas.reduce((s, m) => s + 30 + m.name.length + m.data.length, 0)
  const centralSize = metas.reduce((s, m) => s + 46 + m.name.length, 0)
  const out = new Uint8Array(new ArrayBuffer(localSize + centralSize + 22))
  const v = new DataView(out.buffer)
  let p = 0
  const offsets: number[] = []
  for (const m of metas) {
    offsets.push(p)
    v.setUint32(p, 0x04034b50, true)
    v.setUint16(p + 4, 20, true) // 필요한 버전 2.0
    v.setUint16(p + 6, 0, true) // 플래그
    v.setUint16(p + 8, 0, true) // 0 = 무압축
    v.setUint16(p + 10, dosTime, true)
    v.setUint16(p + 12, dosDate, true)
    v.setUint32(p + 14, m.crc, true)
    v.setUint32(p + 18, m.data.length, true)
    v.setUint32(p + 22, m.data.length, true)
    v.setUint16(p + 26, m.name.length, true)
    v.setUint16(p + 28, 0, true)
    out.set(m.name, p + 30)
    out.set(m.data, p + 30 + m.name.length)
    p += 30 + m.name.length + m.data.length
  }
  const centralStart = p
  metas.forEach((m, i) => {
    v.setUint32(p, 0x02014b50, true)
    v.setUint16(p + 4, 20, true)
    v.setUint16(p + 6, 20, true)
    v.setUint16(p + 8, 0, true)
    v.setUint16(p + 10, 0, true)
    v.setUint16(p + 12, dosTime, true)
    v.setUint16(p + 14, dosDate, true)
    v.setUint32(p + 16, m.crc, true)
    v.setUint32(p + 20, m.data.length, true)
    v.setUint32(p + 24, m.data.length, true)
    v.setUint16(p + 28, m.name.length, true)
    v.setUint16(p + 30, 0, true)
    v.setUint16(p + 32, 0, true)
    v.setUint16(p + 34, 0, true)
    v.setUint16(p + 36, 0, true)
    v.setUint32(p + 38, 0, true)
    v.setUint32(p + 42, offsets[i], true)
    out.set(m.name, p + 46)
    p += 46 + m.name.length
  })
  v.setUint32(p, 0x06054b50, true)
  v.setUint16(p + 4, 0, true)
  v.setUint16(p + 6, 0, true)
  v.setUint16(p + 8, metas.length, true)
  v.setUint16(p + 10, metas.length, true)
  v.setUint32(p + 12, p - centralStart, true)
  v.setUint32(p + 16, centralStart, true)
  v.setUint16(p + 20, 0, true)
  return out
}

// ───────────────────────── WordprocessingML ─────────────────────────

const W_NS = 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'
const XML_HEAD = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n'

/** XML 글자 이스케이프 + XML 1.0에 못 넣는 제어 문자 제거 */
export function xmlText(s: string): string {
  return s
    // eslint-disable-next-line no-control-regex -- XML 1.0에 못 넣는 제어 문자를 일부러 지운다
    .replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F￾￿]/g, '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

const PAGE = { w: 11906, h: 16838, top: 1701, right: 1440, bottom: 1440, left: 1440 }
const TABLE_W = PAGE.w - PAGE.left - PAGE.right // 9026
const NO_COL = 620
const ROW_H = 1020 // 문제지 칸 높이 1.8cm (사용자 양식)
const ANSWER_ROW_H = 420 // 정답지는 촘촘하게
const GRAY = 'D9D9D9'
const HEAD_FILL = 'F2F2F2'
const JP_FONT = 'Yu Gothic'
const KO_FONT = 'Malgun Gothic'

interface RunStyle {
  sizeHalfPt: number
  bold?: boolean
  japanese?: boolean
  color?: string
}

function run(text: string, st: RunStyle): string {
  const font = st.japanese ? JP_FONT : KO_FONT
  const rPr =
    `<w:rFonts w:ascii="${font}" w:eastAsia="${font}" w:hAnsi="${font}" w:cs="${font}"/>` +
    (st.bold ? '<w:b/><w:bCs/>' : '') +
    (st.color ? `<w:color w:val="${st.color}"/>` : '') +
    `<w:sz w:val="${st.sizeHalfPt}"/><w:szCs w:val="${st.sizeHalfPt}"/>` +
    (st.japanese ? '<w:lang w:eastAsia="ja-JP"/>' : '')
  return `<w:r><w:rPr>${rPr}</w:rPr><w:t xml:space="preserve">${xmlText(text)}</w:t></w:r>`
}

function para(runs: string, align: 'left' | 'center' | 'right' = 'left', spacingAfter = 0, keepNext = false): string {
  const pPr =
    (keepNext ? '<w:keepNext/>' : '') +
    `<w:spacing w:after="${spacingAfter}" w:line="240" w:lineRule="auto"/>` +
    (align === 'left' ? '' : `<w:jc w:val="${align}"/>`)
  return `<w:p><w:pPr>${pPr}</w:pPr>${runs}</w:p>`
}

function cell(width: number, content: string, opts: { fill?: string; align?: 'left' | 'center' } = {}): string {
  const tcPr =
    `<w:tcW w:w="${width}" w:type="dxa"/>` +
    (opts.fill ? `<w:shd w:val="clear" w:color="auto" w:fill="${opts.fill}"/>` : '') +
    '<w:vAlign w:val="center"/>'
  return `<w:tc><w:tcPr>${tcPr}</w:tcPr>${para(content, opts.align ?? 'left')}</w:tc>`
}

function row(cells: string, height: number, header = false): string {
  // trPr 자식 순서는 스키마대로: cantSplit → trHeight → tblHeader
  const trPr = `<w:cantSplit/><w:trHeight w:val="${height}"/>` + (header ? '<w:tblHeader/>' : '')
  return `<w:tr><w:trPr>${trPr}</w:trPr>${cells}</w:tr>`
}

function table(widths: number[], rows: string): string {
  const border = (side: string) => `<w:${side} w:val="single" w:sz="4" w:space="0" w:color="000000"/>`
  const tblPr =
    `<w:tblW w:w="${widths.reduce((a, b) => a + b, 0)}" w:type="dxa"/>` +
    `<w:tblBorders>${['top', 'left', 'bottom', 'right', 'insideH', 'insideV'].map(border).join('')}</w:tblBorders>` +
    '<w:tblLayout w:type="fixed"/>' +
    '<w:tblLook w:val="04A0" w:firstRow="1" w:lastRow="0" w:firstColumn="1" w:lastColumn="0" w:noHBand="0" w:noVBand="1"/>'
  const grid = widths.map((w) => `<w:gridCol w:w="${w}"/>`).join('')
  return `<w:tbl><w:tblPr>${tblPr}</w:tblPr><w:tblGrid>${grid}</w:tblGrid>${rows}</w:tbl>`
}

/** 칸 너비: 번호 칸을 뺀 나머지를 사용자 양식처럼 같은 너비로 나눈다 */
function columnWidths(japanese: boolean): number[] {
  const rest = TABLE_W - NO_COL
  if (japanese) {
    const a = Math.floor(rest / 3)
    return [NO_COL, a, a, rest - 2 * a]
  }
  const a = Math.floor(rest / 2)
  return [NO_COL, a, rest - a]
}

function headerRow(widths: number[], japanese: boolean): string {
  const labels = japanese ? ['번호', '단어', '읽기', '뜻'] : ['번호', '단어', '뜻']
  const st: RunStyle = { sizeHalfPt: 20, bold: true }
  return row(labels.map((l, i) => cell(widths[i], run(l, st), { fill: HEAD_FILL, align: 'center' })).join(''), 420, true)
}

function questionRow(r: SheetRow, widths: number[], japanese: boolean): string {
  const no = cell(widths[0], run(String(r.no), { sizeHalfPt: 20, color: '555555' }), { align: 'center' })
  const word = cell(widths[1], run(r.word, { sizeHalfPt: japanese ? 32 : 28, japanese }), { align: 'center' })
  if (!japanese) return row(no + word + cell(widths[2], ''), ROW_H)
  // 한자가 없는 단어(가나만)는 읽기를 쓸 필요가 없어 회색으로 막아 둔다
  const reading = cell(widths[2], '', r.needsReading ? {} : { fill: GRAY })
  return row(no + word + reading + cell(widths[3], ''), ROW_H)
}

function answerRow(r: SheetRow, widths: number[], japanese: boolean): string {
  const small: RunStyle = { sizeHalfPt: 19 }
  const no = cell(widths[0], run(String(r.no), { sizeHalfPt: 18, color: '555555' }), { align: 'center' })
  const word = cell(widths[1], run(r.word, { sizeHalfPt: 21, japanese }), { align: 'center' })
  const meaning = (w: number) => cell(w, run(r.meaning, small))
  if (!japanese) return row(no + word + meaning(widths[2]), ANSWER_ROW_H)
  const reading = r.needsReading
    ? cell(widths[2], run(r.reading || '—', { sizeHalfPt: 19, japanese: true }))
    : cell(widths[2], '', { fill: GRAY })
  return row(no + word + reading + meaning(widths[3]), ANSWER_ROW_H)
}

export interface SheetDocInfo {
  deckTitle: string
  /** 문제지 맨 위 부제 — 예: "⭐ 별표 30문항 · 순서 섞음" */
  subtitle: string
  answerKey: boolean
}

export function testSheetDocumentXml(model: SheetModel, info: SheetDocInfo): string {
  const widths = columnWidths(model.japanese)
  const n = model.rows.length
  const title = para(run(`${info.deckTitle} 시험지`, { sizeHalfPt: 30, bold: true, japanese: model.japanese }), 'left', 60, true)
  const meta = para(
    run(info.subtitle, { sizeHalfPt: 18, color: '666666' }) +
      run(`        날짜 ______________     점수 ______ / ${n}`, { sizeHalfPt: 20 }),
    'left',
    160,
    true,
  )
  const qTable = table(
    widths,
    headerRow(widths, model.japanese) + model.rows.map((r) => questionRow(r, widths, model.japanese)).join(''),
  )
  let body = title + meta + qTable
  if (info.answerKey) {
    const pageBreak = '<w:p><w:r><w:br w:type="page"/></w:r></w:p>'
    const aTitle = para(run(`${info.deckTitle} 정답`, { sizeHalfPt: 26, bold: true, japanese: model.japanese }), 'left', 120, true)
    const aTable = table(
      widths,
      headerRow(widths, model.japanese) + model.rows.map((r) => answerRow(r, widths, model.japanese)).join(''),
    )
    body += pageBreak + aTitle + aTable
  }
  // 표로 끝나는 문서는 Word가 마지막에 빈 문단을 요구한다
  body += '<w:p/>'
  const sect =
    `<w:sectPr><w:pgSz w:w="${PAGE.w}" w:h="${PAGE.h}"/>` +
    `<w:pgMar w:top="${PAGE.top}" w:right="${PAGE.right}" w:bottom="${PAGE.bottom}" w:left="${PAGE.left}" w:header="851" w:footer="992" w:gutter="0"/>` +
    '<w:cols w:space="425"/><w:docGrid w:linePitch="360"/></w:sectPr>'
  return `${XML_HEAD}<w:document xmlns:w="${W_NS}"><w:body>${body}${sect}</w:body></w:document>`
}

const CONTENT_TYPES =
  XML_HEAD +
  '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">' +
  '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>' +
  '<Default Extension="xml" ContentType="application/xml"/>' +
  '<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>' +
  '<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>' +
  '</Types>'

const ROOT_RELS =
  XML_HEAD +
  '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">' +
  '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>' +
  '</Relationships>'

const DOC_RELS =
  XML_HEAD +
  '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">' +
  '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>' +
  '</Relationships>'

const STYLES =
  XML_HEAD +
  `<w:styles xmlns:w="${W_NS}">` +
  '<w:docDefaults>' +
  `<w:rPrDefault><w:rPr><w:rFonts w:ascii="${KO_FONT}" w:eastAsia="${KO_FONT}" w:hAnsi="${KO_FONT}" w:cs="${KO_FONT}"/>` +
  '<w:sz w:val="22"/><w:szCs w:val="22"/><w:lang w:val="en-US" w:eastAsia="ko-KR" w:bidi="ar-SA"/></w:rPr></w:rPrDefault>' +
  '<w:pPrDefault><w:pPr><w:spacing w:after="0" w:line="240" w:lineRule="auto"/></w:pPr></w:pPrDefault>' +
  '</w:docDefaults>' +
  '<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:qFormat/></w:style>' +
  '<w:style w:type="table" w:default="1" w:styleId="TableNormal"><w:name w:val="Normal Table"/><w:uiPriority w:val="99"/><w:semiHidden/><w:unhideWhenUsed/>' +
  '<w:tblPr><w:tblInd w:w="0" w:type="dxa"/><w:tblCellMar><w:top w:w="0" w:type="dxa"/><w:left w:w="108" w:type="dxa"/><w:bottom w:w="0" w:type="dxa"/><w:right w:w="108" w:type="dxa"/></w:tblCellMar></w:tblPr></w:style>' +
  '</w:styles>'

export const DOCX_MIME = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'

/** 시험지 .docx 바이트 */
export function buildTestSheetDocx(model: SheetModel, info: SheetDocInfo, date: Date = new Date()): Uint8Array<ArrayBuffer> {
  const enc = new TextEncoder()
  return zipStore(
    [
      { name: '[Content_Types].xml', data: enc.encode(CONTENT_TYPES) },
      { name: '_rels/.rels', data: enc.encode(ROOT_RELS) },
      { name: 'word/document.xml', data: enc.encode(testSheetDocumentXml(model, info)) },
      { name: 'word/_rels/document.xml.rels', data: enc.encode(DOC_RELS) },
      { name: 'word/styles.xml', data: enc.encode(STYLES) },
    ],
    date,
  )
}
