# 기존 DB 마이그레이션 안내

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
