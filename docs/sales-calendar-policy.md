# 매출 달력 구현 기준

연결: #586 DTO, #587 저장 모델, #588 Service, #589 Controller, #607 검증.

## 2026-10-28 사용자 지시로 정합화한 매출 기준

사용자는 기존 매출 분석의 취소 처리 방식을 확인한 뒤 달력도 같은 기준을 사용하도록 지시했다.
따라서 기존 Notion 달력 문서의 결제 완료일·환불 완료일 별도 집계 부분을 이 구현에 적용하지 않는다.
Notion 원문은 이번 작업에서 수정하지 않았으며, 아래 내용이 해당 부분을 대체한다.

- POS `데이터 기준`의 정산 기준은 기존처럼 `주문한 날`이다.
- `상품 주문 상세내역`의 `실판매금액 (할인, 옵션 포함)`을 주문기준일자에 합산한다.
- 취소 행의 음수 수량·금액을 그대로 반영한다. 별도 환불 완료일로 옮기거나 두 번 차감하지 않는다.
- KPI/달력은 비메뉴를 포함한 `total_net_amount`를 사용한다.
- 주문 건수는 기존 `SalesOrder.valid`(`MENU` 순수량 > 0) 기준이다.
- `sales_activity_count`는 그 날짜에 저장된 원천 주문 그룹 수다. 취소만 있는 날과 0원 활동일도 포함한다.
- COMPLETE이며 활동이 있는 날만 월평균 분모에 포함한다. 미래·휴무·누락·미확인과 활동 없는 날은 금액·건수 null이다.
- 기존 매출 집계 금액·주문 건수·커버리지 상태를 변경하지 않는다. 새 환불 테이블·결제 시각 컬럼은 추가하지 않는다.

근거: 기존 `docs/v2-profit-calculation-policy.md` 2절, `TossPosWorkbookParser`,
`SalesUploadPersistenceService`, 사용자 지시(2026-10-28).

## 조회·날씨

- API 정의서 SALES-08 `/v3/sales/calendar`, SALES-09 `/v3/sales/calendar/days/{date}`를 구현한다.
- 세션 사용자 ID로 DB에서 소유 매장을 다시 결정한다. 요청·세션의 매장 ID를 조회 권한으로 신뢰하지 않는다.
- 월 범위·오늘은 서울 기준이다. 기본 선택일은 현재 월의 오늘, 과거 월의 1일이다.
- 월평균 금액은 HALF_UP 원 단위로 표시하되 HIGH/LOW 비교는 반올림 전 평균으로 한다.
- 평균 <= 0 또는 활동 없음이면 수준은 UNAVAILABLE, 비교 비율은 null이다.
- 기존 ASOS 스케줄러에서 정오 조건을 적재한다. GET에서는 월별 요약과 관측값만 조회한다.
- ASOS 시간자료의 12시 행만 사용한다. 3시간 신적설 > 0이면 SNOW, 시간 강수 > 0이면 RAIN,
  그 외 유효 전운량 0~2는 SUNNY, 3~10은 CLOUDY다. 강수 품질 오류/결측·운량 결측은 저장하지 않는다.
- ASOS 시간자료는 전일까지 제공하므로 당일 관측이 없으면 weather=null이다.
- 공식 원천: https://www.data.go.kr/data/15057210/openapi.do

## DB 적용

`20261026-add-sales-calendar.sql`은 기존 DB용이다. 새 DB는 init SQL을 사용한다.
기존 주문 그룹에서 활동 건수만 백필하며 과거 day_status를 COMPLETE로 바꾸지 않는다.
분류 통일과 함께 배포할 때는 BE·Worker 드레인·중지 → 백업 →
`20261029-prepare-sales-category-varchar.sql` → `20261026-add-sales-calendar.sql` →
`20261027-unify-sales-menu-categories.sql` → 스키마·데이터 보존 확인 → 신버전 기동 순서다.
날짜순으로 실행하지 않으며 이미 적용된 SQL을 재실행하지 않는다.
정확한 배포 SHA/digest 기록, 단계별 검증과 복원 절차는 [카테고리 정책](sales-category-policy.md)을 따른다.
운영 DB에는 이번 작업에서 적용하지 않았다.
