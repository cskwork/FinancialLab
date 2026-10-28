> **Superseded (2026-10-28).** This is a pre-implementation plan kept for history. Current documentation:
> [architecture](../architecture.md), [backend README](../../backend/README.md), [HTTP contract](../../contract/README.md),
> [ADRs](../adr/README.md).
>
> Backend operation (PostgreSQL without Docker, profiles, signing keys) is in
> [backend/README.md](../../backend/README.md). Some fixes below call APIs that do not exist, such as
> `authStore.refreshToken()`; there are no refresh tokens.

# FinLab 문제 해결 가이드

## 목차

1. [설치 및 실행 문제](#설치-및-실행-문제)
2. [빌드 오류](#빌드-오류)
3. [런타임 오류](#런타임-오류)
4. [데이터베이스 문제](#데이터베이스-문제)
5. [API 통신 문제](#api-통신-문제)
6. [성능 문제](#성능-문제)
7. [브라우저 호환성](#브라우저-호환성)
8. [배포 문제](#배포-문제)

---

## 설치 및 실행 문제

### npm install 실패

**증상**
```bash
npm ERR! code ERESOLVE
npm ERR! ERESOLVE unable to resolve dependency tree
```

**원인**
- 의존성 버전 충돌
- package-lock.json 손상

**해결 방법**

```bash
# 1. 캐시 정리
npm cache clean --force

# 2. node_modules 및 lockfile 삭제
rm -rf node_modules package-lock.json

# 3. 재설치
npm install

# 또는 --legacy-peer-deps 플래그 사용
npm install --legacy-peer-deps
```

---

### 개발 서버가 시작되지 않음

**증상**
```bash
npm run dev
# 에러 또는 무반응
```

**해결 방법**

1. **포트 충돌 확인**
   ```bash
   # 5173 포트 사용 중인 프로세스 확인
   lsof -i :5173  # macOS/Linux
   netstat -ano | findstr :5173  # Windows

   # 프로세스 종료 또는 다른 포트 사용
   # vite.config.ts
   server: {
     port: 3000  # 다른 포트로 변경
   }
   ```

2. **Node 버전 확인**
   ```bash
   node --version

   # Node 16 사용 시 경고는 무시해도 됨
   # Node 20+ 권장
   nvm install 20
   nvm use 20
   ```

3. **권한 문제 (Linux/macOS)**
   ```bash
   sudo npm run dev  # 권장하지 않음

   # 또는 nvm으로 Node 재설치 (권한 문제 해결)
   ```

---

### TypeScript 에러

**증상**
```
Cannot find module '@/components/...' or its corresponding type declarations
```

**원인**
- Path alias 설정 누락
- VS Code가 TypeScript 버전을 인식하지 못함

**해결 방법**

1. **tsconfig.json 확인**
   ```json
   {
     "compilerOptions": {
       "paths": {
         "@/*": ["./src/*"]
       }
     }
   }
   ```

2. **VS Code 재시작**
   - `Cmd/Ctrl + Shift + P`
   - "TypeScript: Restart TS Server"

3. **workspace 버전 사용**
   - `Cmd/Ctrl + Shift + P`
   - "TypeScript: Select TypeScript Version"
   - "Use Workspace Version" 선택

---

## 빌드 오류

### Vite 빌드 실패

**증상**
```bash
npm run build
# Build failed with errors
```

**일반적인 원인 및 해결**

1. **타입 에러**
   ```bash
   # 타입 체크만 실행
   npm run vue-tsc --noEmit

   # 타입 에러 수정 후 재빌드
   ```

2. **메모리 부족**
   ```bash
   # Node 메모리 증가
   NODE_OPTIONS=--max-old-space-size=4096 npm run build
   ```

3. **환경 변수 누락**
   ```bash
   # .env.production 파일 확인
   VITE_API_BASE_URL=https://api.finlab.app
   ```

---

### "Cannot find module" 에러 (빌드 시)

**증상**
```
[vite]: Rollup failed to resolve import "@/utils/..." from "..."
```

**해결 방법**

1. **파일 경로 확인**
   - 대소문자 정확히 일치하는지 확인
   - 파일 확장자 포함 여부 확인

2. **vite.config.ts alias 설정**
   ```typescript
   resolve: {
     alias: {
       '@': path.resolve(__dirname, './src')
     }
   }
   ```

---

## 런타임 오류

### 빈 화면 (White Screen)

**증상**
- 페이지 로드 후 빈 화면
- 콘솔에 에러 메시지

**해결 방법**

1. **콘솔 에러 확인**
   - F12 → Console 탭
   - 에러 메시지 읽고 해당 파일 수정

2. **라우터 설정 확인**
   ```typescript
   // router.ts
   const router = createRouter({
     history: createWebHistory(import.meta.env.BASE_URL),  // BASE_URL 확인
     routes
   })
   ```

3. **BASE_URL 설정 (GitHub Pages)**
   ```typescript
   // vite.config.ts
   export default defineConfig({
     base: '/finlab/'  // 저장소 이름과 일치
   })
   ```

---

### "Hydration mismatch" 경고

**증상**
```
Hydration completed but contains mismatches
```

**원인**
- SSR/SSG 사용 시 발생 (현재 프로젝트는 CSR이므로 해당 없음)

**해결 방법**
- 이 프로젝트는 CSR이므로 발생하지 않음
- 만약 발생한다면 다른 라이브러리 충돌 가능성

---

### IndexedDB 접근 불가

**증상**
```
Failed to execute 'transaction' on 'IDBDatabase': The database connection is closing
```

**원인**
- 브라우저의 IndexedDB 용량 초과
- Private/Incognito 모드에서 제한

**해결 방법**

1. **브라우저 데이터 삭제**
   - F12 → Application → IndexedDB
   - FinLabDB 우클릭 → Delete

2. **일반 모드로 전환**
   - Private/Incognito 모드는 IndexedDB 제한

3. **코드에서 에러 처리**
   ```typescript
   try {
     await db.portfolios.add(data)
   } catch (error) {
     if (error.name === 'QuotaExceededError') {
       alert('저장 공간이 부족합니다.')
     }
   }
   ```

---

## 데이터베이스 문제

### 데이터가 사라짐

**증상**
- 로그인 후 포트폴리오가 없어짐

**원인**
- 브라우저 캐시 삭제
- IndexedDB 버전 변경 후 마이그레이션 실패

**해결 방법**

1. **시드 데이터 재로드**
   ```typescript
   import { loadSeedData } from '@/utils/seedLoader'
   await loadSeedData()
   ```

2. **백업 (사전 예방)**
   - F12 → Application → IndexedDB → FinLabDB
   - 각 테이블 Export (JSON)

3. **로컬스토리지 확인**
   ```javascript
   localStorage.getItem('BACKEND_MODE')
   // Mock 모드인지 확인
   ```

---

### 마이그레이션 실패

**증상**
```
VersionError: The requested version (2) is less than the existing version (3)
```

**원인**
- Dexie 버전 다운그레이드 시도

**해결 방법**

```typescript
// 강제 초기화 (데이터 삭제됨!)
await db.delete()
window.location.reload()

// 또는 브라우저 도구에서 수동 삭제
// F12 → Application → IndexedDB → FinLabDB → Delete
```

---

### FIFO 계산 오류

**증상**
- 보유 수량이 음수
- 평균 단가가 비정상적

**원인**
- 거래 순서 문제
- 매도 수량 > 보유 수량

**해결 방법**

1. **거래 내역 검증**
   ```typescript
   // 모든 거래를 시간순 정렬
   const sorted = transactions.sort((a, b) =>
     new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime()
   )
   ```

2. **보유 수량 재계산**
   ```typescript
   import { calculateFIFO } from '@/utils/fifoCalculator'

   const result = calculateFIFO(transactions)
   console.log(result.shares, result.avgCost)
   ```

3. **데이터 정합성 체크**
   - 매도 전에 충분한 보유량 확인
   - 테스트 코드로 검증

---

## API 통신 문제

### CORS 에러

**증상**
```
Access to fetch at 'https://api.finlab.app' from origin 'http://localhost:5173'
has been blocked by CORS policy
```

**원인**
- Backend에서 CORS 허용 안 함

**해결 방법**

1. **Backend CORS 설정 (Spring Boot 예시)**
   ```java
   @Configuration
   public class CorsConfig {
       @Bean
       public CorsConfigurationSource corsConfigurationSource() {
           CorsConfiguration config = new CorsConfiguration();
           config.setAllowedOrigins(Arrays.asList(
               "http://localhost:5173",
               "https://yourusername.github.io"
           ));
           config.setAllowedMethods(Arrays.asList("*"));
           config.setAllowCredentials(true);
           // ...
           return source;
       }
   }
   ```

2. **Vite Proxy 사용 (개발 환경)**
   ```typescript
   // vite.config.ts
   server: {
     proxy: {
       '/api': {
         target: 'https://api.finlab.app',
         changeOrigin: true,
         rewrite: (path) => path.replace(/^\/api/, '')
       }
     }
   }

   // API 호출 시
   fetch('/api/portfolios')  // https://api.finlab.app/portfolios로 프록시
   ```

---

### 401 Unauthorized

**증상**
```
API request failed: 401 Unauthorized
```

**원인**
- JWT 토큰 만료
- 토큰 없이 요청

**해결 방법**

1. **로그인 재시도**
   ```typescript
   const authStore = useAuthStore()
   await authStore.login('demo@finlab.app')
   ```

2. **토큰 확인**
   ```typescript
   console.log(authStore.token)
   // null이면 로그인 필요
   ```

3. **자동 재로그인 구현**
   ```typescript
   if (response.status === 401) {
     await authStore.refreshToken()
     // 또는 로그인 페이지로 리다이렉트
     router.push('/login')
   }
   ```

---

### 네트워크 타임아웃

**증상**
```
Failed to fetch: Network request failed
```

**해결 방법**

1. **인터넷 연결 확인**

2. **Backend URL 확인**
   ```typescript
   const configStore = useConfigStore()
   console.log(configStore.backendUrl)
   ```

3. **Timeout 설정 추가**
   ```typescript
   const controller = new AbortController()
   const timeoutId = setTimeout(() => controller.abort(), 5000)

   fetch(url, { signal: controller.signal })
     .finally(() => clearTimeout(timeoutId))
   ```

---

## 성능 문제

### 페이지 로딩이 느림

**증상**
- 초기 로딩 5초 이상

**해결 방법**

1. **Lighthouse 분석**
   - F12 → Lighthouse 탭
   - "Generate report"
   - 권장사항 확인

2. **Code splitting 확인**
   ```typescript
   // 라우트 lazy loading 적용 여부
   const DashboardPage = () => import('@/pages/DashboardPage.vue')
   ```

3. **Bundle 크기 분석**
   ```bash
   npm run build
   # stats.html 확인
   ```

---

### UI가 버벅임 (Janky)

**증상**
- 스크롤이 부드럽지 않음
- 차트 렌더링 시 멈춤

**해결 방법**

1. **Web Worker 사용**
   ```typescript
   // CPU 집약적 작업은 Worker로 이동
   const worker = new BacktestWorker()
   worker.postMessage(data)
   ```

2. **대량 데이터 가상화**
   ```vue
   <!-- 수천 개 행 렌더링 시 -->
   <VirtualScroller :items="transactions" :item-height="50">
     <template #item="{ item }">
       <TransactionRow :data="item" />
     </template>
   </VirtualScroller>
   ```

3. **Debounce/Throttle 적용**
   ```typescript
   import { debounce } from '@/utils/debounce'

   const handleSearch = debounce((query: string) => {
     // 검색 로직
   }, 300)
   ```

---

## 브라우저 호환성

### Safari에서 작동하지 않음

**증상**
- Safari에서 빈 화면 또는 기능 미작동

**해결 방법**

1. **Polyfill 확인**
   ```typescript
   // vite.config.ts
   build: {
     target: 'es2015'  // 구형 브라우저 지원
   }
   ```

2. **Safari 개발자 도구 확인**
   - Safari → 설정 → 고급 → "메뉴 막대에서 개발자용 메뉴 보기"
   - 개발 → 웹 속성 보기 → 콘솔

3. **Private Browsing 문제**
   - Safari Private Browsing은 IndexedDB 제한
   - 일반 모드 사용 권장

---

### IE11 지원 불가

**참고**
- 이 프로젝트는 IE11을 지원하지 않습니다
- 최신 브라우저(Chrome, Firefox, Safari, Edge) 사용 권장

---

## 배포 문제

### GitHub Pages 404 에러

**증상**
- 배포 후 페이지 새로고침 시 404

**원인**
- SPA 라우팅 vs 정적 서버

**해결 방법**

1. **404.html 생성**
   ```html
   <!-- public/404.html -->
   <!DOCTYPE html>
   <html>
   <head>
     <meta charset="utf-8">
     <script>
       sessionStorage.redirect = location.href;
     </script>
     <meta http-equiv="refresh" content="0;URL='/finlab'">
   </head>
   </html>
   ```

2. **index.html에서 처리**
   ```html
   <script>
     (function(){
       var redirect = sessionStorage.redirect;
       delete sessionStorage.redirect;
       if (redirect && redirect !== location.href) {
         history.replaceState(null, null, redirect);
       }
     })();
   </script>
   ```

---

### Vercel/Netlify 환경 변수 적용 안 됨

**증상**
- 프로덕션 빌드에서 환경 변수가 undefined

**해결 방법**

1. **Dashboard에서 설정**
   - Vercel: Settings → Environment Variables
   - Netlify: Site settings → Build & deploy → Environment

2. **Redeploy 필요**
   - 환경 변수 추가 후 반드시 재배포

3. **빌드 로그 확인**
   ```
   Deploying...
   Environment variables:
   - VITE_API_BASE_URL: https://api.finlab.app
   ```

---

### 빌드된 파일 크기가 너무 큼

**증상**
- dist/ 폴더가 10MB 이상

**해결 방법**

1. **Tree shaking 확인**
   ```typescript
   // Bad
   import _ from 'lodash'

   // Good
   import { debounce } from 'lodash-es'
   ```

2. **이미지 최적화**
   ```bash
   # 이미지를 WebP로 변환
   npm i -D vite-plugin-imagemin
   ```

3. **불필요한 의존성 제거**
   ```bash
   npm prune
   npx depcheck  # 사용하지 않는 패키지 확인
   ```

---

## 추가 지원

위 문제 해결 방법으로 해결되지 않는 경우:

1. **GitHub Issues 검색**
   - https://github.com/yourusername/finlab/issues
   - 유사한 이슈가 있는지 확인

2. **새 이슈 생성**
   - 문제 재현 단계
   - 에러 메시지 전문
   - 환경 정보 (OS, 브라우저, Node 버전)

3. **이메일 문의**
   - support@finlab.app
   - 스크린샷 첨부 권장

---

**관련 문서**
- [Configuration Guide](CONFIGURATION-GUIDE.md)
- [Developer Guide](DEVELOPER-GUIDE.md)
- [User Guide](../USER-GUIDE.md)
