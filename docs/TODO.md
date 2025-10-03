# FinLab - Implementation TODO

**프로젝트**: FinLab (Vue 3 + Spring Boot 금융 데모)
**시작일**: 2025-10-02
**목표**: GitHub Pages 배포 가능한 production-ready 금융 포트폴리오 앱

---

## 📊 Progress Overview

- **Phase 1 (Foundation)**: 🔄 In Progress
- **Phase 2 (Core Features)**: ⏳ Pending
- **Phase 3 (Advanced Features)**: ⏳ Pending
- **Phase 4 (Documentation)**: ⏳ Pending
- **Phase 5 (Launch)**: ⏳ Pending

---

## Phase 1: Foundation Setup (Week 1)

### Project Structure
- [x] docs/TODO.md 생성
- [x] docs/IMPLEMENTATION_NOTES_20251002.md 생성
- [ ] .gitignore 생성
- [ ] README.md 기본 구조

### Frontend Initialization
- [ ] Vite + Vue 3 + TypeScript 프로젝트 생성
- [ ] 핵심 dependencies 설치
  - [ ] pinia (상태 관리)
  - [ ] vue-router (라우팅)
  - [ ] dexie (IndexedDB)
  - [ ] chart.js (차트)
  - [ ] zod (스키마 검증)
  - [ ] decimal.js (정밀 계산)
  - [ ] papaparse (CSV 파싱)
- [ ] 프로젝트 구조 설정 (src/app, pages, components, stores, services, utils, workers)
- [ ] Design tokens (CSS variables) 설정
- [ ] Vue Router 설정 (lazy loading)
- [ ] Pinia store 초기 설정
- [ ] Dexie IndexedDB schema 정의
- [ ] Dexie migrations 시스템 구현
- [ ] Seed data loader 구현

### Backend Structure (Optional - 문서 생성용)
- [ ] backend-samples/spring-boot 디렉토리 생성
- [ ] Spring Boot 3 기본 구조
- [ ] pom.xml dependencies 설정
- [ ] Entity 모델 정의 (User, Portfolio, Transaction, etc.)

---

## Phase 2: Core Features (Week 2)

### Mock API System
- [ ] API client base (services/api-client.ts)
- [ ] Mock API router (route matching)
- [ ] Zod validation schemas
- [ ] Auth handlers (JWT simulation)
- [ ] Portfolio handlers (CRUD)
- [ ] Transaction handlers (CRUD + bulk)
- [ ] RBAC implementation
- [ ] Rate limiting (token bucket)
- [ ] Error response formatting

### Storage Layer
- [ ] Dexie repositories (User, Portfolio, Transaction, etc.)
- [ ] Transaction write logic
- [ ] Query optimization
- [ ] Migration testing

### Portfolio Management
- [ ] FIFO cost basis calculation
- [ ] Holdings computation (from transactions)
- [ ] Unit tests for FIFO logic
- [ ] DashboardPage.vue
- [ ] TransactionsPage.vue
- [ ] Portfolio CRUD forms
- [ ] Transaction forms (BUY/SELL/DEPOSIT/WITHDRAW)

### Data Import
- [ ] CSV parser worker (workers/csv-parser.worker.ts)
- [ ] Bulk transaction import
- [ ] Sample OHLCV data (AAPL.csv, SPY.csv in public/data/ohlcv)
- [ ] Sample transactions (public/data/seeds)

### Visualization
- [ ] Chart.js integration
- [ ] Portfolio value over time chart
- [ ] Holdings pie chart
- [ ] P&L chart

---

## Phase 3: Advanced Features (Week 3)

### Financial Calculations
- [ ] utils/finance.ts (FIFO, metrics, DCA)
- [ ] utils/money.ts (Decimal.js wrapper)
- [ ] Daily returns calculation
- [ ] Volatility (annualized)
- [ ] Sharpe ratio
- [ ] Max drawdown
- [ ] Unit tests for all metrics

### Web Workers
- [ ] Backtest worker (workers/backtest.worker.ts)
  - [ ] DCA strategy implementation
  - [ ] Lump-sum comparison
  - [ ] Performance metrics
  - [ ] Test with 10k+ candles
