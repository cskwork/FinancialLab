# Implementation Notes - 2025-10-03

## 프로젝트 개요

FinLab Frontend - Vue 3 기반 포트폴리오 관리 및 백테스팅 데모 애플리케이션

## 아키텍처 결정

### 1. 프론트엔드 아키텍처

**선택**: Vue 3 Composition API + TypeScript Strict Mode
- **이유**: 타입 안전성과 코드 재사용성을 극대화
- **장점**: 명시적 타입 체크, 런타임 오류 감소
- **단점**: 초기 설정 복잡도 증가 (tsconfig.app.json에 paths 설정 필요)

### 2. 데이터 레이어

**선택**: IndexedDB (Dexie.js) + Repository Pattern
- **이유**: 오프라인 우선 아키텍처, 백엔드 없이 완전한 데모 구현
- **장점**:
  - 브라우저 내 완전한 데이터 저장
  - 대용량 데이터 처리 가능 (LocalStorage 대비)
  - 트랜잭션 지원
- **트레이드오프**:
  - 초기 스키마 설계 중요
  - 마이그레이션 전략 필요

### 3. API 레이어

**선택**: Mock API (Zod 기반 검증)
- **이유**:
  - 백엔드 없이 완전한 타입 안전 API
  - 실제 백엔드 전환 용이 (인터페이스 동일)
- **구현**:
  - `mockClient.ts`: IndexedDB 기반 CRUD
  - `schemas.ts`: 요청/응답 Zod 스키마 (런타임 검증)
- **장점**:
  - Contract-first 개발
  - 런타임 타입 검증으로 버그 조기 발견

### 4. 금융 계산 로직

**선택**: Decimal.js + FIFO 알고리즘
- **이유**: JavaScript Number의 부동소수점 오류 방지
- **구현**: `fifoCalculator.ts`
  - Lot 단위 매수/매도 추적
  - 정확한 평균단가 계산
  - 실현/미실현 손익 분리
- **검증**: 14개 단위 테스트 (100% 통과)

### 5. 빌드 시스템

**선택**: Vite 7.1
- **이유**:
  - 빠른 HMR (Hot Module Replacement)
  - Code splitting (lazy loading)
  - Rollup 기반 프로덕션 빌드
- **결과**:
  - 빌드 시간: ~700ms
  - 메인 번들: 208.50 kB (75.06 kB gzipped)
  - 8개 페이지 청크 (0.4-1.8 KB each)

## 구현 로그

### 2025-10-02: Foundation Setup

#### 완료된 작업
1. **프로젝트 초기화**
   - Vite + Vue 3 + TypeScript 템플릿
   - 의존성 설치: pinia, vue-router, dexie, chart.js, zod, decimal.js, papaparse
   - Dev dependencies: vitest, happy-dom, @types/papaparse

2. **디자인 시스템**
   - `styles/tokens.css`: 색상, 타이포그래피, 간격, 그림자
   - `styles/global.css`: 리셋, 접근성 유틸리티, 인쇄 스타일
   - CSS Custom Properties 기반 (dark mode 준비 완료)

3. **라우팅**
   - Vue Router 4.5 설정 (lazy loading)
   - 8개 라우트: landing, login, dashboard, transactions, goals-plans, backtest, api-playground, docs
   - 메타데이터: title, requiresAuth (인증 가드 준비)

4. **데이터베이스 스키마**
   - Dexie v1 스키마: 8개 테이블
     - users, portfolios, transactions, holdings
     - goals, plans, candles, metricSnapshots
   - 복합 인덱스: [symbol+date], [portfolioId+symbol]
   - Repository 패턴: UserRepository, PortfolioRepository, TransactionRepository

#### 발생한 이슈 및 해결

**Issue 1**: TypeScript 모듈 해석 오류
```
Cannot find module '@/pages/*.vue'
```
- **원인**: .vue 파일에 대한 타입 선언 누락
- **해결**: `src/env.d.ts` 생성, Vue 모듈 선언 추가

**Issue 2**: Vite config test 속성 오류
```
'test' does not exist in type 'UserConfigExport'
```
- **원인**: `import { defineConfig } from 'vite'` 사용
- **해결**: `import { defineConfig } from 'vitest/config'`로 변경

