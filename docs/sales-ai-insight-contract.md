# SALES AI 인사이트 내부 API 계약 (V1)

## 1. 결정 사항

- 인사이트는 규칙 기반이 아니라 **LLM**으로 생성한다.
- 인사이트는 매출 금액을 언급할 수 있다.
- V1은 **월 단위 인사이트 1개**를 생성·저장한다. 화면의 오늘/이번 주/직접 설정 필터는 차트와 KPI에만 적용하고, 인사이트는 `targetMonth` 기준으로 표시한다.
- 업로드 파일이 여러 달을 포함하면 파일의 `periodEnd`가 속한 최신 월을 `targetMonth`로 사용한다.
- 원본 파일과 주문 데이터는 AI 서버에 보내지 않는다. BE가 집계한 지표만 전달한다.
- `totalSales`는 화면 상단 총 매출 KPI와 같은 값이며 총액 언급에만 사용한다.
- `menuSales`는 메뉴·카테고리 비중과 상세 패턴의 기준이다. 주차권, 선불카드 충전 등 비메뉴 매출을 제외한다.
- 예측 API의 기준은 기존 합의대로 `menuSales`이며 이 계약으로 변경하지 않는다.
- 주 단위 요일 추이(예: “3주 연속 감소”)는 V2로 미룬다.

## 2. 호출 방식

- Method/Path: `POST /internal/v1/ai/sales-insights`
- 방향: BE → AI
- 처리: 동기 호출, Callback 없음
- 인증: 운영 환경은 `Authorization: Bearer {INTERNAL_AI_TOKEN}`
- 멱등키: `Idempotency-Key: sales-insight:{analysisRunId}`
- Timeout: 연결 3초, 응답 30초
- Retry: 연결 실패, HTTP 502/503/504만 500ms 후 1회 재시도

## 3. 요청

```json
{
  "storeId": 1,
  "salesAnalysisId": 56,
  "analysisRunId": 34,
  "targetMonth": "2026-09",
  "triggerType": "UPLOAD",
  "metrics": {
    "salesSummary": {
      "totalSales": 7920000,
      "menuSales": 7480000,
      "orderCount": 923,
      "averageOrderValue": 8581,
      "vsPrevPeriod": 0.042
    },
    "salesTrend": [
      { "date": "2026-09-12", "menuSales": 2140000, "orderCount": 231 }
    ],
    "weekdaySales": [
      { "dayOfWeek": "SATURDAY", "menuSales": 1560000, "orderCount": 182 }
    ],
    "hourlySales": [
      { "dayType": "WEEKDAY", "hour": 12, "menuSales": 420000, "orderCount": 55 }
    ],
    "categorySales": [
      { "categoryName": "커피", "menuSales": 3120000, "ratio": 0.417, "vsPrevPeriod": -0.044 }
    ],
    "menuRankings": [
      { "rank": 1, "menuName": "아메리카노", "menuSales": 2108000, "quantity": 620, "ratio": 0.282, "vsPrevPeriod": 0.031 }
    ]
  },
  "maxInsightCount": 3
}
```

### 필드 규칙

| 필드 | 규칙 |
|---|---|
| `targetMonth` | `yyyy-MM`, 월간 인사이트 기준 월 |
| `triggerType` | `UPLOAD` 또는 `RETRY` |
| `totalSales` | 전체 유효 주문의 순매출 합계, 화면 KPI와 동일 |
| `menuSales` | `MENU` 항목 순매출 합계, 상세 지표 및 비중 계산 기준 |
| `averageOrderValue` | `totalSales / orderCount` |
| `vsPrevPeriod` | 이전 동기간 대비 증감률. 4.2% 증가를 `0.042`로 표현하며 비교 데이터가 없으면 `null` |
| `ratio` | `menuSales` 대비 비중, `0.0` 이상 `1.0` 이하 |
| `hourlySales.dayType` | `WEEKDAY` 또는 `WEEKEND` |
| 금액 | 원 단위, 0 이상의 정수 값으로 전달 |
| `maxInsightCount` | 1~3, 기본 운영값 3 |

`salesTrend`, `weekdaySales`, `hourlySales`, `categorySales`, `menuRankings`의 매출은 모두 `menuSales` 기준이다.
`salesSummary.vsPrevPeriod`, `categorySales[].vsPrevPeriod`, `menuRankings[].vsPrevPeriod`는 모두 `null`을 허용하며, AI는 `null`인 비교값을 인사이트에서 언급하지 않는다.