- [ ] Metrics worker (workers/metrics.worker.ts)
  - [ ] Portfolio metrics calculation
  - [ ] Async computation
  - [ ] Result caching

### Advanced Pages
- [ ] BacktestLabPage.vue
  - [ ] Strategy configuration form
  - [ ] Results visualization
  - [ ] DCA vs Lump-sum comparison
- [ ] GoalsPlansPage.vue
  - [ ] Goals CRUD
  - [ ] Plans CRUD (DCA scheduling)
  - [ ] Progress tracking

---

## Phase 4: Documentation & Integration (Week 4)

### API Documentation
- [ ] Swagger UI integration (ApiPlaygroundPage.vue)
- [ ] OpenAPI spec creation (public/docs/openapi.json)
- [ ] Spring Boot springdoc-openapi setup
- [ ] Javadoc generation
- [ ] JaCoCo coverage reports
- [ ] DocsPage.vue (문서 브라우저)

### Backend Toggle
- [ ] Config store (stores/config.ts)
- [ ] Backend mode switcher UI
- [ ] Live backend URL configuration
- [ ] CORS handling guide

### Error Handling & UX
- [ ] Error boundary component
- [ ] Toast notification system (composables/useToast.ts)
- [ ] Loading states
- [ ] Empty states
- [ ] Retry mechanisms

### Accessibility
- [ ] Semantic HTML audit
- [ ] ARIA labels
- [ ] Keyboard navigation
- [ ] Focus management
- [ ] Color contrast check (WCAG AA)
- [ ] Screen reader testing

---

## Phase 5: Launch Prep

### Security & Performance
- [ ] Content Security Policy (CSP)
- [ ] Security headers
- [ ] Route-based code splitting
- [ ] Lazy loading optimization
- [ ] Bundle size analysis
- [ ] Lighthouse audit (≥90 all categories)
  - [ ] Performance ≥90
  - [ ] Accessibility ≥90
  - [ ] Best Practices ≥90
  - [ ] SEO ≥90

### Landing Page
- [ ] LandingPage.vue
  - [ ] Hero section
  - [ ] Feature highlights
  - [ ] Demo video/GIF
  - [ ] CTA buttons
  - [ ] Backend mode switcher
  - [ ] Footer disclaimers

### SEO & PWA (Optional)
- [ ] robots.txt
- [ ] sitemap.xml
- [ ] manifest.webmanifest
- [ ] Service worker (offline support)

### CI/CD
- [ ] .github/workflows/deploy.yml
  - [ ] Java build step
  - [ ] Docs generation
  - [ ] Frontend build
  - [ ] GitHub Pages deployment
- [ ] Test workflow locally

### Documentation
- [ ] README.md
  - [ ] 프로젝트 소개
  - [ ] 기술 스택
  - [ ] 로컬 개발 가이드
  - [ ] 배포 가이드
  - [ ] 아키텍처 다이어그램
  - [ ] 스크린샷
- [ ] CONTRIBUTING.md (optional)
- [ ] LICENSE

### Final Testing
- [ ] Core user flows smoke test
- [ ] Cross-browser testing (Chrome, Firefox, Safari)
- [ ] Mobile responsive check
- [ ] Console error check
- [ ] Network error handling
- [ ] IndexedDB persistence test

---

## 🎯 Acceptance Criteria

### Functional
- [ ] 포트폴리오 생성 → 거래 추가 → 새로고침시 데이터 유지
- [ ] Holdings P/L 계산 정확성 (seed data 기준)
- [ ] 10k OHLCV 백테스트 < 1초 완료
- [ ] Swagger UI에서 3개 이상 엔드포인트 실행 가능
- [ ] Mock ↔ Live 모드 전환 동작

### Non-Functional
- [ ] Lighthouse Performance ≥90
- [ ] Lighthouse Accessibility ≥90
- [ ] Lighthouse Best Practices ≥90
- [ ] Core flows에서 console error 0개
- [ ] CSP violations 0개

---

## 📝 Notes

- 작업 중 결정사항은 `IMPLEMENTATION_NOTES_20251002.md`에 기록
- 3회 시도 후 실패시 해당 항목 skip하고 문서화
- MVP 우선, 점진적 개선 방식
- 한국어 주석 사용
