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
