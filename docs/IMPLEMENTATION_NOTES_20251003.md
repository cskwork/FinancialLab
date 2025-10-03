# FinLab Implementation Notes
**날짜**: 2025-10-03
**작성자**: Codex
**프로젝트**: FinLab - Production-Ready Financial Demo

---

## 📋 Summary
- Node 16 환경에서도 테스트와 빌드가 가능하도록 crypto 관련 Polyfill을 추가했습니다.
- Vitest, Vite 실행 스크립트를 조정하여 최신 CLI 요구사항을 충족했습니다.

## 🔧 Changes
1. `vite.config.ts`에서 Web Crypto API 및 `crypto.hash` Polyfill을 초기화하도록 추가했습니다.
2. `src/test/setup.ts`에 동일한 Polyfill을 적용하여 테스트 런타임 안정성을 확보했습니다.
3. `scripts/run-vitest.mjs`를 신설해 Vitest 실행 전에 Polyfill을 주입하고 CLI 인자를 위임하도록 구성했습니다.
4. `package.json`의 테스트 관련 스크립트를 새로운 실행 스크립트로 교체했습니다.
5. `src/workers/csvImportWorker.ts`의 사용되지 않는 Step 콜백 인자를 정리했습니다.

## 🤔 Decisions
- Node 16 업그레이드 없이 동작을 보장하기 위해 런타임 Polyfill 방식으로 대응했습니다.
- 중복 로직이 생기더라도 안정성을 위해 구성/테스트/스크립트 각각에서 Polyfill을 명시적으로 초기화했습니다.

## 🧪 Tests
- `npm run test`
- `npm run build`

## ⚠️ Limitations
- Vite 7는 공식적으로 Node 20 이상을 요구하므로, 향후 환경 업그레이드를 권장합니다. 현재는 경고 메시지가 출력되지만 빌드는 성공합니다.

---

