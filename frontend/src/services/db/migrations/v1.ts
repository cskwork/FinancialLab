/**
 * Migration v1
 * 초기 스키마 버전
 */

/**
 * v1 마이그레이션은 schema.ts의 version(1)에 정의됨
 *
 * 향후 스키마 변경시 이 파일에 마이그레이션 로직 추가:
 *
 * Example:
 * export async function migrateToV2(db: FinLabDatabase) {
 *   await db.version(2).stores({
 *     users: '++id, email',
 *     // ... 새 테이블 추가 또는 인덱스 변경
 *   }).upgrade(tx => {
 *     // 데이터 변환 로직
 *   })
 * }
 */

export const V1_SCHEMA_VERSION = 1
