# FinLab - Financial Portfolio Demo

> Vue 3 기반 포트폴리오 관리 및 백테스팅 데모 애플리케이션

[![TypeScript](https://img.shields.io/badge/TypeScript-5.6-blue.svg)](https://www.typescriptlang.org/)
[![Vue 3](https://img.shields.io/badge/Vue-3.5-brightgreen.svg)](https://vuejs.org/)
[![Vite](https://img.shields.io/badge/Vite-7.1-purple.svg)](https://vitejs.dev/)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

## 📋 프로젝트 개요

FinLab은 실전 fintech 역량을 시연하기 위한 포트폴리오 관리 데모 애플리케이션입니다. 백엔드 없이 동작하는 Mock API를 통해 IndexedDB에 데이터를 저장하며, 실제 금융 로직(FIFO, Sharpe ratio, DCA 백테스팅)을 구현합니다.

### 주요 특징

- ✅ **오프라인 우선**: IndexedDB를 활용한 완전한 클라이언트 사이드 데이터 저장
- ✅ **Mock API**: Zod 기반 타입 안전 API (백엔드 없이 동작)
- ✅ **FIFO 회계**: First-In-First-Out 방식의 정확한 손익 계산
- ✅ **백테스팅**: Web Worker 기반 DCA vs Lump-sum 전략 비교
- ✅ **리스크 메트릭**: Sharpe ratio, 변동성, 최대 낙폭 분석 (Metrics Worker)
- ✅ **OpenAPI 연동**: API Playground + 문서 허브 제공

## 🚀 빠른 시작

### 사전 요구사항

- Node.js 18+ (권장: 20 LTS)
- npm 9+

### 설치 및 실행

```bash
# 의존성 설치
npm install

# 개발 서버 실행 (http://localhost:5173)
npm run dev

# 프로덕션 빌드
npm run build

# 빌드 결과 미리보기
npm run preview

# 단위 테스트 실행
npm run test

# 테스트 커버리지 확인
npm run coverage
```

## 📁 프로젝트 구조

```
frontend/
├── docs/                          # 문서
│   ├── TODO.md                   # 작업 진행 상황
│   └── IMPLEMENTATION_NOTES_*.md # 구현 기록
├── public/
│   └── data/
│       ├── ohlcv/               # 샘플 시장 데이터 (AAPL, SPY)
│       └── seeds/               # 샘플 거래 데이터
├── src/
│   ├── app/
│   │   └── router.ts           # Vue Router 설정
│   ├── components/             # 재사용 가능한 Vue 컴포넌트
│   ├── pages/                  # 페이지 컴포넌트
│   │   ├── LandingPage.vue
│   │   ├── DashboardPage.vue
│   │   ├── TransactionsPage.vue
│   │   ├── BacktestLabPage.vue
│   │   └── ...
│   ├── services/
│   │   ├── api/                # Mock API 클라이언트
│   │   │   ├── schemas.ts      # Zod 스키마
│   │   │   ├── mockClient.ts   # Mock API 구현
│   │   │   └── index.ts
│   │   └── db/                 # IndexedDB 레이어
│   │       ├── schema.ts       # Dexie 스키마
│   │       ├── repositories/   # Repository 패턴
│   │       └── migrations/     # DB 마이그레이션
│   ├── stores/                 # Pinia 상태 관리
│   ├── utils/
│   │   ├── fifoCalculator.ts  # FIFO 손익 계산
│   │   ├── fifoCalculator.spec.ts
│   │   └── seedLoader.ts       # 샘플 데이터 로더
│   ├── styles/
│   │   ├── tokens.css          # 디자인 토큰
│   │   └── global.css          # 글로벌 스타일
│   ├── main.ts
│   └── App.vue
├── package.json
├── vite.config.ts
├── tsconfig.json
└── README.md
```

## 🛠️ 기술 스택

### 핵심 라이브러리

- **Vue 3.5** - Composition API 기반 UI 프레임워크
- **TypeScript 5.6** - 타입 안전성
- **Vite 7.1** - 빠른 빌드 도구
- **Pinia 3.0** - Vue 공식 상태 관리
- **Vue Router 4.5** - SPA 라우팅

### 데이터 & 검증

- **Dexie.js 4.2** - IndexedDB wrapper (오프라인 저장소)
- **Zod 4.1** - 스키마 기반 런타임 검증
- **Decimal.js 10.6** - 정밀한 금융 계산

### 시각화 & 유틸

- **Chart.js 4.5** - 포트폴리오 차트
- **PapaParse 5.5** - CSV 파싱 (거래 내역 import)

### 개발 도구

- **Vitest 3.2** - 단위 테스트 (14/14 테스트 통과)
- **Happy-DOM** - 가벼운 DOM 환경
- **ESLint + Prettier** - 코드 품질

## 🎯 현재 구현 상태

- ✅ **Phase 1** – 앱 셸, 디자인 시스템, IndexedDB 스키마, Repository 계층 완료
- ✅ **Phase 2** – Auth/Portfolio/Transaction 플로우와 CSV import, Goals & Plans UI 구성
- ✅ **Phase 3** – Web Worker 기반 DCA 백테스트·리스크 메트릭, API Playground 구축
- ✅ **Phase 4** – `/docs` 허브, OpenAPI 스펙, CI 산출물 placeholder 제공
- 🚧 **Phase 5** – 라이트하우스 튜닝 및 Chart.js 기반 대시보드 고도화 예정
- [ ] Swagger UI 통합 (API Playground)
- [ ] 백엔드 모드 토글 (mock ↔ live)
- [ ] Spring Boot 백엔드 (OpenAPI 생성)
- [ ] 에러 바운더리 및 토스트 시스템

#### Phase 5: Launch Prep
- [ ] WCAG AA 접근성 감사
- [ ] CSP 및 보안 헤더
- [ ] Lighthouse 최적화 (≥90 점)
- [ ] GitHub Actions CI/CD
- [ ] Landing 페이지 완성

## 🧪 테스트

### 단위 테스트 실행

```bash
# 전체 테스트 실행
npm run test

# Watch 모드
npm run test:ui

# 커버리지 리포트
npm run coverage
```

### 현재 테스트 커버리지

- **FIFO Calculator**: 14/14 테스트 통과
  - 단일/복수 매수 처리
  - FIFO 매도 로직
  - 미실현 손익 계산
  - 복합 시나리오

## 📊 데이터 모델

### Dexie IndexedDB Tables

```typescript
// User
{
  id: number (PK)
  email: string (unique)
  role: 'USER' | 'ADMIN'
  createdAt: Date
}

// Portfolio
{
  id: number (PK)
  userId: number (FK)
  name: string
  baseCurrency: string
  createdAt: Date
  updatedAt: Date
}

// Transaction
{
  id: number (PK)
  portfolioId: number (FK)
  type: 'BUY' | 'SELL' | 'DEPOSIT' | 'WITHDRAW'
  symbol?: string
  qty?: number
  price?: number
  fee: number
  timestamp: Date
  note?: string
}

// Candle (OHLCV)
{
  id: number (PK)
  symbol: string
  date: string (YYYY-MM-DD)
  open: number
  high: number
  low: number
  close: number
  volume: number
}
```

## 🔧 개발 가이드

### 코드 스타일

- **언어**: 코드는 영어, 주석은 한국어
- **컴포넌트**: Composition API + `<script setup>`
- **타입**: Strict TypeScript 모드
- **스타일**: CSS Custom Properties (design tokens)

### 디렉토리 규칙

- `pages/` - 라우트별 페이지 컴포넌트
- `components/` - 재사용 가능한 컴포넌트
- `services/api/` - API 클라이언트 및 스키마
- `services/db/` - 데이터베이스 레이어
- `utils/` - 순수 함수 유틸리티 (테스트 포함)

### API 응답 형식

```typescript
// 성공
{
  success: true,
  data: T
}

// 실패
{
  success: false,
  error: {
    code: string,
    message: string,
    details?: Record<string, unknown>
  }
}
```

## 🚀 배포

### GitHub Pages 배포 (예정)

```bash
# 프로덕션 빌드
npm run build

# dist/ 폴더를 GitHub Pages에 배포
# (GitHub Actions workflow 예정)
```

### 환경 변수

```bash
# .env.local (예제)
VITE_API_BASE_URL=http://localhost:8080  # 실제 백엔드 사용 시
VITE_MODE=mock  # mock | live
```

## 📝 문서

- [TODO.md](docs/TODO.md) - 작업 진행 상황
- [IMPLEMENTATION_NOTES_20251002.md](docs/IMPLEMENTATION_NOTES_20251002.md) - 구현 기록
- [PRD.md](../PRD.md) - 제품 요구사항 문서

## 🤝 기여

이 프로젝트는 포트폴리오 데모 목적으로 제작되었습니다.

## 📄 라이선스

MIT License

---

**Built with ❤️ using Vue 3 + TypeScript + Vite**
