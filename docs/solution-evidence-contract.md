# SOL 솔루션 카드 근거 문장 계약

## 결정

- AI 솔루션 응답의 각 `solutionCards[]`에 `evidence`를 포함한다.
- `evidence`는 해당 카드의 제목·요약·상세 실행안을 만든 근거 문장이다.
- AI가 더 이상 반환하지 않는 `promptVersion`은 응답 DTO에서 제거한다.
- FE 조회 응답과 챗봇 요청의 `context[].evidence`에는 저장된 동일 문자열을 사용한다.
- 챗봇 요청 시 수치를 다시 계산하거나 원본 데이터로 근거 문장을 재생성하지 않는다.

## 응답 예시

```json
{
  "rankNo": 1,
  "title": "점심 시간대 할인",
  "summaryText": "점심 할인 프로모션을 제안합니다.",
  "detailText": "12시부터 14시까지 할인 행사를 진행하세요.",
  "evidence": "최근 4주간 평일 12시부터 14시까지의 주문 수가 다른 점심 시간대보다 18% 낮았습니다."
}
```

## 저장 규칙

- 컬럼: `solutions.evidence_text`
- 타입: `TEXT`
- NULL: 허용. 기존 데이터와 AI가 근거를 주지 못한 실패 대응을 위한 호환성 용도이다.
- 신규 생성 카드에는 가능한 한 비어 있지 않은 값을 저장한다.
- 응답 매핑: `solutions.evidence_text` → `solutionCards[].evidence`
- 챗봇 매핑: `solutions.evidence_text` → `context[].evidence`
