# FinLab

> 전문적인 포트폴리오 관리 및 백테스팅 시뮬레이션 플랫폼

## 프로젝트 개요

FinLab은 Vue 3 기반의 풀스택 금융 기술 데모 애플리케이션으로, 포트폴리오 추적, 거래 관리, DCA 백테스팅, 리스크 지표 계산 등의 기능을 제공합니다. 이 프로젝트는 프로덕션급 아키텍처 패턴과 금융 도메인 전문성을 입증하기 위해 설계되었습니다.

**주요 특징**
- 포트폴리오 CRUD 및 거래 추적
- FIFO 방식 평균 단가 계산
- DCA (Dollar Cost Averaging) 전략 백테스팅
- 리스크 지표 (샤프 비율, 변동성, 최대 낙폭)
- Web Workers를 활용한 고성능 계산
- Mock API와 Live Backend 토글 지원
- IndexedDB 기반 오프라인 지원

## 기술 스택

### Frontend
- **Framework**: Vue 3 (Composition API) + TypeScript
- **Build Tool**: Vite 7
- **State Management**: Pinia
- **Routing**: Vue Router 4
- **Database**: Dexie.js (IndexedDB wrapper)
- **Validation**: Zod 4
- **Charts**: Chart.js
- **Testing**: Vitest + happy-dom

### Backend (Optional)
- **Framework**: Spring Boot 3
- **Database**: PostgreSQL + Flyway
- **Security**: Spring Security (JWT)
- **Documentation**: SpringDoc OpenAPI, Javadoc
- **Testing**: JUnit 5, Testcontainers

## 빠른 시작

### 사전 요구사항

- Node.js 20+ (권장) 또는 16+ (Polyfill 적용)
- npm 또는 yarn
- Git

### 설치 및 실행

```bash
# 저장소 클론
git clone https://github.com/yourusername/finlab.git
cd finlab/frontend

# 의존성 설치
npm install

# 개발 서버 실행 (http://localhost:5173)
npm run dev

# 프로덕션 빌드
npm run build

# 빌드 결과 미리보기
npm run preview

# 테스트 실행
npm run test

# 테스트 커버리지 확인
npm run coverage
```

### 데모 계정

**Mock 모드 기본 계정**
- Email: `demo@finlab.app`
- 비밀번호 불필요 (자동 로그인)

## 프로젝트 구조

```
FinancialLab/
├── frontend/                   # Vue 3 프론트엔드
│   ├── public/
│   │   ├── data/              # 시드 데이터 (OHLCV CSV)
│   │   └── docs/              # 생성된 문서 (OpenAPI, Javadoc)
│   ├── src/
│   │   ├── app/               # 라우터, 메인 설정
│   │   ├── pages/             # 페이지 컴포넌트
│   │   ├── components/        # 재사용 가능한 컴포넌트
│   │   ├── stores/            # Pinia 스토어
│   │   ├── services/
│   │   │   ├── api/           # API 클라이언트 (Mock/Live)
│   │   │   └── db/            # Dexie 리포지토리
│   │   ├── workers/           # Web Workers
│   │   ├── utils/             # 유틸리티 함수
│   │   ├── composables/       # Vue Composables
│   │   └── styles/            # 전역 스타일
│   └── package.json
├── docs/                       # 프로젝트 문서
│   ├── README.md              # (이 파일)
│   ├── API-DOCUMENTATION.md   # API 명세
│   ├── USER-GUIDE.md          # 사용자 가이드
│   ├── DEVELOPER-GUIDE.md     # 개발자 가이드
│   ├── CONFIGURATION-GUIDE.md # 설정 가이드
│   └── TROUBLESHOOTING.md     # 문제 해결 가이드
└── PRD.md                      # 제품 요구사항 명세
```

## 주요 기능

### 1. 포트폴리오 관리
- 다중 포트폴리오 생성 및 관리
- 기준 통화 설정 (USD, KRW 등)
- 실시간 포트폴리오 가치 계산

### 2. 거래 추적
- BUY/SELL/DEPOSIT/WITHDRAW 거래 유형 지원
- CSV 파일 일괄 임포트
- FIFO 방식 평균 단가 자동 계산
- 거래 내역 필터링 및 검색

### 3. 백테스팅
- DCA (정액 적립식) 전략 시뮬레이션
- 일시불 투자 대비 성과 분석
- 10,000+ 캔들 데이터 처리 (Web Worker 사용)
- 프랙셔널 주식 지원

### 4. 리스크 지표
- 연간 변동성 (Volatility)
- 샤프 비율 (Sharpe Ratio)
- 최대 낙폭 (Max Drawdown)
- 연초 대비 수익률 (YTD Return)

### 5. 목표 및 계획
- 재무 목표 설정 및 진행률 추적
- DCA 투자 계획 자동화

## 아키텍처 하이라이트

### Backend Mode Toggle
사용자는 Mock API와 실제 Backend 간 전환 가능:

```typescript
// Mock Mode: 클라이언트 측 IndexedDB 사용
const configStore = useConfigStore()
configStore.setBackendMode('mock')

// Live Mode: 실제 HTTP 요청 전송
configStore.setBackendMode('live')
configStore.setBackendUrl('https://api.finlab.app')
```

