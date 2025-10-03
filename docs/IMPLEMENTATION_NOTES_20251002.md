# FinLab Implementation Notes
**날짜**: 2025-10-02
**작성자**: Claude Code
**프로젝트**: FinLab - Production-Ready Financial Demo

---

## 📋 Summary

GitHub Pages에 배포 가능한 Vue 3 + Spring Boot 기반 금융 포트폴리오 관리 데모 애플리케이션 구축 시작.

**핵심 목표**:
- Frontend: Vue 3 + TypeScript + Pinia + Dexie (IndexedDB)
- Mock API with production patterns (RBAC, rate limiting, validation)
- Backend toggle (Mock ↔ Live Spring Boot)
- Financial calculations (FIFO, Sharpe, DCA backtesting)
- GitHub Pages deployment with CI/CD

---

## 🏗️ Architecture Decisions

### 1. Frontend Stack
- **Framework**: Vue 3 (Composition API) + Vite
- **State**: Pinia (official Vue store)
- **Storage**: IndexedDB via Dexie.js (오프라인 우선)
- **Validation**: Zod (schema-first, TypeScript 타입 생성)
- **Charts**: Chart.js (번들 포함, CDN 없음)
- **Workers**: Dedicated workers for heavy computation (backtest, metrics, CSV)

**이유**:
- Vue 3 Composition API는 로직 재사용성 우수
- Dexie는 IndexedDB wrapper 중 가장 성숙
- Zod는 OpenAPI와 일관된 스키마 검증 가능
- Web Workers로 UI 블로킹 방지

### 2. Mock API Quality
- Zod 스키마로 요청/응답 검증
- RBAC (role-based access control)
- Rate limiting (token bucket)
- Fault injection (5xx/latency simulation)
- OpenAPI 3.1 spec 준수

**이유**: 실제 프로덕션 API 패턴 시연

### 3. Financial Logic
- **FIFO cost basis**: Queue로 purchase lots 관리
- **Decimal.js**: 부동소수점 정밀도 문제 방지
- **UTC normalization**: 시간대 drift 방지
- **Deterministic tests**: 알려진 데이터셋으로 검증

### 4. Backend (Spring Boot)
- **목적**: 문서/증적 생성 (OpenAPI, Javadoc, JaCoCo)
- **배포**: Optional (Render/Fly.io)
- **CI 통합**: GitHub Actions에서 자동 빌드 → 문서 추출

---

## 🔄 Changes Log

### 2025-10-02 - Project Initialization

#### Created
1. `docs/TODO.md` - 25개 작업 항목 (5 phases)
2. `docs/IMPLEMENTATION_NOTES_20251002.md` - 이 파일

#### Next Steps
1. `.gitignore` 생성 (node_modules, dist, .env, IDE files)
2. Vite 프로젝트 초기화
3. 프로젝트 구조 생성 (PRD.md 기준)
4. Dexie schema 정의

---

## 🧪 Testing Strategy

### Unit Tests
- **대상**: utils/finance.ts (FIFO, metrics, DCA)
- **도구**: Vitest (Vite 네이티브)
- **커버리지**: 핵심 계산 로직 100%

### Integration Tests
- **대상**: Mock API handlers, Dexie repositories
- **검증**: 스키마 준수, 에러 처리, 트랜잭션 무결성

### E2E Tests (Optional)
- **도구**: Playwright
- **시나리오**: 포트폴리오 생성 → 거래 추가 → 백테스트 실행

### Performance Tests
- 10k+ candles backtest < 1s
- Portfolio metrics calculation < 500ms
- CSV import 1MB < 2s

---

## ⚠️ Known Limitations

### Current
- 프로젝트가 아직 초기화되지 않음
- Backend는 선택 사항 (문서 생성용)

### Future Considerations
- Real-time price updates (WebSocket) - Phase 6
- Multi-currency support - Phase 6
- Tax lot tracking (LIFO, HIFO) - Phase 6
- PDF report generation - Phase 6

---

## 🎯 Success Metrics

### Code Quality
- [ ] TypeScript strict mode 활성화
- [ ] ESLint + Prettier 설정
- [ ] 0 console errors in production

### Performance
- [ ] Lighthouse Performance ≥90
- [ ] First Contentful Paint < 1.5s
- [ ] Time to Interactive < 3s
- [ ] Bundle size < 500KB (gzipped)

### Accessibility
- [ ] WCAG AA compliance
- [ ] Keyboard navigation 100%
- [ ] Screen reader compatible

### Documentation
- [ ] README with setup instructions
- [ ] OpenAPI spec available
- [ ] Javadoc published (if backend included)
- [ ] Architecture diagrams

---

## 🔗 References

- [PRD.md](../PRD.md) - 전체 제품 요구사항
- [Vue 3 Docs](https://vuejs.org/)
- [Dexie.js Guide](https://dexie.org/)
- [Zod Documentation](https://zod.dev/)
- [Spring Boot 3](https://spring.io/projects/spring-boot)

---

## 📝 Daily Log

### 2025-10-02
- ✅ PRD.md 분석 완료
- ✅ TODO.md 생성 (25 tasks across 5 phases)
- ✅ IMPLEMENTATION_NOTES 생성
- 🔄 프로젝트 초기화 준비 중