**Issue 3**: TypeScript strict mode 오류 (unused parameters)
```
'from' is declared but its value is never read
```
- **해결**: 미사용 파라미터에 `_` 접두사 (`_from`, `_savedPosition`)

**Issue 4**: Path alias 해석 실패
```
Cannot find module '@/services/db/schema'
```
- **원인**: tsconfig.app.json에 paths 설정 누락
- **해결**: `baseUrl: "."`, `paths: { "@/*": ["./src/*"] }` 추가

### 2025-10-03: Core Features Implementation

#### 완료된 작업

1. **Seed Data Loader**
   - OHLCV 데이터: AAPL (60 rows), SPY (60 rows)
     - 기간: 2024-01-02 ~ 2024-04-01
     - CSV 형식: date, open, high, low, close, volume
   - 샘플 거래: 10건 (BUY, SELL, DEPOSIT, WITHDRAW)
   - 자동 로드: `initializeSeedData()` in `main.ts`
   - 중복 방지: DB가 비어있을 때만 실행

2. **Mock API Router**
   - **Auth API**
     - `login(email)`: 사용자 조회 또는 생성
     - Mock 토큰 생성
   - **Portfolio API**
     - `getPortfolios(userId)`: 포트폴리오 목록
     - `getPortfolio(id)`: 단건 조회
     - `createPortfolio()`: 생성 (Zod 검증)
     - `updatePortfolio()`: 수정
     - `deletePortfolio()`: 삭제 (cascade)
   - **Transaction API**
     - `getTransactions(portfolioId)`: 거래 목록
     - `createTransaction()`: 생성 (BUY/SELL 조건 검증)
     - `deleteTransaction()`: 삭제
   - **Market Data API**
     - `getMarketData(symbol, dateRange)`: OHLCV 조회
   - **검증**: Zod 스키마 기반 런타임 검증
   - **응답 형식**: `{ success, data?, error? }`

3. **FIFO Calculator**
   - **구현**:
     - `FifoLot`: qty (Decimal), price (Decimal), purchaseDate
     - `processBuy()`: 거래 목록 → Holdings 계산
     - `calculateUnrealizedPnL()`: 미실현 손익
   - **알고리즘**:
     - 매수: 새 로트 추가
     - 매도: 가장 오래된 로트부터 순차 소진 (FIFO)
     - 평균단가: 전체 로트의 가중평균
   - **정밀도**: Decimal.js 사용 (부동소수점 오류 방지)
   - **테스트**: 14개 테스트 (100% 통과)
     - 단일/복수 매수
     - FIFO 매도 (전체/일부 로트)
     - 보유량 초과 매도 (에러)
     - 미실현 손익 (양/음)
     - 복합 시나리오

#### 발생한 이슈 및 해결

**Issue 5**: Zod record 타입 오류
```
Expected 2-3 arguments, but got 1
```
- **원인**: `z.record(z.unknown())` → 키 타입 누락
- **해결**: `z.record(z.string(), z.unknown())`

**Issue 6**: Array 인덱스 undefined 체크
```
Object is possibly 'undefined'
```
- **원인**: TypeScript strict null check
- **해결**:
  - `const lot = holding.lots[i]`
  - `if (!lot) continue`

**Issue 7**: Candle date 타입 불일치
```
Type 'Date' is not assignable to type 'string'
```
- **원인**: 스키마에서 `date: string` (YYYY-MM-DD)
- **해결**: CSV 파싱 시 `row.date` 직접 사용 (Date 변환 제거)

## 테스트 전략

### 단위 테스트

- **프레임워크**: Vitest 3.2 + Happy-DOM
- **커버리지 도구**: v8
- **현재 상태**:
  - FIFO Calculator: 14/14 테스트 (100%)
  - Mock API: 미구현 (수동 테스트)
  - Seed Loader: 미구현

### 테스트 원칙

