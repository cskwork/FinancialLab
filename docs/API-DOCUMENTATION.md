# FinLab API Documentation

## 개요

FinLab API는 포트폴리오 관리, 거래 추적, 백테스팅 및 재무 지표 계산을 위한 RESTful API입니다. 이 API는 Mock 모드와 Live 모드를 모두 지원하여 개발 및 프로덕션 환경에서 유연하게 사용할 수 있습니다.

**Base URL**
- Mock Mode: `/api/v1`
- Live Mode: `https://your-backend-url.com/api/v1`

**인증 방식**
- Bearer Token (JWT)
- Header: `Authorization: Bearer <token>`

**표준 응답 형식**

성공 응답:
```json
{
  "data": { ... },
  "meta": {
    "requestId": "uuid",
    "timestamp": "2025-10-03T12:00:00Z"
  }
}
```

에러 응답:
```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "Human readable message",
    "details": {
      "field": "fieldName",
      "constraint": "validation rule"
    }
  }
}
```

---

## 인증 (Authentication)

### POST /v1/auth/login

사용자 로그인 및 JWT 토큰 발급

**Request Body**
```json
{
  "email": "user@example.com"
}
```

**Response**
```json
{
  "userId": 1,
  "email": "user@example.com",
  "role": "USER",
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Status Codes**
- `200 OK`: 로그인 성공
- `400 Bad Request`: 잘못된 이메일 형식
- `401 Unauthorized`: 인증 실패

---

## 포트폴리오 (Portfolios)

### GET /v1/portfolios

사용자의 모든 포트폴리오 조회

**Headers**
```
Authorization: Bearer <token>
```

**Response**
```json
[
  {
    "id": 1,
    "userId": 1,
    "name": "My Portfolio",
    "baseCurrency": "USD",
    "createdAt": "2025-10-01T00:00:00Z",
    "updatedAt": "2025-10-03T00:00:00Z"
  }
]
```

**Status Codes**
- `200 OK`: 성공
- `401 Unauthorized`: 인증 필요

---

### POST /v1/portfolios

새 포트폴리오 생성

**Request Body**
```json
{
  "name": "Tech Portfolio",
  "baseCurrency": "USD"
}
```

**Response**
```json
{
  "id": 2,
  "userId": 1,
  "name": "Tech Portfolio",
  "baseCurrency": "USD",
  "createdAt": "2025-10-03T12:00:00Z",
  "updatedAt": "2025-10-03T12:00:00Z"
}
```

**Validation**
- `name`: 1-100자, 필수
- `baseCurrency`: 정확히 3자 (예: USD, KRW, EUR)

**Status Codes**
- `201 Created`: 생성 성공
- `400 Bad Request`: 유효성 검증 실패
- `401 Unauthorized`: 인증 필요

---

### GET /v1/portfolios/{id}

특정 포트폴리오 상세 조회

**Path Parameters**
- `id` (number): 포트폴리오 ID

**Response**
```json
{
  "id": 1,
  "userId": 1,
  "name": "My Portfolio",
  "baseCurrency": "USD",
  "createdAt": "2025-10-01T00:00:00Z",
  "updatedAt": "2025-10-03T00:00:00Z"
}
```

**Status Codes**
- `200 OK`: 성공
- `404 Not Found`: 포트폴리오를 찾을 수 없음
- `403 Forbidden`: 접근 권한 없음

---

### PATCH /v1/portfolios/{id}

포트폴리오 정보 수정

**Request Body**
```json
{
  "name": "Updated Portfolio Name"
}
```

**Response**
```json
{
  "id": 1,
  "userId": 1,
  "name": "Updated Portfolio Name",
  "baseCurrency": "USD",
  "createdAt": "2025-10-01T00:00:00Z",
  "updatedAt": "2025-10-03T12:30:00Z"
}
```

**Status Codes**
- `200 OK`: 수정 성공
- `404 Not Found`: 포트폴리오를 찾을 수 없음
- `403 Forbidden`: 접근 권한 없음

---

### DELETE /v1/portfolios/{id}

포트폴리오 삭제

**Response**
```
204 No Content
```

**Status Codes**
- `204 No Content`: 삭제 성공
- `404 Not Found`: 포트폴리오를 찾을 수 없음
- `403 Forbidden`: 접근 권한 없음

---

## 거래 (Transactions)

### GET /v1/portfolios/{id}/transactions

포트폴리오의 모든 거래 조회

**Query Parameters**
- `startDate` (optional): ISO 8601 datetime
- `endDate` (optional): ISO 8601 datetime
- `type` (optional): BUY | SELL | DEPOSIT | WITHDRAW
- `symbol` (optional): 종목 심볼

**Response**
```json
[
  {
    "id": 1,
    "portfolioId": 1,
    "type": "BUY",
    "symbol": "AAPL",
    "qty": 10,
    "price": 150.00,
    "fee": 0.99,
    "timestamp": "2025-10-01T14:30:00Z",
    "note": "Initial purchase"
  },
  {
    "id": 2,
    "portfolioId": 1,
    "type": "DEPOSIT",
    "fee": 0,
    "timestamp": "2025-10-01T10:00:00Z",
    "note": "Initial funding"
  }
]
```

**Status Codes**
- `200 OK`: 성공
- `400 Bad Request`: 잘못된 쿼리 파라미터
- `403 Forbidden`: 접근 권한 없음

---

### POST /v1/portfolios/{id}/transactions

새 거래 추가

**Request Body**

매수/매도 거래:
```json
{
  "portfolioId": 1,
  "type": "BUY",
  "symbol": "AAPL",
  "qty": 10,
  "price": 150.00,
  "fee": 0.99,
  "timestamp": "2025-10-03T14:30:00Z",
  "note": "Optional note"
}
```

입금/출금 거래:
```json
{
  "portfolioId": 1,
  "type": "DEPOSIT",
  "fee": 0,
  "timestamp": "2025-10-03T10:00:00Z",
  "note": "Monthly contribution"
}
```

**Validation**
- BUY/SELL: `symbol`, `qty`, `price` 필수
- DEPOSIT/WITHDRAW: `symbol`, `qty`, `price` 불필요
- `qty`: 양수
- `price`: 양수
- `fee`: 0 이상

**Response**
```json
{
  "id": 3,
  "portfolioId": 1,
  "type": "BUY",
  "symbol": "AAPL",
  "qty": 10,
  "price": 150.00,
  "fee": 0.99,
  "timestamp": "2025-10-03T14:30:00Z",
  "note": "Optional note"
}
```

**Status Codes**
- `201 Created`: 생성 성공
- `400 Bad Request`: 유효성 검증 실패
- `403 Forbidden`: 접근 권한 없음

---

### POST /v1/portfolios/{id}/transactions/bulk

대량 거래 일괄 추가 (CSV 임포트용)

**Request Body**
```json
{
  "transactions": [
    {
      "type": "BUY",
      "symbol": "AAPL",
      "qty": 10,
      "price": 150.00,
      "fee": 0.99,
      "timestamp": "2025-10-01T14:30:00Z"
    },
    {
      "type": "BUY",
      "symbol": "GOOGL",
      "qty": 5,
      "price": 140.00,
      "fee": 0.99,
      "timestamp": "2025-10-02T14:30:00Z"
    }
  ]
}
```

**Response**
```json
{
  "created": 2,
  "failed": 0,
  "errors": []
}
```

**Status Codes**
- `201 Created`: 일부 또는 전체 성공
- `400 Bad Request`: 잘못된 요청 형식
- `403 Forbidden`: 접근 권한 없음

---

## 보유 종목 (Holdings)

### GET /v1/portfolios/{id}/holdings

포트폴리오의 현재 보유 종목 조회 (거래 내역에서 자동 계산)

**Response**
```json
[
  {
    "id": 1,
    "portfolioId": 1,
    "symbol": "AAPL",
    "shares": 10.5,
    "avgCost": 148.50,
    "marketValue": 1575.00,
    "unrealizedPnL": 17.25,
    "lastUpdated": "2025-10-03T12:00:00Z"
  }
]
```

**계산 방식**
- FIFO (First In First Out) 방식으로 평균 단가 계산
- `unrealizedPnL` = (현재가 - 평균단가) × 보유수량
- 매도 시 가장 오래된 매수분부터 차감

**Status Codes**
- `200 OK`: 성공
- `403 Forbidden`: 접근 권한 없음

---

## 재무 지표 (Metrics)

### GET /v1/metrics/portfolio/{id}

포트폴리오의 리스크 및 수익률 지표 조회

**Response**
```json
{
  "id": 1,
  "portfolioId": 1,
  "asOf": "2025-10-03T12:00:00Z",
  "totalValue": 10500.00,
  "cash": 500.00,
  "pnlDaily": 125.50,
  "pnlYTD": 850.00,
  "volatility": 0.18,
  "sharpe": 1.25,
  "maxDrawdown": -0.12
}
```

**지표 설명**
- `totalValue`: 현재 포트폴리오 총 가치
- `cash`: 현금 잔고
- `pnlDaily`: 일일 손익
- `pnlYTD`: 연초 대비 손익
- `volatility`: 연간 변동성 (σ × √252)
- `sharpe`: 샤프 비율 = (수익률 - 무위험수익률) / 변동성
- `maxDrawdown`: 최대 낙폭 (peak to trough)

**Status Codes**
- `200 OK`: 성공
- `403 Forbidden`: 접근 권한 없음

---

## 목표 (Goals)

### GET /v1/portfolios/{id}/goals

포트폴리오의 재무 목표 목록 조회

**Response**
```json
[
  {
    "id": 1,
    "portfolioId": 1,
    "name": "Retirement Fund",
    "targetAmount": 1000000.00,
    "targetDate": "2045-12-31T00:00:00Z",
    "currentAmount": 50000.00
  }
]
```

**Status Codes**
- `200 OK`: 성공
- `403 Forbidden`: 접근 권한 없음

---

### POST /v1/portfolios/{id}/goals

새 재무 목표 생성

**Request Body**
```json
{
  "portfolioId": 1,
  "name": "Emergency Fund",
  "targetAmount": 10000.00,
  "targetDate": "2026-12-31T00:00:00Z",
  "currentAmount": 2000.00
}
```

**Validation**
- `name`: 1-100자, 필수
- `targetAmount`: 양수
- `targetDate`: 유효한 ISO 8601 datetime
- `currentAmount`: 0 이상

**Response**
```json
{
  "id": 2,
  "portfolioId": 1,
  "name": "Emergency Fund",
  "targetAmount": 10000.00,
  "targetDate": "2026-12-31T00:00:00Z",
  "currentAmount": 2000.00
}
```

**Status Codes**
- `201 Created`: 생성 성공
- `400 Bad Request`: 유효성 검증 실패
- `403 Forbidden`: 접근 권한 없음

---

## 적립식 투자 계획 (Plans)

### GET /v1/portfolios/{id}/plans

포트폴리오의 DCA 투자 계획 목록 조회

**Response**
```json
[
  {
    "id": 1,
    "portfolioId": 1,
    "symbol": "SPY",
    "amountPerInterval": 500.00,
    "interval": "MONTHLY",
    "startDate": "2025-01-01T00:00:00Z",
    "endDate": "2030-12-31T00:00:00Z",
    "isActive": true
  }
]
```

**Status Codes**
- `200 OK`: 성공
- `403 Forbidden`: 접근 권한 없음

---

### POST /v1/portfolios/{id}/plans

새 투자 계획 생성

**Request Body**
```json
{
  "portfolioId": 1,
  "symbol": "VTI",
  "amountPerInterval": 1000.00,
  "interval": "WEEKLY",
  "startDate": "2025-10-03T00:00:00Z",
  "endDate": "2027-10-03T00:00:00Z",
  "isActive": true
}
```

**Validation**
- `symbol`: 필수
- `amountPerInterval`: 양수
- `interval`: WEEKLY | MONTHLY
- `startDate`: 유효한 datetime
- `endDate`: 선택사항, startDate 이후여야 함

**Response**
```json
{
  "id": 2,
  "portfolioId": 1,
  "symbol": "VTI",
  "amountPerInterval": 1000.00,
  "interval": "WEEKLY",
  "startDate": "2025-10-03T00:00:00Z",
  "endDate": "2027-10-03T00:00:00Z",
  "isActive": true
}
```

**Status Codes**
- `201 Created`: 생성 성공
- `400 Bad Request`: 유효성 검증 실패
- `403 Forbidden`: 접근 권한 없음

---

## 백테스팅 (Backtesting)

### POST /v1/backtests/dca

DCA (Dollar Cost Averaging) 전략 백테스트

**Request Body**
```json
{
  "symbol": "SPY",
  "startDate": "2020-01-01",
  "endDate": "2025-10-03",
  "amountPerInterval": 1000,
  "interval": "MONTHLY"
}
```

**Response**
```json
{
  "strategy": "DCA",
  "symbol": "SPY",
  "startDate": "2020-01-01",
  "endDate": "2025-10-03",
  "amountPerInterval": 1000,
  "interval": "MONTHLY",
  "finalValue": 72500.50,
  "totalInvested": 70000.00,
  "cagr": 0.085,
  "sharpe": 1.45,
  "maxDrawdown": -0.32,
  "volatility": 0.18
}
```

**계산 방식**
- 매 interval마다 고정 금액으로 매수 (종가 기준)
- 단위 미만 주식 허용 (fractional shares)
- 수수료는 0으로 가정

**Status Codes**
- `200 OK`: 성공
- `400 Bad Request`: 잘못된 파라미터
- `404 Not Found`: 시세 데이터 없음

---

## 시세 데이터 (Market Data)

### GET /v1/market/symbols

사용 가능한 종목 목록 조회

**Response**
```json
[
  {
    "symbol": "AAPL",
    "name": "Apple Inc.",
    "exchange": "NASDAQ"
  },
  {
    "symbol": "SPY",
    "name": "SPDR S&P 500 ETF Trust",
    "exchange": "NYSE"
  }
]
```

**Status Codes**
- `200 OK`: 성공

---

### GET /v1/market/candles

OHLCV 시세 데이터 조회

**Query Parameters**
- `symbol` (required): 종목 심볼
- `startDate` (optional): YYYY-MM-DD 형식
- `endDate` (optional): YYYY-MM-DD 형식

**Response**
```json
[
  {
    "symbol": "AAPL",
    "date": "2025-10-01",
    "open": 149.50,
    "high": 151.20,
    "low": 148.80,
    "close": 150.75,
    "volume": 65432100
  },
  {
    "symbol": "AAPL",
    "date": "2025-10-02",
    "open": 150.80,
    "high": 152.00,
    "low": 150.00,
    "close": 151.50,
    "volume": 58765400
  }
]
```

**Status Codes**
- `200 OK`: 성공
- `400 Bad Request`: 필수 파라미터 누락
- `404 Not Found`: 데이터 없음

---

## Rate Limiting

Mock API는 토큰 버킷 알고리즘을 사용하여 Rate Limiting을 시뮬레이션합니다.

**제한 사항**
- 기본: 분당 60 요청
- 인증된 사용자: 분당 120 요청
- 대량 작업: 분당 10 요청

**응답 헤더**
```
X-RateLimit-Limit: 60
X-RateLimit-Remaining: 45
X-RateLimit-Reset: 1696339200
```

**429 Too Many Requests**
```json
{
  "error": {
    "code": "RATE_LIMIT_EXCEEDED",
    "message": "Too many requests. Please retry after 30 seconds.",
    "details": {
      "retryAfter": 30
    }
  }
}
```

---

## 에러 코드 목록

| 코드 | 설명 |
|------|------|
| `VALIDATION_ERROR` | 요청 데이터 유효성 검증 실패 |
| `AUTHENTICATION_REQUIRED` | 인증 토큰이 없거나 유효하지 않음 |
| `FORBIDDEN` | 접근 권한 없음 |
| `NOT_FOUND` | 리소스를 찾을 수 없음 |
| `RATE_LIMIT_EXCEEDED` | API 요청 제한 초과 |
| `INTERNAL_SERVER_ERROR` | 서버 내부 오류 |
| `SERVICE_UNAVAILABLE` | 서비스 일시적으로 이용 불가 |

---

## 예제 코드

### JavaScript/TypeScript

```typescript
// 로그인
const loginResponse = await fetch('/api/v1/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: 'user@example.com' })
})
const { token } = await loginResponse.json()

// 포트폴리오 목록 조회
const portfoliosResponse = await fetch('/api/v1/portfolios', {
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  }
})
const portfolios = await portfoliosResponse.json()

// 거래 추가
const transactionResponse = await fetch('/api/v1/portfolios/1/transactions', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    portfolioId: 1,
    type: 'BUY',
    symbol: 'AAPL',
    qty: 10,
    price: 150.00,
    fee: 0.99,
    timestamp: new Date().toISOString()
  })
})
```

---

## 버전 관리

API는 `/v1` 네임스페이스를 사용하여 버전을 관리합니다. 향후 호환되지 않는 변경사항이 발생할 경우 새로운 버전 (예: `/v2`)으로 제공됩니다.

**Deprecation 헤더**
```
X-Api-Deprecated: true
X-Api-Sunset: 2026-12-31
X-Api-Replacement: /v2/portfolios
```

---

## 지원 및 문의

문제가 발생하거나 질문이 있는 경우:
- GitHub Issues: https://github.com/yourusername/finlab/issues
- Email: support@finlab.app
