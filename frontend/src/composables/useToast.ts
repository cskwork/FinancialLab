/**
 * Toast 유틸 훅
 * 토스트 메시지를 간편하게 노출하기 위한 헬퍼
 */

import { useToastStore, type ToastVariant } from '@/stores/toastStore'

export function useToast() {
  const toastStore = useToastStore()

  function notify(type: ToastVariant, message: string, options?: { actionLabel?: string; onAction?: () => void; timeout?: number }) {
    toastStore.addToast({
      type,
      message,
      actionLabel: options?.actionLabel,
      action: options?.onAction,
      timeout: options?.timeout
    })
  }

  return {
    info: (message: string, options?: Parameters<typeof notify>[2]) => notify('info', message, options),
    success: (message: string, options?: Parameters<typeof notify>[2]) => notify('success', message, options),
    warning: (message: string, options?: Parameters<typeof notify>[2]) => notify('warning', message, options),
    error: (message: string, options?: Parameters<typeof notify>[2]) => notify('error', message, options),
    remove: toastStore.removeToast,
    clear: toastStore.clearToasts,
    toasts: toastStore.toasts
  }
}
