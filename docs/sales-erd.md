# SALES ERD 정합화

토스 POS 업로드의 원본 이력은 보존하고, 주문 데이터는 새 파일이 포함하는 기간 단위로 교체한다. 현재 구현되지 않은 회원·매장·메뉴 도메인과는 JPA 연관관계를 만들지 않고 식별자 컬럼만 저장한다.

## 관계

- `sales_uploads` 1 : N `sales_orders` (`sales_orders.sales_upload_id`)
- `sales_orders` 1 : N `sales_order_items` (`sales_order_items.sales_order_id`)
- `sales_uploads` 1 : N `analysis_runs` (`analysis_runs.based_on_upload_id`)
- `sales_daily_summaries`는 `store_id`, `sales_date`별 주문 집계 스냅샷이다.
- `store_id`, `requested_by_user_id`, `menu_id`는 아직 구현되지 않은 도메인을 가리키는 논리 참조이며 물리 FK/JPA 연관관계는 두지 않는다.

## 테이블

### sales_uploads

| 컬럼 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | Y | PK, auto increment |
| store_id | BIGINT | Y | 업로드 대상 매장 식별자 |
| requested_by_user_id | BIGINT | Y | 업로드 요청 사용자 식별자 |
| source_type | VARCHAR(30) | Y | `TOSS_POS` |
| original_file_name | VARCHAR(255) | Y | 원본 파일명 |
| storage_key | VARCHAR(500) | Y | 파일 저장 위치 식별자 |
| file_checksum | VARCHAR(64) | Y | 파일 체크섬. 중복 여부 조회용이며 unique가 아님 |
| status | VARCHAR(20) | Y | `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED` |
| processing_phase | VARCHAR(20) | N | `VALIDATING`, `NORMALIZING`, `AGGREGATING`, `ANALYZING` |
| period_start / period_end | DATE | N | 파일이 포함하는 영업일 범위 |
| total_row_count | INT | N | 입력 행 수 |
| applied_record_count | INT | N | 반영 행 수 |
| invalid_row_count | INT | N | 유효하지 않은 행 수 |
| fail_reason | VARCHAR(50) | N | 실패 분류 |
| error_message | VARCHAR(500) | N | 실패 상세 |
| uploaded_at | DATETIME | Y | 접수 시각 |
| processed_at | DATETIME | N | 처리 종료 시각 |

인덱스는 `(store_id, uploaded_at)`과 `(store_id, file_checksum)`을 둔다. 체크섬을 unique로 만들지 않아 같은 파일을 다시 접수하더라도 별도의 업로드 이력을 남긴다.

### sales_orders

| 컬럼 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | Y | PK, auto increment |
| sales_upload_id | BIGINT | Y | 주문을 생성한 업로드 식별자 |
| store_id | BIGINT | Y | 매장 식별자 |
| channel | VARCHAR(20) | Y | `KIOSK`, `POS`, `DELIVERY` |
| pos_order_no | VARCHAR(100) | Y | POS 주문번호 |
| ordered_at | DATETIME | Y | 주문시각 |
| is_valid | BOOLEAN | Y | 분석 반영 가능 여부 |
| created_at | DATETIME | Y | 생성 시각 |

자연키 unique 제약 `uk_sales_orders_natural_key`는 `(store_id, channel, pos_order_no, ordered_at)`으로 구성한다.

### sales_order_items

| 컬럼 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | Y | PK, auto increment |
| sales_order_id | BIGINT | Y | 소속 주문 식별자 |
| menu_id | BIGINT | N | 연결 가능한 표준 메뉴 식별자 |
| order_status | VARCHAR(20) | Y | `COMPLETED`, `CANCELED` |
| menu_name_raw | VARCHAR(255) | Y | POS 원본 메뉴명 |
| menu_name | VARCHAR(100) | Y | 정규화 메뉴명 |
| menu_key | VARCHAR(191) | Y | 메뉴 매칭용 정규화 키 |
| category_raw | VARCHAR(100) | N | 원본 카테고리 |
| standard_category | VARCHAR(30) | Y | 표준 카테고리 enum |
| option_raw | VARCHAR(500) | N | 옵션 스냅샷 |
| quantity | INT | Y | 취소 행은 음수, 0은 허용하지 않음 |
| line_price | BIGINT | Y | `quantity * unit_price`, 취소 가능 |
| unit_price / option_price | BIGINT | Y | 단가와 옵션 금액 |
| item_discount_name | VARCHAR(100) | N | 상품 할인명 |
| item_discount_amount | BIGINT | Y | 상품 할인 금액 |
| order_discount_name | VARCHAR(100) | N | 주문 할인명 |
| order_discount_amount | BIGINT | Y | 주문 할인 금액 |
| net_amount | BIGINT | Y | 금액 구성요소 합계, 취소 가능 |
| taxable | BOOLEAN | Y | 과세 여부 |
| vat_amount | BIGINT | Y | 부가세 금액 |
| item_type | VARCHAR(30) | Y | 메뉴·주차·선불카드·배달비 등 항목 분류 |
| created_at | DATETIME | Y | 생성 시각 |

