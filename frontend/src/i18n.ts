/**
 * i18n 설정
 * Vue I18n을 사용한 다국어 지원
 */

import { createI18n } from 'vue-i18n'
import ko from './locales/ko.json'
import en from './locales/en.json'

// 메시지 타입 정의
export type MessageSchema = typeof ko

// i18n 인스턴스 생성
export const i18n = createI18n<[MessageSchema], 'ko' | 'en'>({
  legacy: false, // Composition API 사용
  locale: 'ko', // 기본 언어: 한국어
  fallbackLocale: 'en', // 폴백 언어: 영어
  messages: {
    ko,
    en
  },
  globalInjection: true // 전역 $t 사용 가능
})

export default i18n
