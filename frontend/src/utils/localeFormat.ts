/**
 * 화면 표시용 숫자·날짜 포맷
 * 선택된 UI 언어(ko/en)를 따른다. 입력값(date/datetime-local), API, CSV 형식에는 쓰지 않는다.
 */

export type UiLocale = 'ko' | 'en'

const LOCALE_TAGS: Record<UiLocale, string> = {
  ko: 'ko-KR',
  en: 'en-US'
}

export function localeTag(locale: string): string {
  return LOCALE_TAGS[locale as UiLocale] ?? LOCALE_TAGS.en
}

/** 소수 자릿수를 고정한 숫자 (그룹 구분 포함) */
export function formatNumber(value: number, locale: string, fractionDigits = 2): string {
  return value.toLocaleString(localeTag(locale), {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits
  })
}

/** USD 금액: 통화 기호는 두 언어 모두 '$'로 유지한다 */
export function formatUsd(value: number, locale: string, fractionDigits = 2): string {
  return `$${formatNumber(value, locale, fractionDigits)}`
}

/** 부호를 붙인 USD 금액 (색만으로 손익을 구분하지 않도록) */
export function formatSignedUsd(value: number, locale: string): string {
  return `${value >= 0 ? '+' : '-'}${formatUsd(Math.abs(value), locale)}`
}

/** 차트 축 눈금용 축약 금액 */
export function formatCompactUsd(value: number, locale: string): string {
  return `$${value.toLocaleString(localeTag(locale), { notation: 'compact', maximumFractionDigits: 1 })}`
}

/** 거래 시각 (사용자 시간대 기준 날짜+시:분) */
export function formatDateTime(value: string | Date, locale: string): string {
  return new Date(value).toLocaleDateString(localeTag(locale), {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}

/** 날짜만 (사용자 시간대 기준) */
export function formatDate(value: string | Date, locale: string): string {
  return new Date(value).toLocaleDateString(localeTag(locale))
}

/** 'YYYY-MM-DD' 거래일 문자열 (UTC로 고정해 날짜가 밀리지 않게) */
export function formatSessionDay(day: string, locale: string): string {
  return new Date(`${day}T00:00:00Z`).toLocaleDateString(localeTag(locale), {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    timeZone: 'UTC'
  })
}

/** 'YYYY-MM-DD' 거래일의 월 약칭 */
export function formatSessionMonth(day: string, locale: string): string {
  return new Date(`${day}T00:00:00Z`).toLocaleDateString(localeTag(locale), { month: 'short', timeZone: 'UTC' })
}
