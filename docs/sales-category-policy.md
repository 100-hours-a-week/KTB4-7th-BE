# 메뉴판·POS 매출 공통 카테고리

2026-10-27 사용자 승인: 메뉴판뿐 아니라 POS 파싱·매출 저장·분석·AI 입력까지
`COFFEE`, `TEA`, `BEVERAGE`, `CAKE`, `BAKERY`, `OTHER` 6종으로 통일한다.
연관 이슈: #598(분류·집계), #600(저장·데이터 전환), #601(회귀 검증).
기존 #451 / PR #508의 매출 분석 제외 범위를 이번 작업에서 확장한다.

요구사항 근거는 `FR-ANALYSIS-001`, `BR-UPLOAD-001`, `NFR-DATA-001`이며,
[API 정의서](https://app.notion.com/p/4011a2afa1b48021aa3edafe56bfb4dc)의 메뉴판 공통 코드와 동일한 값을 사용한다.
기존 ERD의 `sales_order_items.standard_category VARCHAR(30) NOT NULL` 및 `category_raw`를 유지한다.
새 테이블·컬럼·API·외부 AI 호출은 추가하지 않는다.

## 단일 정의와 분류

- `com.memme.entity.store.MenuCategory`만 사용한다. 매출 전용 중복 enum은 삭제한다.
- 표시 이름도 같은 enum에서 `커피`, `차`, `음료`, `케이크`, `베이커리`, `기타`로 결정한다.
- POS 규칙은 `src/main/resources/sales/menu-category-rules.properties`에 둔다.
  한국어·영어 라벨을 인식하고 영어는 단어 경계를 확인한다. `steak`는 `tea`로 분류하지 않는다.
- 라벨이 한 분류로 명확하면 해당 분류를 사용한다. 없거나 알 수 없거나 여러 분류가 섞이면
  메뉴명으로 보완한다. 특정 매장의 시즌명을 하드코딩하지 않는다.
- 맛 표현(커피 케이크·녹차 스콘)은 케이크·베이커리 분류를 우선한다.
  판단 불가 항목은 `OTHER`다. 원본 라벨, 금액, 취소, 비메뉴 구분은 보존한다.
- `non`과 `NON_COFFEE`는 `BEVERAGE`다. 논커피를 차로 단정하지 않는다.
- `디저트` 같은 넓은 라벨만으로 케이크/베이커리를 단정하지 않는다.

## 기존 데이터 전환

| 기존 저장 코드 | 전환 |
| --- | --- |
| COFFEE·TEA·BEVERAGE·CAKE·BAKERY·OTHER | 보존 |
| NON_COFFEE·ADE·SMOOTHIE·BOTTLED_DRINK·BEER | BEVERAGE |
| FOOD·SEASONAL·UNMAPPED 등 나머지 | 원본 라벨 → 메뉴명으로 재분류, 판단 불가 시 OTHER |

`20261027-unify-sales-menu-categories.sql`은 파서와 같은 규칙 파일에서 생성한다.
적용 전 `python3 scripts/generate-sales-category-migration.py --check`로 일치 여부를 확인한다.
배포 후 적용된 SQL은 수정하지 않고, 규칙 변경 시 새 버전의 데이터 전환을 작성한다.

## 운영 native ENUM 선행 변경 (#630)

운영의 구 12종 native ENUM에는 `BEVERAGE`·`OTHER`가 없어 기존 분류 SQL만 실행하면
1265 오류가 발생한다. 신규 DB와 ERD는 `VARCHAR(30) NOT NULL`이므로
`20261029-prepare-sales-category-varchar.sql`로 타입부터 맞춘다.
이 SQL은 기존 문자열 값을 보존하며, 분류 갱신·6종 CHECK 추가는 기존 생성 SQL이 수행한다.
이미 VARCHAR인 기존 DB에도 사용할 수 있지만 신규 설치에는 별도 실행하지 않는다.

## 적용 순서와 확인

파일명 날짜순이 아니라 **아래 순서**로 Cloud 담당자가 수동 적용한다.

1. 보완 변경이 병합된 `dev`의 정확한 커밋 SHA, 해당 SHA의 CI 성공과 ECR 이미지 digest를 기록한다.
   이 문서 작성이나 로컬 검사 성공을 머지·CI 성공·운영 배포 완료로 간주하지 않는다.
2. BE 신규 요청과 업로드·분석 작업 유입을 막고, 진행 작업을 드레인한 뒤 BE·Worker를 모두 중지한다.
   구버전은 신규 `BEVERAGE`/`OTHER`를 읽을 수 없으므로 신구 버전을 동시에 실행하지 않는다.
3. 쓰기가 멈춘 상태에서 복원 가능한 DB 스냅샷 또는 스키마·데이터 백업을 확보한다.
   `SHOW CREATE TABLE sales_order_items`로 원래 ENUM 정의·순서·NULL·기본값·collation도 기록한다.
   달력 변경 대상 `sales_daily_summaries`, `weather_calendar_conditions` 존재 여부와 관련 데이터도 백업에 포함한다.
   별도 DB에서 복원이 되는지 확인하고 아래 기준값을 저장한다.
4. `sales_activity_count`와 `weather_calendar_conditions`가 아직 없는지 확인한다.
   달력 FK의 참조 대상 `weather_stations.id`가 `BIGINT UNSIGNED`인지 확인한다.
   이미 일부 적용됐거나 스키마가 다르면 전체 파일을 재실행하지 말고 적용 상태를 확인한다.
5. **`20261029-prepare-sales-category-varchar.sql`**을 실행한다.
   타입이 `varchar(30)`, `IS_NULLABLE=NO`이고 기존 분류별 건수·금액·수량·원본이 그대로인지 확인한다.
6. **`20261026-add-sales-calendar.sql`**을 한 번 실행한다.
   활동 건수 백필, 날씨 테이블과 제약을 확인한다. 기존 일별 매출 금액·주문 건수·`day_status`는 유지한다.
7. **`20261027-unify-sales-menu-categories.sql`**을 DB 연결 하나에서 한 번 실행한다.
   임시 테이블에서 결과를 계산한 뒤 트랜잭션으로 갱신하므로 문장을 서로 다른 연결로 나누지 않는다.
8. 분류가 6종으로 제한되고 행 수·금액·수량·원본 값이 보존됐는지 확인한다.
   `ck_sales_item_standard_category` CHECK가 존재하고 ENFORCED=YES인지 확인한다.
   달력 활동 건수도 원천 주문 그룹 수와 대조한다.
9. 확인한 SHA/digest의 신버전 BE·Worker를 기동한다. Health Check, 달력 월/일 조회,
   분류별 분석 조회·새 업로드를 스모크 테스트하고 결과를 기록한다.

타입 변경과 CHECK 추가, 달력 DDL은 MySQL의 암묵적 커밋을 일으킨다.
전체 작업이 하나의 트랜잭션으로 원복되지는 않는다. 단계별 실패 시 후속 SQL과 앱 기동을 멈추고,
실제 스키마·데이터 상태를 확인한 다음 아래 백업 복원 절차를 따른다. SQL 클라이언트의 오류 무시 옵션은 사용하지 않는다.

사전 기준값과 적용 후 값을 각각 저장한다. Cloud가 전달한 675,707행(메뉴 항목),
291,452행(분류 전환 대상)은 당시 조회값이며 현재의 고정 기대값으로 사용하지 않는다.
아래 전체 행 집계는 비메뉴도 포함하므로 메뉴 항목 수와 구분한다.

```sql
SHOW CREATE TABLE sales_order_items;
SELECT COLUMN_TYPE, IS_NULLABLE, CHARACTER_MAXIMUM_LENGTH
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_order_items'
  AND COLUMN_NAME = 'standard_category';
SELECT standard_category, COUNT(*) AS item_count, SUM(net_amount), SUM(quantity)
FROM sales_order_items GROUP BY standard_category;
SELECT COUNT(*) AS item_count, SUM(net_amount), SUM(quantity) FROM sales_order_items;
SELECT COUNT(*) AS conversion_target FROM sales_order_items
WHERE standard_category NOT IN ('COFFEE','TEA','BEVERAGE','CAKE','BAKERY','OTHER');
```

최종 `conversion_target`은 0이어야 한다. 합계 비교와 함께 백업 대비 ID별 원본 라벨·메뉴명·금액·수량 등
분류 외 컬럼이 같음을 확인한다. 달력 적용 후 다음 불일치 수도 0이어야 한다.

```sql
SELECT COUNT(*) AS activity_mismatch
FROM sales_daily_summaries AS summary
LEFT JOIN (
  SELECT store_id, DATE(ordered_at) AS sales_date, COUNT(*) AS activity_count
  FROM sales_orders GROUP BY store_id, DATE(ordered_at)
) AS activity ON activity.store_id = summary.store_id AND activity.sales_date = summary.sales_date
WHERE summary.sales_activity_count <> COALESCE(activity.activity_count, 0);
SELECT CONSTRAINT_NAME, ENFORCED FROM information_schema.TABLE_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_order_items'
  AND CONSTRAINT_NAME = 'ck_sales_item_standard_category';
```

## 실패 시 복원

앱 기동 전까지 쓰기가 멈춘 배포 창에서는 다음 순서로 분류를 복원한다.

1. BE·Worker 중지 상태를 유지한다. 이미 기동했다면 다시 중지하고 새 쓰기 유무를 확인한다.
2. 분류 CHECK가 추가됐으면 `ALTER TABLE sales_order_items DROP CHECK ck_sales_item_standard_category`로 제거한다.
   실패 단계상 CHECK가 없으면 이 단계는 생략한다.
3. VARCHAR 상태에서 백업의 ID별 원래 `standard_category`를 복원한다.
   백업과 현재 ID 집합이 동일하고 분류 외 데이터가 보존됐는지 확인한다.
4. 데이터가 구 코드로 복원된 **뒤에**, 기록해 둔 원래 컬럼 정의로 타입을 복원한다.
   native ENUM이었다면 ENUM 값의 순서·NULL·기본값·collation까지 백업 정의를 따른다.
   `BEVERAGE`·`OTHER`가 남은 상태에서 ENUM부터 되돌리면 다시 실패한다.
5. 전체 행과 원래 분류별 집계가 백업과 일치하는지 확인한다. 달력까지 원복해야 하면 백업의
   스키마·데이터를 함께 복원하고 구버전 스키마 호환성을 확인한 뒤 기존 정상 버전을 시작한다.

`BEVERAGE`를 일괄 `NON_COFFEE`로 바꾸는 식의 역매핑으로는 원래 분류를 복구할 수 없다.
신버전 기동 후 새 쓰기가 발생했거나 ID 집합이 달라졌다면 단순 분류 복원 절차를 실행하지 않는다.
새 데이터 보존 및 백업 시점 이후 변경분 처리 방식을 Cloud와 확정한 뒤 복원한다.

## 분석·AI 입력

화면 분석·인사이트의 `categoryName`, 솔루션의 `categoryBreakdown[].name`은 위 6개 표시 이름만 사용한다.
MENU 항목만 집계하고 취소 금액은 기존 규칙대로 반영한다. 비율·전기 대비 증감률 계산은 유지한다.
이미 저장된 AI 문장·솔루션은 과거 결과로 보존하며 이 SQL에서 다시 생성하지 않는다.

`OTHER`에는 실제 기타 메뉴와 분류 근거가 부족한 항목이 모두 포함된다. 이번 변경만으로 분류 신뢰도나
AI 카드 3장 생성을 보장하지 않는다. 가용성 신호를 추가하려면 AI와 별도의 필드 계약을 정해야 한다.
원본 라벨이 넓거나 모호한 매장은 검토가 필요하다.

## 재현 검증

- `./gradlew test` 및 `./gradlew build`
- `python3 scripts/verify-sales-category-migration.py`: 네트워크·포트를 열지 않는 일회용 MySQL 8.4 컨테이너에서
  VARCHAR와 구 12종 native ENUM 각각에서 Java와 같은 CSV 사례를 검사한다.
  ENUM의 기존 SQL 1265 실패 재현, 선행 타입 변경의 값 보존, 분류 전환, 원본·금액·수량·행 수 보존,
  CHECK, 백업 기반 데이터·원래 타입 복원, 신규 DB CHECK를 검증하고 컨테이너를 제거한다.
  같은 스크립트를 PR/push CI에서도 실행한다 (#631).
