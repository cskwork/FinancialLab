/**
 * Toast Store
 * 사용자 피드백 메시지를 전역 관리 (Pinia)
 */

import { defineStore } from 'pinia'
import { ref } from 'vue'

export type ToastVariant = 'info' | 'success' | 'warning' | 'error'

export interface ToastMessage {
  id: string
  type: ToastVariant
  message: string
  actionLabel?: string
  action?: () => void
  timeout?: number
}

const DEFAULT_TIMEOUT = 5000

export const useToastStore = defineStore('toast', () => {
  const toasts = ref<ToastMessage[]>([])

  /**
   * 토스트 추가
   */
  function addToast(toast: Omit<ToastMessage, 'id'>) {
    const id = crypto.randomUUID()
    const timeout = toast.timeout ?? DEFAULT_TIMEOUT

    const newToast: ToastMessage = {
      id,
      timeout,
      ...toast
    }

    toasts.value.push(newToast)

    if (timeout > 0) {
      window.setTimeout(() => removeToast(id), timeout)
    }

    return id
  }

  /**
   * 토스트 제거
   */
  function removeToast(id: string) {
    toasts.value = toasts.value.filter((toast) => toast.id !== id)
  }

  /**
   * 모든 토스트 초기화
   */
  function clearToasts() {
    toasts.value = []
  }

  return {
    toasts,
    addToast,
    removeToast,
    clearToasts
  }
})
