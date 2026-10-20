# 기존 DB 마이그레이션 안내

## V2 선택 재생성 실행 이력 (#517)

기존 QA·운영 DB에는 BE 배포 전 `20261020-add-regeneration-executions.sql`을 한 번 적용합니다.
신규 DB는 `docker/mysql/init/11-regeneration-executions.sql`을 사용합니다. 두 SQL은
`sales_ai_insight_regenerations`와 `solution_regenerations` 테이블만 추가하며 기존 솔루션·인사이트 행을 변경하지 않습니다.
기존 운영 `stores.id`가 signed `BIGINT`이므로 마이그레이션의 `store_id`도 signed입니다.
적용 전 DB 백업과 테이블 부재를 확인하고, 적용 후 UNIQUE·FK·CHECK 제약 및 빈 행 수를 확인한 뒤
BE 스키마 검증을 진행합니다. 이 변경만으로 기존 결과가 자동 재생성되지는 않습니다.

## V2 과거 강수 관측 지점·관측값 저장 (#482)

기존 QA·운영 DB에는 BE 배포 전 `20261018-add-weather-observation-stations.sql`을 한 번 적용합니다.
신규 DB는 `docker/mysql/init/10-weather-observations.sql`을 사용합니다. 두 SQL은
`weather_stations`, `weather_observations`를 생성하고 `stores.weather_station_id`를
NULL 허용 FK로 추가합니다. 기존 매장은 모두 미매핑 상태(NULL)로 유지하며 관측값을
자동 적재하거나 매장 좌표로 관측 지점을 추정하지 않습니다.

적용 전 백업과 함께 `weather_stations`, `weather_observations` 테이블 및
`stores.weather_station_id` 컬럼 존재 여부를 확인해야 합니다. 위 마이그레이션은
관측값 테이블이 **없는 기존 DB**를 대상으로 합니다. 구형 `weather_observations`가
이미 존재하면 이 파일을 실행하지 말고 기존 행의 제공처·지점 코드 확보와 이행 방식을
별도로 확정해야 합니다. 좌표만으로 지점 코드를 임의 생성하거나 기존 UNIQUE를
삭제하지 않습니다. 적용 후 `(source, station_code)`와 `(station_id, observation_date)`
UNIQUE, 두 FK 및 기존 매장의 NULL 상태를 확인하고 BE 스키마 검증을 진행합니다.

## 매장 행정동 코드 (#477)

기존 QA·운영 DB에는 BE 배포 전에 `20261017-add-store-administrative-dong-code.sql`을 한 번 실행합니다.
신규 DB는 `docker/mysql/init/01-auth-signup.sql`에서 `stores.administrative_dong_code CHAR(10) NULL`을 생성합니다.
기존 매장에는 행정동 코드를 임의로 채우지 않으며 기존 행은 `NULL`로 유지합니다. 주소·좌표에서 행정동 코드를
산출하거나 가입/매장 수정 요청으로 입력받는 기능은 이 변경에 포함되지 않습니다.

배포 전 DB 백업 여부를 확인하고, 마이그레이션 적용 후 컬럼 타입과 기존 행의 `NULL` 상태를 확인한 뒤 BE를 배포합니다.
이 변경은 컬럼 하나를 추가하며 기존 매장 데이터는 삭제하거나 수정하지 않습니다.

## V2 서울 상권 분기 요약 저장 (#471)

