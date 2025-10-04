# FinLab

> 전문적인 포트폴리오 관리 및 백테스팅 시뮬레이션 플랫폼

[![Vue 3](https://img.shields.io/badge/Vue-3.5-4FC08D?logo=vue.js)](https://vuejs.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.9-3178C6?logo=typescript)](https://www.typescriptlang.org/)
[![Vite](https://img.shields.io/badge/Vite-7.1-646CFF?logo=vite)](https://vitejs.dev/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**FinLab**은 Vue 3 기반의 풀스택 금융 기술 데모 애플리케이션으로, 포트폴리오 추적, DCA 백테스팅, 리스크 지표 계산 등의 기능을 제공합니다. 프로덕션급 아키텍처 패턴과 금융 도메인 전문성을 입증하기 위해 설계되었습니다.

---

## 🚀 Live Demo

**🔗 [Try FinLab](https://yourusername.github.io/FinancialLab/)** *(배포 후 업데이트)*

**Demo Account:**
- Email: `demo@finlab.app`
- Mode: Mock (브라우저 IndexedDB 사용)

---

## ✨ 주요 기능

### 💼 포트폴리오 관리
- 다중 포트폴리오 생성 및 관리
- FIFO 방식 평균 단가 자동 계산
- 실시간 포트폴리오 가치 추적

### 📊 거래 추적
- BUY/SELL/DEPOSIT/WITHDRAW 거래 지원
- CSV 파일 일괄 임포트 (Web Worker)
- 거래 내역 필터링 및 검색

### 🧪 DCA 백테스팅
- 정액 적립식 투자 전략 시뮬레이션
- 일시불 투자 대비 성과 분석
- 10,000+ 캔들 데이터 처리 (비동기)
- 프랙셔널 주식 지원

### 📈 리스크 지표
- 샤프 비율 (Sharpe Ratio)
- 연간 변동성 (Volatility)
- 최대 낙폭 (Max Drawdown)
- YTD 수익률

### 🎯 재무 목표 & 계획
- 재무 목표 설정 및 진행률 추적
- DCA 투자 계획 자동화

---

## 🛠️ 기술 스택

### Frontend
| Category | Technology |
|----------|------------|
| Framework | Vue 3 (Composition API) + TypeScript |
| Build Tool | Vite 7 |
| State Management | Pinia |
| Routing | Vue Router 4 |
| Database | Dexie.js (IndexedDB) |
| Validation | Zod 4 |
| Charts | Chart.js |
| Testing | Vitest + happy-dom |
| Workers | Web Workers (Backtest, Metrics, CSV) |

### Backend (Optional)
| Category | Technology |
|----------|------------|
| Framework | Spring Boot 3 |
| Database | PostgreSQL + Flyway |
| Security | Spring Security (JWT) |
| Documentation | SpringDoc OpenAPI, Javadoc |
| Testing | JUnit 5, Testcontainers |

---

## 🏗️ 아키텍처 하이라이트

### Backend Mode Toggle
사용자는 **Mock API**와 **Live Backend** 간 전환 가능:

```typescript
// Mock Mode: 클라이언트 측 IndexedDB 사용
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

---

## 🚀 빠른 시작

### 사전 요구사항

- Node.js 20+ (권장) 또는 16+
- npm 또는 yarn
- Git

### 설치 및 실행

```bash
# 저장소 클론
git clone https://github.com/yourusername/FinancialLab.git
cd FinancialLab/frontend

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

# 테스트 커버리지
npm run coverage
```

---

## 📁 프로젝트 구조

```
FinancialLab/
├── .github/
│   └── workflows/
│       └── deploy.yml           # GitHub Actions 배포 워크플로우
├── frontend/
│   ├── public/
│   │   ├── data/                # 시드 데이터 (OHLCV CSV)
│   │   └── docs/                # API 문서 (OpenAPI, Javadoc)
│   ├── src/
│   │   ├── app/                 # 라우터, 메인 설정
│   │   ├── pages/               # 페이지 컴포넌트
│   │   ├── components/          # 재사용 가능한 컴포넌트
│   │   ├── stores/              # Pinia 스토어
│   │   ├── services/
│   │   │   ├── api/             # API 클라이언트 (Mock/Live)
│   │   │   └── db/              # Dexie 리포지토리
│   │   ├── workers/             # Web Workers
│   │   ├── utils/               # 유틸리티 함수
│   │   ├── composables/         # Vue Composables
│   │   └── styles/              # 전역 스타일
│   └── package.json
├── docs/                         # 프로젝트 문서
│   ├── README.md
│   ├── API-DOCUMENTATION.md
│   ├── USER-GUIDE.md
│   ├── DEVELOPER-GUIDE.md
│   └── TROUBLESHOOTING.md
├── backend-samples/              # (선택) Spring Boot 샘플
└── PRD.md                        # 제품 요구사항 명세
```

---

## 📸 스크린샷

*추후 추가 예정*

---

## 📚 문서

| 문서 | 설명 |
|------|------|
| [User Guide](./docs/USER-GUIDE.md) | 사용자 가이드 (기능 설명) |
| [Developer Guide](./docs/DEVELOPER-GUIDE.md) | 개발자 가이드 (코드 구조) |
| [API Documentation](./docs/API-DOCUMENTATION.md) | API 명세 및 스키마 |
| [Configuration Guide](./docs/CONFIGURATION-GUIDE.md) | 설정 가이드 |
| [Troubleshooting](./docs/TROUBLESHOOTING.md) | 문제 해결 |
| [PRD](./PRD.md) | 제품 요구사항 명세서 |

---

## 🧪 테스트

### 단위 테스트

```bash
# 전체 테스트 실행
npm run test

# UI 모드
npm run test:ui

# 커버리지 확인
npm run coverage
```

**테스트 대상:**
- FIFO 계산기 (418 라인 테스트)
- 각종 유틸리티 함수
- Pinia 스토어 로직
- API 스키마 검증

---

## 🌐 배포

### GitHub Pages (정적 호스팅)

GitHub Actions를 통해 자동 배포:
1. `main` 브랜치에 push
2. Workflow가 자동으로 빌드 및 배포
3. https://yourusername.github.io/FinancialLab/ 에서 확인

### Vercel / Netlify

```bash
# Vercel
vercel --prod

# Netlify
netlify deploy --prod
```

---

## ⚡ 성능 최적화

- **Code Splitting**: 라우트별 자동 분할
- **Lazy Loading**: 컴포넌트 지연 로딩
- **Tree Shaking**: 사용하지 않는 코드 제거
- **Web Workers**: CPU 집약적 작업 오프로드
- **IndexedDB**: 네트워크 요청 최소화

**Lighthouse 목표 점수:**
- Performance: ≥90
- Accessibility: ≥90
- Best Practices: ≥90
- SEO: ≥80

---

## 🔒 보안 주의사항

⚠️ **이 프로젝트는 데모용입니다.**

- 실제 금융 거래 불가
- JWT 토큰은 시뮬레이션용 (프로덕션 보안 없음)
- Mock 모드는 로컬 데이터만 사용

프로덕션 환경에서는 HTTPS 및 CSP 헤더 설정 권장.

---

## 🤝 기여 방법

1. 이 저장소를 Fork
2. Feature 브랜치 생성 (`git checkout -b feature/AmazingFeature`)
3. 변경사항 커밋 (`git commit -m 'Add some AmazingFeature'`)
4. 브랜치에 Push (`git push origin feature/AmazingFeature`)
5. Pull Request 생성

**코딩 스타일:**
- TypeScript strict 모드 사용
- 한글 주석 권장
- Prettier/ESLint 설정 준수

---

## 🗺️ 로드맵

- [x] **Phase 1**: Core Features (포트폴리오, 거래, FIFO)
- [x] **Phase 2**: Advanced Features (백테스팅, 리스크 지표)
- [x] **Phase 3**: Documentation & Polish
- [ ] **Phase 4**: Backend Integration (Spring Boot)
- [ ] **Phase 5**: CI/CD & Production Deployment

---

## 📝 라이선스

MIT License - 자유롭게 사용, 수정, 배포 가능

---

## 🙏 감사의 말

이 프로젝트는 다음 오픈소스 라이브러리를 사용합니다:

- [Vue.js](https://vuejs.org/) 팀
- [Vite](https://vitejs.dev/) 팀
- [Dexie.js](https://dexie.org/) 메인테이너
- [Chart.js](https://www.chartjs.org/) 커뮤니티
- [Zod](https://zod.dev/) 제작자

---

**Made with ❤️ using Vue 3 + TypeScript**