1. **순수 함수 우선**: utils/ 디렉토리 (예: fifoCalculator)
2. **엣지 케이스**: 보유량 초과, 빈 포트폴리오
3. **복합 시나리오**: 여러 매수/매도 조합
4. **정밀도**: Decimal.js 계산 정확성

## 알려진 제한사항

### 1. UI 미구현
- **현재**: 모든 페이지가 placeholder 상태
- **영향**: 데이터 레이어는 완전하지만 시각화 없음
- **계획**: Phase 2 나머지 작업 (Portfolio CRUD, Transaction UI, Charts)

### 2. 인증 로직 미완성
- **현재**: Router에서 인증 가드 주석 처리
- **영향**: 모든 페이지 접근 가능
- **계획**: Auth store 구현 후 활성화

### 3. 실시간 시세 없음
- **현재**: 정적 OHLCV 데이터 (2024-01-02 ~ 04-01)
- **영향**: 현재가 기반 미실현 손익 계산 불가
- **계획**: Mock API에 "latest price" 엔드포인트 추가

### 4. Holdings 자동 업데이트 미구현
- **현재**: FIFO calculator는 순수 함수
- **영향**: Transaction 추가 시 Holdings 테이블 수동 업데이트 필요
- **계획**: Repository에 `recalculateHoldings()` 메서드 추가

### 5. 에러 처리 미완성
- **현재**: API 오류는 console.error만
- **영향**: 사용자 피드백 없음
- **계획**: Phase 4 - Toast notification system

## 성공 지표

### ✅ 완료

- [x] 빌드 성공 (737ms)
- [x] 번들 크기 최적화 (75 KB gzipped)
- [x] TypeScript strict 모드 (0 errors)
- [x] 코드 스플리팅 (8 lazy chunks)
- [x] FIFO 단위 테스트 (14/14 통과)
- [x] Seed 데이터 로드 (120 candles, 10 transactions)

### 🚧 진행 중

- [ ] UI 구현률: 10% (landing page만 기본 디자인)
- [ ] API 커버리지: 60% (CRUD 완료, Goals/Plans 미구현)
- [ ] 테스트 커버리지: 20% (FIFO만 완료)

### 📊 목표 (Phase 5)

- [ ] Lighthouse 점수 ≥90 (모든 카테고리)
- [ ] WCAG AA 접근성
- [ ] 전체 테스트 커버리지 ≥80%
- [ ] 초기 로딩 시간 <2s

## 다음 단계 (Priority)

### Phase 2 완료 (Core Features)

1. **Portfolio CRUD 페이지** (1-2일)
   - Pinia store 생성 (portfolioStore)
   - 포트폴리오 목록 페이지
   - 생성/수정 모달
   - Holdings 계산 통합

2. **Transaction 관리** (2-3일)
   - Transaction 목록 (테이블)
   - 수동 입력 폼
   - CSV import worker (PapaParse)
   - FIFO 자동 업데이트

3. **차트 시각화** (1-2일)
   - Chart.js 설정
   - 포트폴리오 가치 추이
   - Holdings 파이 차트
   - 손익 바 차트

### Phase 3 (Advanced Features)

4. **Backtest Worker** (2-3일)
   - Web Worker 생성
   - DCA 전략 시뮬레이션
   - Lump-sum 전략 비교
   - 결과 시각화

5. **Metrics Worker** (1-2일)
   - Sharpe ratio 계산
   - 변동성 (standard deviation)
   - 최대 낙폭 (max drawdown)

## 참고 자료

- [Dexie.js Docs](https://dexie.org/)
- [Zod Documentation](https://zod.dev/)
- [Vue 3 Composition API](https://vuejs.org/guide/extras/composition-api-faq.html)
- [Chart.js Docs](https://www.chartjs.org/)
- [Decimal.js API](https://mikemcl.github.io/decimal.js/)

## 변경 이력

### 2025-10-03
- README.md 작성 (완전한 프로젝트 문서)
- IMPLEMENTATION_NOTES 생성
- Phase 2 코어 기능 완료 (9/25 tasks)

### 2025-10-02
- 프로젝트 초기화 및 Foundation 완료
- Seed data loader 구현
- Mock API router 구현
- FIFO calculator 구현 및 테스트
