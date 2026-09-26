/**
 * <input type="datetime-local"> 값 포맷터
 * datetime-local은 로컬 시각을 기대하므로 toISOString()(UTC)을 잘라 쓰면 시차만큼 어긋난다.
 */
const pad = (value: number) => String(value).padStart(2, '0')

export function toDateTimeLocalValue(date: Date = new Date()): string {
  return `${toDateInputValue(date)}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/**
 * <input type="date"> 값 포맷터 (로컬 날짜)
 */
export function toDateInputValue(date: Date = new Date()): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}