## 4. 응답

### 성공 — HTTP 200

```json
{
  "message": "매출 AI 인사이트를 생성했습니다.",
  "status": "COMPLETED",
  "data": {
    "targetMonth": "2026-09",
    "insights": [
      "9월 총 매출은 7,920,000원으로 이전 기간보다 4.2% 증가했습니다.",
      "아메리카노 매출은 2,108,000원으로 메뉴 매출의 28.2%를 차지했습니다."
    ]
  }
}
```

### 데이터 부족 — HTTP 200

BE가 월간 영업일 데이터 14일 미만임을 확인하면 AI를 호출하지 않는 것이 원칙이다. AI가 추가로 데이터 부족을 판단한 경우 아래 형식으로 응답한다.

```json
{
  "message": "AI 인사이트에 필요한 데이터가 부족합니다.",
  "status": "INSUFFICIENT_DATA",
  "data": {
    "missingData": ["SALES_HISTORY"]
  }
}
```

### 실패

```json
{
  "message": "매출 AI 인사이트 생성에 실패했습니다.",
  "status": "FAILED",
  "error": {
    "code": "INSIGHT_GENERATION_FAILED",
    "retryable": false
  },
  "data": null
}
```

| HTTP | 의미 | BE 재시도 |
|---|---|---|
| 401 | 내부 인증 실패 | 안 함 |
| 422 | JSON/필드 검증 실패 | 안 함 |
| 500 | AI 생성 실패 | 안 함 |
| 502 | 외부 모델 공급자 오류 | 1회 |
| 503 | AI 서버 일시 장애 | 1회 |
| 504 | AI 응답 Timeout | 1회 |

AI 내부 오류 메시지는 사용자에게 노출하지 않는다. 사용자용 메시지는 BE가 생성한다.

AI 호출이 실패해도 원본 파일 저장, 주문 정제, 통계 저장과 기본 매출 분석은 완료 처리한다. `sales_ai_insights.status`만 `FAILED`로 저장해 이후 재시도할 수 있게 한다.

## 5. 인사이트 생성 규칙

- 1~3개의 비어 있지 않은 한국어 존댓말 문장
- 문장당 최대 100자
- 제공받은 지표만 사용하고 추측하지 않는다.
- 총 매출을 말할 때는 `salesSummary.totalSales`만 사용한다.
- 메뉴·카테고리·일별·요일별·시간대별 패턴을 말할 때는 `menuSales` 계열 필드만 사용한다.
- 같은 내용을 중복하지 않고, 전달받은 숫자를 임의로 재계산하거나 변경하지 않는다.
- V1 입력에 없는 여러 주의 요일 추세를 추론하지 않는다.

## 6. V2 보류 항목

- `periodType`, `startDate`, `endDate`를 사용한 오늘/주/월/직접 설정별 인사이트
- 최근 4주 × 7요일의 주 단위 요일 추이
- `modelVersion`, `promptVersion` 응답 저장

## 7. 화면·ERD 후속 반영

- 화면설계서의 “선택 기간에 따라 인사이트 갱신”을 “월간 인사이트” 문구로 변경한다.
- V1 ERD의 `UNIQUE (store_id, target_month)`는 유지한다.
- 데이터 부족을 별도 보존하기 위해 상태값에 `INSUFFICIENT_DATA`를 포함한다.
- 향후 기간별 생성으로 변경할 때만 기간 컬럼과 UNIQUE 키를 함께 변경한다.

`GET /v1/sales/analyses`는 조회 종료일이 속한 월의 `COMPLETED` 인사이트를 다음 형태로 함께 반환한다.

```json
{
  "aiInsight": {
    "targetMonth": "2026-09",
    "insights": [
      "9월 총 매출은 7,920,000원으로 이전 기간보다 4.2% 증가했습니다."
    ],
    "generatedAt": "2026-09-08T00:00:00+09:00"
  }
}
```

저장된 인사이트가 없거나 상태가 `PENDING`, `GENERATING`, `INSUFFICIENT_DATA`, `FAILED`이면 기본 매출 분석 응답은 그대로 `COMPLETED`로 반환하고 `aiInsight`만 `null`로 반환한다.
