/**
 * Logger Utility
 * 구조화된 로깅 시스템 (개발/프로덕션 환경 대응)
 */

type LogLevel = 'debug' | 'info' | 'warn' | 'error'

interface LogContext {
  [key: string]: unknown
}

class Logger {
  private isDevelopment: boolean

  constructor() {
    this.isDevelopment = import.meta.env.DEV
  }

  /**
   * 로그 레벨별 색상 반환
   */
  private getColor(level: LogLevel): string {
    const colors = {
      debug: '#6B7280',
      info: '#3B82F6',
      warn: '#F59E0B',
      error: '#EF4444'
    }
    return colors[level]
  }

  /**
   * 로그 포맷팅
   */
  private format(level: LogLevel, message: string, context?: LogContext): void {
    const timestamp = new Date().toISOString()
    const color = this.getColor(level)

    if (this.isDevelopment) {
      console.log(
        `%c[${level.toUpperCase()}]%c ${timestamp} - ${message}`,
        `color: ${color}; font-weight: bold`,
        'color: inherit',
        context || ''
      )
    } else {
      // 프로덕션 환경에서는 error만 로깅
      if (level === 'error') {
        console.error(`[${level.toUpperCase()}] ${timestamp} - ${message}`, context || '')
        // TODO: 실제 프로덕션에서는 Sentry, LogRocket 등으로 전송
      }
    }
  }

  /**
   * Debug 레벨 로그
   */
  debug(message: string, context?: LogContext): void {
    this.format('debug', message, context)
  }

  /**
   * Info 레벨 로그
   */
  info(message: string, context?: LogContext): void {
    this.format('info', message, context)
  }

  /**
   * Warning 레벨 로그
   */
  warn(message: string, context?: LogContext): void {
    this.format('warn', message, context)
  }

  /**
   * Error 레벨 로그
   */
  error(message: string, error?: Error | unknown, context?: LogContext): void {
    const errorContext = {
      ...context,
      ...(error instanceof Error
        ? {
            name: error.name,
            message: error.message,
            stack: error.stack
          }
        : { error })
    }
    this.format('error', message, errorContext)
  }
}

// Singleton 인스턴스 export
export const logger = new Logger()