기존 QA·운영 DB에는 BE 배포 전에 `20261016-add-seoul-district-quarter-benchmarks.sql`을 한 번 실행합니다.
신규 DB는 `docker/mysql/init/09-seoul-district-quarter-benchmarks.sql`에서 같은 두 테이블을 생성합니다.
`administrative_dong_code`는 서울시 `ADSTRD_CD` 원본 10자리에 맞춰 `CHAR(10)`입니다 (#474).
Cloud에 먼저 전달된 `CHAR(8)` 버전의 SQL은 실행하지 말고, 이 수정이 포함된 파일을 다시 전달해 적용합니다.
로컬에서도 기존 DB 볼륨에는 `docker/mysql/init`이 다시 실행되지 않으므로, 테이블이 없으면 수정된
마이그레이션 SQL을 해당 로컬 DB에 한 번 적용합니다. 이미 테이블이 있다면 이 `CREATE TABLE` 파일을
다시 실행하지 말고 현재 컬럼 정의와 데이터 상태를 먼저 확인합니다.
두 테이블은 기존 테이블을 참조하지 않으므로 기존 데이터는 변경하지 않습니다. 실행 전에 DB 백업을 확인하고,
실행 후 두 테이블의 UNIQUE·CHECK·FK 제약과 빈 행 수를 확인한 다음 BE 스키마 검증 및 Health Check를 진행합니다.
실제 분기 데이터 수집은 이 마이그레이션에서 수행하지 않습니다.

`20260929-add-analysis-schema.sql`은 분석 실행 이력 스키마가 추가되기 전에
생성된 QA·운영 DB에 적용하는 마이그레이션 SQL입니다. 이 스크립트는 Hibernate
엔티티와 맞지 않던 `store_business_hours.day_of_week` 타입도 함께 보정합니다.

## 적용 순서

1. DB 백업 여부를 확인하거나 백업을 생성합니다.
2. `20260929-add-analysis-schema.sql`을 한 번 실행합니다.
3. 스크립트가 출력하는 두 개의 조회 결과를 저장합니다.
4. BE 기동, 매출 업로드, 분석, 인사이트, 예측, 솔루션 생성 순서로 스모크 테스트를 진행합니다.
5. 고아 `sales_analyses` 행 조회 결과를 BE와 검토한 뒤,
   보류된 `sales_analyses.analysis_run_id` 외래 키 추가 여부를 결정합니다.

이 마이그레이션은 기존 비즈니스 데이터를 삭제하거나 변경하지 않습니다.
신규 DB는 `docker/mysql/init`을 통해 초기화하며, 이 경우 분석 실행 이력 외래 키까지 포함한 전체 스키마가 생성됩니다.

## 비밀번호 재설정 이메일 요청 제한 (#341)

기존 QA·운영 DB에는 BE 배포 전에 `20261002-add-password-reset-rate-limit.sql`을 한 번 실행합니다.
신규 DB는 `docker/mysql/init/01-auth-signup.sql`에서 같은 테이블을 생성합니다.
이 변경은 데이터 삭제 없이 `password_reset_email_attempts` 이력 테이블만 추가합니다.

Cloud 배포 설정:

- `PASSWORD_RESET_RATE_LIMIT_HASH_KEY`: 모든 BE 인스턴스에서 같은 값으로 설정할 강한 임의 비밀값을 권장합니다. 비워 두면 기존 `DB_PASSWORD`를 사용합니다.
- `TRUST_X_FORWARDED_FOR=true`: 실제 클라이언트 IP를 읽으려면 ALB가 `X-Forwarded-For`를 append 모드로 추가하는지 확인하고, 백엔드 인바운드를 ALB 보안 그룹에서만 허용한 경우에만 설정합니다. 그 외에는 소켓 peer IP를 사용합니다.
- `PASSWORD_RESET_RATE_LIMIT_CLEANUP_CRON`은 선택 설정이며, 기본값은 5분마다 만료된 요청 이력을 최대 10,000건씩 정리합니다.

권장 배포 순서는 DB 백업 확인 → SQL 적용 및 테이블·인덱스 확인 → 환경변수 확인 → BE 배포 → 비밀번호 재설정 요청의 202/429 동작 스모크 테스트입니다.

## 로그인 실패 요청 제한 (#369)

기존 QA·운영 DB에는 BE 배포 전에 `20261006-add-login-rate-limit.sql`을 한 번 실행합니다.
신규 DB는 `docker/mysql/init/01-auth-signup.sql`에서 `login_attempts` 테이블을 생성합니다.
실패 이력은 이메일·IP를 HMAC 해시로 저장하며, 성공 로그인 시 이메일 기준 실패 이력만 초기화합니다.

Cloud 배포 설정:

- `LOGIN_RATE_LIMIT_HASH_KEY`: 모든 BE 인스턴스에서 같은 강한 임의 비밀값을 권장합니다. 비워 두면 기존 `DB_PASSWORD`를 사용합니다.
- `LOGIN_RATE_LIMIT_CLEANUP_CRON`: 선택 설정이며, 기본값은 5분마다 15분보다 오래된 로그인 실패 이력을 최대 10,000건씩 정리합니다.
- `TRUST_X_FORWARDED_FOR`: ALB 뒤에서 실제 IP별 제한이 필요하면 ALB가 `X-Forwarded-For`를 append 모드로 추가하고 BE 인바운드가 ALB 보안 그룹으로 제한된 경우에만 `true`로 둡니다.

계정은 최근 15분 실패 횟수에 따라 5–9회 30초, 10–14회 1분, 15–19회 5분, 20회 이상 15분 쿨다운을 적용합니다. IP는 최근 10분간 실패 30회를 허용하고 다음 요청을 429로 제한합니다. 쿨다운을 발생시킨 실패 응답은 기존과 동일한 401이며, 차단 요청은 횟수에 추가하지 않습니다.

권장 배포 순서는 DB 백업 확인 → SQL 적용 및 테이블·인덱스 확인 → `LOGIN_RATE_LIMIT_HASH_KEY` 등 환경변수 확인 → BE 배포 → 로그인 실패 401 및 제한 429 동작 스모크 테스트입니다.

## V2 월별 순이익 비용 (#377)

기존 DB에는 BE 배포 전에 `20261007-add-store-cost-items.sql`을 한 번 실행합니다.
신규 DB는 `docker/mysql/init/06-profit-costs.sql`에서 같은 테이블을 생성합니다.
매장과 비용 기준 월에 UNIQUE 제약을 적용하며, 기존 데이터는 변경하지 않습니다.

## 일별 매출 상태 (#391)

기존 QA·운영 DB에는 BE 배포 전에 `20261007-add-sales-daily-status.sql`을 한 번 실행합니다.
기존 `sales_daily_summaries` 행은 기본값 `UNKNOWN`으로 유지하며, 근거 없이 과거 상태를 추정하지 않습니다.
신규 DB는 `docker/mysql/init/02-sales-core.sql`에서 `day_status`를 생성합니다.

권장 배포 순서는 DB 백업 확인 → SQL 적용 및 기존 행의 `UNKNOWN` 확인 → BE 배포 → 매출 업로드 후 `COMPLETE`/`CLOSED`/`MISSING`/`UNKNOWN` 상태 확인입니다.

## V2 메뉴판 영속성 (#406)

기존 QA·운영 DB에는 BE 배포 전에 `20261007-add-menu-recognition.sql`을 한 번 실행합니다.
신규 DB는 `docker/mysql/init/01-auth-signup.sql`의 `stores.menu_revision`과
`docker/mysql/init/07-menu-recognition.sql`의 메뉴 테이블 다섯 개를 사용합니다.
기존 DB의 `stores.id`가 signed `BIGINT`이므로 마이그레이션의 `store_id`도
같은 타입으로 생성합니다. 신규 DB의 `stores.id`와 `store_id`는 `BIGINT UNSIGNED`입니다.
기존 매장의 메뉴 개정 번호는 0으로 시작하며 기존 매장·매출 데이터는 변경하지 않습니다.

## 메뉴판 카테고리 TEA 전환 (#451)

기존 DB에는 BE 배포 전에 `20261014-menu-category-tea.sql`을 한 번 실행합니다.
실행 전 아래 쿼리로 같은 매장의 활성 `NON_COFFEE`와 `OTHER` 메뉴가
같은 이름·가격을 갖는지 확인하고, 결과가 있으면 카테고리를 직접 정리합니다.
그대로 실행하면 `uk_menus_active_tuple` 유니크 제약에 걸립니다.

```sql
SELECT old_menu.store_id, old_menu.id AS non_coffee_menu_id,
       other_menu.id AS other_menu_id
FROM menus old_menu
JOIN menus other_menu
  ON other_menu.store_id = old_menu.store_id
 AND other_menu.active_normalized_name = old_menu.active_normalized_name
 AND other_menu.price = old_menu.price
WHERE old_menu.category = 'NON_COFFEE'
  AND other_menu.category = 'OTHER'
  AND old_menu.removed_at IS NULL
  AND other_menu.removed_at IS NULL;
```

기존 `menus.category=NON_COFFEE`는 차가 아닌 메뉴도 포함할 수 있으므로
`OTHER`로 전환합니다. 검토 대기 중인 `menu_image_items.detected_category=NON_COFFEE`는
`NULL`로 바꿔 사용자가 다시 선택하게 하고, 검토가 끝난 후보는 `OTHER`로 전환합니다. 이후 허용값을
`COFFEE`, `TEA`, `BEVERAGE`, `CAKE`, `BAKERY`, `OTHER`로 제한합니다.
신규 DB는 `docker/mysql/init/07-menu-recognition.sql`에 이 값을 적용합니다.
매출 데이터의 `standard_category`와 `category_raw`는 변경하지 않습니다.

## 메뉴 이미지 순서 타입 보정 (#431)

`MenuImage.imageOrder`의 Java `int`와 Hibernate `INTEGER` 매핑에 맞춰 신규 초기화 SQL과
메뉴판 생성 마이그레이션은 `menu_images.image_order INT NOT NULL`을 사용합니다.
기존 DB에는 `20261012-fix-menu-image-order-type.sql`을 다음 순서로 **한 번만** 적용합니다.

1. 운영 DB 백업이 완료됐는지 확인합니다.
2. 아래 조회로 현재 타입을 확인합니다. `tinyint unsigned`일 때만 보정 SQL을 실행합니다.
   이미 `int`이면 실행하지 않습니다. 다른 타입이면 BE 담당자와 확인합니다.
3. 적용 전후 `menu_images` 행 수와 `image_order`별 값을 비교합니다.
4. 아래 조회로 `int`, `NOT NULL`, 1~10 CHECK, `(batch_id, image_order)` UNIQUE를 확인합니다.
5. BE를 재배포하고 기동 로그의 스키마 검증과 Health Check를 확인합니다.

```sql
SELECT COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_images' AND COLUMN_NAME = 'image_order';

SELECT COUNT(*) AS image_count, MIN(image_order) AS min_order, MAX(image_order) AS max_order
FROM menu_images;

SELECT CONSTRAINT_NAME, CHECK_CLAUSE
FROM information_schema.CHECK_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME = 'ck_menu_image_order';

SELECT INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_images'
  AND INDEX_NAME = 'uk_menu_image_batch_order'
ORDER BY SEQ_IN_INDEX;
```

## 성장률 랭킹 저장 계층 (#402)

기존 QA·운영 DB에는 BE 배포 전에 `20261007-add-growth-ranking.sql`을 한 번 실행합니다.
운영 `stores.id`가 signed `BIGINT`인 스키마와 외래 키를 맞추기 위해 운영 마이그레이션의 `ranking_profiles.store_id`도 signed `BIGINT`로 선언합니다.
신규 DB는 `docker/mysql/init/07-growth-ranking.sql`을 사용하며, fresh-init의 `stores.id` 타입에 맞춰 `ranking_profiles.store_id`를 `BIGINT UNSIGNED`로 선언합니다.

배포 전 세 테이블과 외래 키·유니크 인덱스가 생성됐는지 확인한 뒤 BE를 배포합니다. 이 변경은 기존 매출 데이터를 수정하지 않습니다.

## V2 기상청 단기예보 요약 저장 (#463)

기존 QA·운영 DB에는 BE 배포 전에 `20261015-add-weather-forecast-summaries.sql`을 한 번 실행합니다.
기존 `stores.id`가 signed `BIGINT`인 스키마와 FK 타입을 맞추기 위해 운영 마이그레이션의 `store_id`도 signed `BIGINT`입니다.
신규 DB는 `docker/mysql/init/08-weather-forecast-summaries.sql`을 사용하며, fresh-init의 `stores.id`와 `store_id`는 `BIGINT UNSIGNED`입니다.
매장·KST 대상일에 한 행만 저장하며, 원본 예보 응답이나 과거 관측값은 이 테이블에 넣지 않습니다.
이 마이그레이션은 새 테이블만 만들고 기존 데이터를 변경하지 않습니다.