### Web Workers
UI 블로킹 방지를 위한 백그라운드 계산:
- **BacktestWorker**: DCA 시뮬레이션
- **MetricsWorker**: 리스크 지표 계산
- **CSVImportWorker**: 대용량 CSV 파싱

### Dexie.js (IndexedDB)
오프라인 우선 데이터 저장:
- 버전 관리 및 마이그레이션
- 트랜잭션 지원
- 타입 안전 쿼리

### Zod 스키마 검증
API 요청/응답 런타임 검증:
```typescript
const result = CreatePortfolioRequestSchema.safeParse(data)
if (!result.success) {
  // 유효성 검증 실패 처리
}
```

## 테스트

### 단위 테스트

```bash
# 전체 테스트 실행
npm run test

# UI 모드
npm run test:ui

# 커버리지 확인
npm run coverage
```

**테스트 대상**
- FIFO 계산기 (`utils/fifoCalculator.spec.ts`)
- 각종 유틸리티 함수
- Pinia 스토어 로직
- API 스키마 검증

### 통합 테스트 (선택사항)

Spring Boot 백엔드가 있는 경우:
```bash
cd backend-samples/spring-boot
mvn verify
```

## 배포

### GitHub Pages (정적 호스팅)

```bash
# 빌드
npm run build

# dist/ 폴더를 GitHub Pages에 배포
# GitHub Actions를 통한 자동 배포 설정 가능
```

**Base URL 설정**
```typescript
// vite.config.ts
export default defineConfig({
  base: '/finlab/', // GitHub Pages 저장소 이름
})
```

### Vercel / Netlify

```bash
# Vercel
vercel --prod

# Netlify
netlify deploy --prod
```

### Docker (Backend 포함)

```bash
# Docker Compose로 전체 스택 실행
docker-compose up -d
```

## 성능 최적화

- **Code Splitting**: 라우트별 자동 분할
- **Lazy Loading**: 컴포넌트 지연 로딩
- **Tree Shaking**: 사용하지 않는 코드 제거
- **Web Workers**: CPU 집약적 작업 오프로드
- **IndexedDB**: 네트워크 요청 최소화

**Lighthouse 점수 목표**
- Performance: ≥90
- Accessibility: ≥90
- Best Practices: ≥90
- SEO: ≥80

## 보안

**주의: 이 프로젝트는 데모용입니다.**
- 실제 금융 거래 불가
- JWT 토큰은 시뮬레이션용 (프로덕션 보안 없음)
- HTTPS 및 CSP 헤더 권장

**CSP 정책 예시**
```html
<meta http-equiv="Content-Security-Policy"
      content="default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline';">
```

## 라이선스

MIT License - 자유롭게 사용, 수정, 배포 가능

## 기여 방법

1. 이 저장소를 Fork
2. Feature 브랜치 생성 (`git checkout -b feature/AmazingFeature`)
3. 변경사항 커밋 (`git commit -m 'Add some AmazingFeature'`)
4. 브랜치에 Push (`git push origin feature/AmazingFeature`)
5. Pull Request 생성

**코딩 스타일**
- ESLint + Prettier 설정 준수
- TypeScript strict 모드 사용
- 한글 주석 권장

## 로드맵

### Phase 1: Core Features (완료)
- [x] 포트폴리오 CRUD
- [x] 거래 추적 및 FIFO 계산
- [x] IndexedDB 스키마 및 마이그레이션
- [x] Mock API 라우터

### Phase 2: Advanced Features (완료)
- [x] DCA 백테스팅
- [x] 리스크 지표 계산
- [x] Web Workers 통합
- [x] CSV 임포트

### Phase 3: Documentation & Polish (진행 중)
- [x] API 문서
- [x] 사용자 가이드
- [ ] Swagger UI 통합
- [ ] 데모 비디오

### Phase 4: Backend Integration (선택)
- [ ] Spring Boot API 개발
- [ ] JWT 인증
- [ ] PostgreSQL 마이그레이션
- [ ] CI/CD 파이프라인

## 자주 묻는 질문

**Q: Mock 모드와 Live 모드의 차이는?**
A: Mock 모드는 브라우저의 IndexedDB를 사용하며, Live 모드는 실제 Backend API에 HTTP 요청을 전송합니다.

**Q: 실제 주가 데이터를 사용하나요?**
A: 데모용 CSV 파일을 사용하며, Live 모드에서는 실제 시세 API 연동 가능합니다.

**Q: 모바일 지원이 되나요?**
A: 반응형 디자인으로 모바일 브라우저에서 작동하지만, 최적의 경험은 데스크톱에서 제공됩니다.

**Q: 오프라인에서 작동하나요?**
A: Mock 모드에서는 완전히 오프라인으로 작동합니다 (IndexedDB).

## 지원 및 문의

- **GitHub Issues**: https://github.com/yourusername/finlab/issues
- **Email**: support@finlab.app
- **Documentation**: `/docs` 폴더 참조

## 감사의 말

이 프로젝트는 다음 오픈소스 라이브러리를 사용합니다:
- Vue.js 팀
- Vite 팀
- Dexie.js 메인테이너
- Chart.js 커뮤니티
- Zod 제작자

---

**Made with Vue 3 + TypeScript**
