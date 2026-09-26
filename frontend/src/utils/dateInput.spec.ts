import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { toDateInputValue, toDateTimeLocalValue } from './dateInput'

describe('toDateTimeLocalValue', () => {
  const originalTz = process.env.TZ

  beforeEach(() => {
    process.env.TZ = 'Asia/Seoul'
  })

  afterEach(() => {
    process.env.TZ = originalTz
  })

  it('formats the local wall-clock time, not UTC', () => {
    // 2026-09-26 08:05 KST == 2026-09-25 23:05 UTC
    const now = new Date('2026-09-25T23:05:00Z')
    expect(toDateTimeLocalValue(now)).toBe('2026-09-26T08:05')
  })

  it('round-trips through the datetime-local parse used on submit', () => {
    const now = new Date('2026-09-25T23:05:00Z')
    expect(new Date(toDateTimeLocalValue(now)).toISOString()).toBe(now.toISOString())
  })
})

describe('toDateInputValue', () => {
  const originalTz = process.env.TZ

  beforeEach(() => {
    process.env.TZ = 'Asia/Seoul'
  })

  afterEach(() => {
    process.env.TZ = originalTz
  })

  it('uses the local calendar day in early KST morning (UTC is still yesterday)', () => {
    // 2026-09-26 00:30 KST == 2026-09-25 15:30 UTC
    const now = new Date('2026-09-25T15:30:00Z')
    expect(now.toISOString().slice(0, 10)).toBe('2026-09-25')
    expect(toDateInputValue(now)).toBe('2026-09-26')
  })

  it('keeps the local day right before local midnight', () => {
    // 2026-09-26 23:59 KST == 2026-09-26 14:59 UTC
    expect(toDateInputValue(new Date('2026-09-26T14:59:00Z'))).toBe('2026-09-26')
  })
})
