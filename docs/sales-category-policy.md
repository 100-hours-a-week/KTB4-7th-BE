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

## 적용 순서와 확인

1. BE 요청 처리와 업로드·분석 Worker를 중지한다. 구버전은 신규 `BEVERAGE`/`OTHER`를 읽을 수 없어
   신버전과 동시에 실행하는 롤링 배포를 하지 않는다.
2. `sales_order_items`를 복원 가능한 형태로 백업한다. 기존 코드별 건수와 금액·수량 합계를 기록한다.
3. SQL을 DB 연결 하나에서 한 번 실행한다. 임시 테이블에서 결과를 계산한 뒤 트랜잭션으로 갱신한다.
   CHECK 추가는 MySQL DDL의 별도 커밋이다.
4. 아래 결과에서 6종 이외 코드가 없고 건수·금액·수량 합계가 적용 전과 같은지 확인한다.
5. 신버전 BE·Worker를 시작하고 분석 조회·새 업로드를 확인한다.

```sql
SELECT standard_category, COUNT(*) AS item_count, SUM(net_amount), SUM(quantity)
FROM sales_order_items GROUP BY standard_category;
SELECT COUNT(*) AS invalid_count FROM sales_order_items
WHERE standard_category NOT IN ('COFFEE','TEA','BEVERAGE','CAKE','BAKERY','OTHER');
SELECT COUNT(*) AS item_count, SUM(net_amount), SUM(quantity) FROM sales_order_items;
```

롤백은 BE·Worker 중지 → CHECK 제거 → 백업의 원래 분류 복원 → 구버전 시작 순서다.
새 분류를 구 12종으로 역변환하면 원래 분류를 복원할 수 없으므로 역변환 SQL은 사용하지 않는다.

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
  Java와 같은 CSV 사례, 구 12종 전환, 원본·금액·수량 보존, 신규 DB CHECK를 검증하고 컨테이너를 제거한다.
