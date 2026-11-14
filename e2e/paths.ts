import path from 'node:path'

// 설정 파일과 테스트가 함께 쓰는 경로 (e2e 패키지는 CommonJS라 __dirname을 쓸 수 있다)
export const AUTH_FILE = path.resolve(__dirname, '.auth/user.json')
export const APP_LOG = path.resolve(__dirname, 'app.log')
