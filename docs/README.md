# FinLab 문서

FinLab은 Vue 3 SPA와 Spring Boot + PostgreSQL 백엔드로 구성됩니다. 공개 데모는 브라우저 안의 IndexedDB mock API로만 동작하고, 백엔드는 공개 호스팅되지 않아 로컬에서 실행합니다.

- [프로젝트 소개, 실행 방법, 검증 결과](../README.md)
- [현재 아키텍처 (두 모드와 백엔드)](architecture.md)
- [아키텍처 결정 기록](adr/README.md)
- [백엔드 실행·설정·운영](../backend/README.md)
- [백엔드 상세 설계](../backend/docs/architecture.md)
- [공유 HTTP 계약 예시](../contract/README.md)
- [사용자 가이드](USER-GUIDE.md)
- [에셋 기록](asset-register.md)

## 과거 계획 자료

아래 문서는 백엔드 구현 전에 쓴 계획입니다. 각 문서 맨 위에 대체된 부분과 아직 구현되지 않은 부분을 적어 두었습니다.

- [API 설계](planned/API-DOCUMENTATION.md) — 구현된 `/v1/*` API로 대체. 일괄 거래 등록, 서버 백테스트는 미구현
- [설정 설계](planned/CONFIGURATION-GUIDE.md) — 대체됨. 문서의 `VITE_*` 변수 대부분은 코드에서 쓰지 않음
- [개발 설계](planned/DEVELOPER-GUIDE.md) — 대체됨
- [문제 해결 참고](planned/TROUBLESHOOTING.md) — 대체됨
- [기존 작업 계획](planned/TODO.md) — 대체됨, 체크 상태는 갱신하지 않음
