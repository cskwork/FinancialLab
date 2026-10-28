Planned backend — not implemented in this repository.

Historical design/reference material; it is not a description of the shipped frontend. See [current architecture](../architecture.md).

# FinLab 설정 가이드

## 목차

1. [개발 환경 설정](#개발-환경-설정)
2. [환경 변수](#환경-변수)
3. [Backend 모드 설정](#backend-모드-설정)
4. [데이터베이스 설정](#데이터베이스-설정)
5. [빌드 설정](#빌드-설정)
6. [배포 설정](#배포-설정)
7. [보안 설정](#보안-설정)
8. [성능 최적화](#성능-최적화)

---

## 개발 환경 설정

### Node.js 버전 관리

**nvm 사용 (권장)**

```bash
# .nvmrc 파일 생성
echo "20" > .nvmrc

# Node.js 설치 및 사용
nvm install
nvm use

# 버전 확인
node --version  # v20.x.x
```

**Node.js 16 사용 시**

Vite 7은 공식적으로 Node 20+를 요구하지만, Polyfill로 Node 16에서도 실행 가능:

```bash
# Polyfill이 이미 프로젝트에 적용되어 있음
npm run dev  # 경고가 표시되지만 정상 작동
```

### Package Manager

**npm (기본)**
```bash
npm install
npm run dev
```

**yarn**
```bash
# yarn 사용 시 lockfile 생성
yarn install
yarn dev
```

**pnpm (선택)**
```bash
pnpm install
pnpm dev
```

**주의**: 팀 프로젝트에서는 하나의 Package Manager로 통일하세요.

---

## 환경 변수

### 환경 변수 파일

Vite는 `.env` 파일을 사용하여 환경 변수를 관리합니다.

**파일 우선순위**
```
.env                 # 모든 환경
.env.local           # 모든 환경 (Git 무시, 로컬 전용)
.env.development     # 개발 환경
.env.production      # 프로덕션 환경
```

### 환경 변수 정의

**`.env.development` 예시**
```bash
# Backend API URL
VITE_API_BASE_URL=http://localhost:8080

# Backend 모드 (mock | live)
VITE_BACKEND_MODE=mock

# 앱 이름
VITE_APP_NAME=FinLab

# 디버그 모드
VITE_DEBUG=true

# Feature Flags
VITE_FEATURE_BACKTEST=true
VITE_FEATURE_GOALS=true
```

**`.env.production` 예시**
```bash
VITE_API_BASE_URL=https://api.finlab.app
VITE_BACKEND_MODE=live
VITE_APP_NAME=FinLab
VITE_DEBUG=false
VITE_FEATURE_BACKTEST=true
VITE_FEATURE_GOALS=true
```

### 환경 변수 사용

**TypeScript에서 접근**

```typescript
// vite-env.d.ts에 타입 정의
interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
  readonly VITE_BACKEND_MODE: 'mock' | 'live'
  readonly VITE_DEBUG: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

// 사용
const apiUrl = import.meta.env.VITE_API_BASE_URL
const isDebug = import.meta.env.VITE_DEBUG === 'true'
```

**주의사항**
- 환경 변수는 반드시 `VITE_` 접두사 필요
- `.env.local`은 `.gitignore`에 추가
- 민감한 정보는 환경 변수에 저장하지 말 것 (클라이언트 노출됨)

---

## Backend 모드 설정

### Mock 모드 (기본)

IndexedDB를 사용한 완전한 오프라인 모드

**설정 방법**

1. **UI에서 설정**
   - 설정 페이지 접속
   - "Backend 모드" → "Mock" 선택
   - 자동으로 `localStorage`에 저장

2. **코드에서 설정**
   ```typescript
   import { useConfigStore } from '@/stores/configStore'

   const configStore = useConfigStore()
   configStore.setBackendMode('mock')
   ```

3. **환경 변수로 설정**
   ```bash
   # .env
   VITE_BACKEND_MODE=mock
   ```

**특징**
- 네트워크 요청 없음
- 브라우저 IndexedDB에 모든 데이터 저장
- 빠른 응답 시간
- 오프라인 작동
- 데이터는 브라우저에만 존재

### Live 모드

실제 Backend API를 호출하는 모드

**설정 방법**

1. **UI에서 설정**
   - 설정 페이지 접속
   - "Backend 모드" → "Live" 선택
   - Backend URL 입력: `https://api.finlab.app`
   - "저장" 버튼 클릭

2. **코드에서 설정**
   ```typescript
   const configStore = useConfigStore()
   configStore.setBackendMode('live')
   configStore.setBackendUrl('https://api.finlab.app')
   ```

3. **환경 변수로 설정**
   ```bash
   # .env
   VITE_BACKEND_MODE=live
   VITE_API_BASE_URL=https://api.finlab.app
   ```

**특징**
- 실제 HTTP 요청
- 서버 측 데이터 저장
- 인터넷 연결 필요
- 여러 클라이언트 간 데이터 동기화 가능

### 하이브리드 모드 (고급)

Mock과 Live를 혼합하여 사용

```typescript
// 읽기는 캐시(IndexedDB), 쓰기는 서버
async function getPortfolios() {
  const cached = await portfolioRepo.findAll()

  if (cached.length > 0 && !isStale(cached)) {
    return cached
  }

  // 서버에서 최신 데이터 가져오기
  const fresh = await apiClient.get('/portfolios')
  await portfolioRepo.bulkPut(fresh)

  return fresh
}
```

---

## 데이터베이스 설정

### Dexie.js 설정

**스키마 버전 관리**

`src/services/db/schema.ts`:

```typescript
export class FinLabDatabase extends Dexie {
  constructor() {
    super('FinLabDB')

    // Version 1
    this.version(1).stores({
      users: '++id, email',
      portfolios: '++id, userId, name',
      transactions: '++id, portfolioId, type, symbol, timestamp'
    })

    // Version 2 (새 테이블 추가)
    this.version(2).stores({
      users: '++id, email',
      portfolios: '++id, userId, name',
      transactions: '++id, portfolioId, type, symbol, timestamp',
      alerts: '++id, userId, portfolioId, type'
    }).upgrade(tx => {
      // 마이그레이션 로직
      return tx.table('users').toCollection().modify(user => {
        user.alertsEnabled = true
      })
    })
  }
}
```

### 초기 데이터 시딩

**자동 시딩 (첫 실행 시)**

```typescript
this.on('populate', async () => {
  // Demo 사용자 생성
  await this.users.add({
    email: 'demo@finlab.app',
    role: 'USER',
    createdAt: new Date()
  })

  // Demo 포트폴리오 생성
  await this.portfolios.add({
    userId: 1,
    name: 'My Portfolio',
    baseCurrency: 'USD',
    createdAt: new Date(),
    updatedAt: new Date()
  })
})
```

**수동 시딩**

`src/utils/seedLoader.ts`:

```typescript
import { db } from '@/services/db/schema'
import seedData from '@/assets/seeds/demo-data.json'

export async function loadSeedData() {
  await db.transaction('rw', db.users, db.portfolios, db.transactions, async () => {
    // 기존 데이터 삭제 (주의!)
    await db.users.clear()
    await db.portfolios.clear()
    await db.transactions.clear()

    // 시드 데이터 추가
    await db.users.bulkAdd(seedData.users)
    await db.portfolios.bulkAdd(seedData.portfolios)
    await db.transactions.bulkAdd(seedData.transactions)
  })
}

// 사용
import { loadSeedData } from '@/utils/seedLoader'

// 개발 모드에서만 실행
if (import.meta.env.DEV) {
  await loadSeedData()
}
```

### 데이터베이스 초기화

```typescript
// 전체 데이터베이스 삭제
await db.delete()

// 새로 생성
const newDb = new FinLabDatabase()

// 특정 테이블만 초기화
await db.portfolios.clear()
await db.transactions.clear()
```

---

## 빌드 설정

### Vite 설정

`vite.config.ts`:

```typescript
import { defineConfig, type UserConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig(({ mode }): UserConfig => {
  return {
    plugins: [vue()],

    // Path alias
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
        '@components': path.resolve(__dirname, './src/components'),
        '@stores': path.resolve(__dirname, './src/stores')
      }
    },

    // 개발 서버
    server: {
      port: 5173,
      strictPort: false,
      host: true,
      open: true,
      cors: true,
      proxy: mode === 'development' ? {
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api/, '')
        }
      } : undefined
    },

    // 빌드 옵션
    build: {
      outDir: 'dist',
      assetsDir: 'assets',
      sourcemap: mode === 'development',
      minify: mode === 'production' ? 'terser' : false,

      // 청크 사이즈 경고
      chunkSizeWarningLimit: 1000,

      // Rollup 옵션
      rollupOptions: {
        output: {
          // Manual chunk splitting
          manualChunks: {
            'vue-vendor': ['vue', 'vue-router', 'pinia'],
            'ui-vendor': ['chart.js'],
            'db-vendor': ['dexie']
          }
        }
      },

      // Terser 옵션 (프로덕션)
      terserOptions: {
        compress: {
          drop_console: mode === 'production',
          drop_debugger: mode === 'production'
        }
      }
    },

    // Optimizations
    optimizeDeps: {
      include: ['vue', 'pinia', 'vue-router', 'dexie', 'chart.js', 'zod']
    }
  }
})
```

### TypeScript 설정

`tsconfig.json`:

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "module": "ESNext",
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "moduleResolution": "bundler",
    "strict": true,
    "jsx": "preserve",
    "skipLibCheck": true,
    "esModuleInterop": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "paths": {
      "@/*": ["./src/*"],
      "@components/*": ["./src/components/*"],
      "@stores/*": ["./src/stores/*"]
    },
    "types": ["vite/client", "node"]
  },
  "include": ["src/**/*.ts", "src/**/*.d.ts", "src/**/*.tsx", "src/**/*.vue"],
  "exclude": ["node_modules", "dist"]
}
```

### ESLint & Prettier (선택)

**.eslintrc.cjs**:
```javascript
module.exports = {
  root: true,
  env: {
    node: true,
    browser: true,
    es2021: true
  },
  extends: [
    'plugin:vue/vue3-recommended',
    'eslint:recommended',
    '@vue/typescript/recommended'
  ],
  parserOptions: {
    ecmaVersion: 2021
  },
  rules: {
    '@typescript-eslint/no-explicit-any': 'warn',
    'vue/multi-word-component-names': 'off'
  }
}
```

**.prettierrc**:
```json
{
  "semi": false,
  "singleQuote": true,
  "tabWidth": 2,
  "trailingComma": "none",
  "printWidth": 100
}
```

---

## 배포 설정

### GitHub Pages

**1. GitHub Repository 설정**

Settings → Pages:
- Source: GitHub Actions
- Branch: gh-pages

**2. GitHub Actions Workflow**

`.github/workflows/deploy.yml`:

```yaml
name: Deploy to GitHub Pages

on:
  push:
    branches: [main]
  workflow_dispatch:

permissions:
  contents: read
  pages: write
  id-token: write

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
          cache-dependency-path: 'frontend/package-lock.json'

      - name: Install dependencies
        working-directory: frontend
        run: npm ci

      - name: Build
        working-directory: frontend
        run: npm run build
        env:
          VITE_API_BASE_URL: https://api.finlab.app
          VITE_BACKEND_MODE: live

      - name: Upload artifact
        uses: actions/upload-pages-artifact@v3
        with:
          path: frontend/dist

  deploy:
    needs: build
    runs-on: ubuntu-latest

    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}

    steps:
      - name: Deploy to GitHub Pages
        id: deployment
        uses: actions/deploy-pages@v4
```

**3. Base URL 설정**

`vite.config.ts`:
```typescript
export default defineConfig({
  base: '/finlab/', // 저장소 이름과 일치
})
```

### Vercel

**1. Vercel CLI 설치**

```bash
npm i -g vercel
```

**2. 배포**

```bash
cd frontend
vercel --prod
```

**3. 환경 변수 설정**

Vercel Dashboard → Settings → Environment Variables:
- `VITE_API_BASE_URL`: https://api.finlab.app
- `VITE_BACKEND_MODE`: live

### Netlify

**1. netlify.toml 생성**

```toml
[build]
  command = "npm run build"
  publish = "dist"

[[redirects]]
  from = "/*"
  to = "/index.html"
  status = 200

[build.environment]
  VITE_API_BASE_URL = "https://api.finlab.app"
  VITE_BACKEND_MODE = "live"
```

**2. 배포**

```bash
npm i -g netlify-cli
netlify deploy --prod
```

---

## 보안 설정

### Content Security Policy

`index.html`:

```html
<meta http-equiv="Content-Security-Policy"
      content="
        default-src 'self';
        script-src 'self' 'unsafe-eval' 'unsafe-inline';
        style-src 'self' 'unsafe-inline';
        img-src 'self' data: https:;
        font-src 'self' data:;
        connect-src 'self' https://api.finlab.app;
        worker-src 'self' blob:;
      ">
```

### HTTPS 강제

**Vercel/Netlify**: 자동으로 HTTPS 적용

**Nginx (자체 호스팅)**:

```nginx
server {
    listen 80;
    server_name finlab.app;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name finlab.app;

    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;

    root /var/www/finlab/dist;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

### CORS 설정 (Backend)

Spring Boot 예시:

```java
@Configuration
public class CorsConfig {
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(
            "https://yourusername.github.io",
            "http://localhost:5173"
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
```

---

## 성능 최적화

### Code Splitting

**Route-based splitting (자동)**

```typescript
// router.ts
const routes = [
  {
    path: '/dashboard',
    component: () => import('@/pages/DashboardPage.vue')  // Lazy load
  }
]
```

**Component-based splitting**

```vue
<script setup>
import { defineAsyncComponent } from 'vue'

const HeavyChart = defineAsyncComponent(() =>
  import('@/components/HeavyChart.vue')
)
</script>
```

### Asset Optimization

**이미지 최적화**
- WebP 포맷 사용
- Lazy loading: `loading="lazy"`
- Responsive images: `srcset`

**Font 최적화**
- Subset fonts (한글: 자주 쓰는 글자만)
- `font-display: swap`

### Bundle Size 분석

```bash
# Rollup plugin visualizer 설치
npm i -D rollup-plugin-visualizer

# vite.config.ts
import { visualizer } from 'rollup-plugin-visualizer'

export default defineConfig({
  plugins: [
    vue(),
    visualizer({ open: true })
  ]
})

# 빌드 후 stats.html 생성
npm run build
```

### Lighthouse 점수 개선

**목표 점수**
- Performance: ≥90
- Accessibility: ≥90
- Best Practices: ≥90
- SEO: ≥80

**개선 방법**
1. 이미지 최적화
2. Code splitting
3. 불필요한 JavaScript 제거
4. ARIA 라벨 추가
5. Semantic HTML 사용
6. Meta tags 추가

---

## 모니터링 및 로깅

### Sentry (에러 추적)

```typescript
// main.ts
import * as Sentry from '@sentry/vue'

if (import.meta.env.PROD) {
  Sentry.init({
    app,
    dsn: 'YOUR_SENTRY_DSN',
    environment: import.meta.env.MODE,
    integrations: [
      new Sentry.BrowserTracing(),
      new Sentry.Replay()
    ],
    tracesSampleRate: 1.0,
    replaysSessionSampleRate: 0.1
  })
}
```

### 로컬 로깅

```typescript
// utils/logger.ts
export const logger = {
  debug: (msg: string, meta?: any) => {
    if (import.meta.env.VITE_DEBUG === 'true') {
      console.log('[DEBUG]', msg, meta)
    }
  },

  error: (msg: string, error?: Error) => {
    console.error('[ERROR]', msg, error)

    if (import.meta.env.PROD) {
      // Send to Sentry
    }
  }
}
```

---

## 문제 해결

일반적인 설정 문제는 [Troubleshooting Guide](TROUBLESHOOTING.md)를 참조하세요.

---

**개발 가이드는 [Developer Guide](DEVELOPER-GUIDE.md)를 참조하세요.**