`item_type` 값은 `MENU`, `PARKING`, `PREPAID_CARD`, `DELIVERY_FEE`, `PLATFORM_PLACEHOLDER`, `EVENT`, `GOODS`다. `standard_category`는 `COFFEE`, `NON_COFFEE`, `TEA`, `ADE`, `SMOOTHIE`, `BOTTLED_DRINK`, `BEER`, `CAKE`, `BAKERY`, `FOOD`, `SEASONAL`, `UNMAPPED`를 사용한다.

### sales_daily_summaries

| 컬럼 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | Y | PK, auto increment |
| store_id | BIGINT | Y | 매장 식별자 |
| sales_date | DATE | Y | 영업일 |
| total_net_amount | BIGINT | Y | 전체 순매출 합계 |
| menu_net_amount | BIGINT | Y | 메뉴 항목 순매출 합계 |
| order_count | INT | Y | 주문 수, 음수 불가 |
| menu_quantity | INT | Y | 메뉴 수량 합계. 취소 반영으로 감소 가능 |
| updated_at | DATETIME | Y | 마지막 집계 시각 |

`(store_id, sales_date)`에 `uk_sales_daily_summaries_store_date` unique 제약을 둔다.

### analysis_runs

| 컬럼 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | Y | PK, auto increment |
| store_id | BIGINT | Y | 분석 대상 매장 식별자 |
| requested_by_user_id | BIGINT | Y | 분석 요청 사용자 식별자 |
| based_on_upload_id | BIGINT | Y | 분석 기준 업로드 식별자 |
| analysis_type | VARCHAR(20) | Y | `SALES` |
| status | VARCHAR(20) | Y | `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED` |
| period_start / period_end | DATE | N | 분석 대상 기간 |
| engine_version | VARCHAR(50) | Y | 분석 엔진 버전 |
| idempotency_key | VARCHAR(100) | Y | unique 분석 실행 키 |
| error_message | VARCHAR(500) | N | 실패 상세 |
| started_at / completed_at | DATETIME | N | 시작·종료 시각 |
| created_at | DATETIME | Y | 생성 시각 |

## 중첩 기간 재업로드 정책

1. 파일 접수마다 `sales_uploads`에 새 이력을 만든다. 동일 체크섬도 접수를 막지 않는다.
2. 검증이 끝나면 업로드의 `period_start`와 `period_end`를 확정한다.
3. 한 트랜잭션에서 해당 매장과 기간에 속하는 기존 주문 항목, 주문을 순서대로 제거한다.
4. 새 주문과 항목을 현재 `sales_upload_id`로 저장한다.
5. 같은 기간의 `sales_daily_summaries`를 다시 계산해 교체한다.
6. 처리 결과와 실패 정보는 업로드 이력에 남기며 기존 업로드 행은 삭제하지 않는다.

## ERDCloud 반영 목록

- `sales_orders` 자연키를 `(store_id, channel, pos_order_no, ordered_at)`으로 변경한다.
- `sales_orders`에 `sales_upload_id`, `channel`, `pos_order_no`, `ordered_at`, `is_valid`를 반영한다.
- `sales_order_items`의 수량·금액 양수 제약을 제거하고 취소 행의 음수를 허용한다.
- `sales_order_items`에 원본/정규화 메뉴명, `menu_key`, 원본/표준 카테고리, 옵션, 할인, 과세, `item_type` 스냅샷을 반영한다.
- `sales_uploads`의 체크섬은 일반 인덱스로 두고 unique 제약을 두지 않는다.
- `sales_uploads`에 처리 단계, 파일 커버리지, 처리 건수, 실패 정보를 반영한다.
- `analysis_runs`와 상태·기간·멱등키·실패 정보 컬럼을 추가한다.
- `sales_daily_summaries`를 `(store_id, sales_date)` 단위로 유지한다.
- 미구현 도메인 식별자는 물리 FK 없이 BIGINT 컬럼으로 반영한다.
