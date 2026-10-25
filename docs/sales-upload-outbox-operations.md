# 매출 업로드 Outbox 전환·검증·운영

승인 #556, 구현 #557~#561. 기본 분석까지만 영속 전달한다. AI 후처리·파일 저장과 DB 간 원자성·범용 브로커는 보장 범위 밖이다.

## 배포 전

1. 새 DB는 init/12-sales-upload-outbox.sql, 기존 DB는 백업·테이블 부재 확인 후 migrations/20261025-add-sales-upload-outbox.sql을 수동 적용한다. 기존 DB 참조 PK의 signedness는 마이그레이션에서 직접 읽는다. 앱 코드의 추가는 운영 DB에 SQL을 실행하는 행위가 아니다.
2. 테이블·FK·CHECK·인덱스와 앱의 JPA validate 성공을 확인한다. Outbox 시간 필드는 JDBC LocalDateTime 직접 매핑으로 UTC DATETIME 값을 보존하고, 선점 조건은 DB UTC_TIMESTAMP(6)와 비교한다. 다른 날짜 컬럼의 기존 시간 정책은 바꾸지 않는다.
3. SALES_OUTBOX_ENABLED=false로 앱을 배포한다. 같은 매장에 쓰는 모든 업로드 경로의 전환 계획을 확인한다. 기본값 false이므로 PR 병합만으로 자동 활성화되지 않는다.
4. 접수를 제어하고 기존 메모리 Worker의 실행·대기 작업을 배출한다. 구버전 직접 Worker 실행 서버와 Outbox 처리 서버를 혼용하지 않는다.
5. 다중 인스턴스에서는 모든 Worker가 같은 storageKey를 읽을 수 있게 기존 S3 또는 공유 볼륨을 확인한다. 인스턴스마다 별도의 로컬 원본 디렉터리인 구성은 복구 대상 파일을 읽지 못한다.
6. SALES_OUTBOX_ENABLED=true, SALES_OUTBOX_DISPATCH_ENABLED=true로 전환한다. 신규 접수의 PENDING/Outbox READY 동시 생성, 조회·완료·실패, 파일 처리 시간을 확인한다. staging/운영 전환 자체는 이 작업에서 실행하지 않았다.

## 동작과 제한

- 요청은 PENDING·초기 분석 실행·Outbox가 커밋된 뒤 202/PENDING을 반환한다. 실행기가 바쁘면 DB에서 기다리고 접수 실패로 바꾸지 않는다.
- 같은 매장 작업은 가장 앞선 READY/RUNNING이 종료돼야 다음 작업을 실행한다. 앞선 작업이 재시도 대기 중이면 뒤 작업도 기다리는 정책이다.
- 매장 행 → 작업 행 순서로 잠근 뒤 각 DB 쓰기 전에 토큰·유효 lease·최선행 여부를 확인한다. lease 만료만으로 이전 Worker가 종료됐다고 가정하지 않는다.
- 기본 분석 DONE 이후 AI 등 후처리 전 프로세스가 꺼지면 해당 후처리는 자동 재전달되지 않는다. DONE 재전달은 후처리를 다시 실행하지 않는다.
- 일시 오류는 최초 포함 5회, 재시도 간격 10초·30초·2분·5분 ±20%. lease 60초·갱신 10초. 실행 등록 거절은 시도 한도를 소비하지 않는다.
- 실패 상태 기록에 DB 장애가 있으면 RUNNING이 남을 수 있다. DB 회복과 lease 만료 후 재선점한다. DEAD 레코드를 SQL로 READY로 되돌리는 운영 절차는 제공하지 않는다. 원인 확인과 별도 승인 없이 replay하지 않는다.
- 기존 PENDING/PROCESSING 이력 자동 backfill은 하지 않는다. 원본 접근·처리 여부·중첩 업로드 영향을 확인한 목록으로 이행을 별도 결정한다.

## 관측

활성화 시 30초마다 구조 로그 `sales_outbox_queue`에 상태별 작업 수, 가장 오래된 미종료 작업의 생성 이후 경과 초, 만료 RUNNING 수, 재시도 횟수 합계, 현재 프로세스의 lease 복구 횟수를 기록한다. `sales_outbox_outcomes`에는 현재 프로세스의 DONE/READY/DEAD/OWNERSHIP_LOST/UNRECORDED/REJECTED 횟수와 실행 점유 시간 합계를 기록한다. 후자의 시간에는 기본 분석 뒤 후처리 시간이 포함될 수 있어 API 접수 지연이나 기본 분석 완료 시간과 동일하지 않다.

DB 기반 상태·대기·재시도 값은 재시작 후에도 계산할 수 있다. 현재 프로세스 카운터는 재시작 시 0으로 시작한다. 메트릭 차원에 업로드/매장/사용자 ID·파일명을 넣지 않는다. 이번에는 로그로 관측하며 Prometheus scrape endpoint를 새로 노출하지 않는다.

조회 전용 점검 예:

```sql
SELECT status, COUNT(*) FROM sales_upload_outbox GROUP BY status;
SELECT COUNT(*) AS expired_running FROM sales_upload_outbox
WHERE status='RUNNING' AND lease_until <= UTC_TIMESTAMP(6);
SELECT MIN(created_at) AS oldest_pending FROM sales_upload_outbox
WHERE status IN ('READY','RUNNING');
```

테이블을 지우거나 기존 직접 호출 경로로 즉시 되돌리면 접수된 작업이 유실될 수 있다. 롤백은 새 접수를 제어한 뒤 Outbox 작업을 배출하거나 호환 Dispatcher를 유지하는 방식으로 계획한다. 스키마·작업 레코드는 보존한다.

## 격리된 MySQL 검증 재실행

테스트는 별도 loopback MySQL의 outbox_test DB를 사용한다. Hibernate create-drop과 실제 Outbox 마이그레이션을 실행하므로 사용자·QA·운영 DB에 연결하면 안 된다. 원본 파일도 전용 임시 디렉터리에 저장한다.

```sh
docker run -d --name codex-sales-outbox-test -p 127.0.0.1:33319:3306 \
  -e MYSQL_ALLOW_EMPTY_PASSWORD=yes -e MYSQL_DATABASE=outbox_test \
  mysql:8.4 --default-time-zone=+00:00
docker exec codex-sales-outbox-test mysqladmin ping --silent
OUTBOX_MYSQL_TEST_URL='jdbc:mysql://127.0.0.1:33319/outbox_test?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true' \
  ./gradlew build --offline
docker rm -f codex-sales-outbox-test
```

테스트 URL은 outbox_test DB로 제한하고 별도 JVM 강제 종료 probe는 loopback 주소만 허용한다. 실제 실행은 준비 상태 확인 후 수행한다. 환경변수가 없으면 MySQL 전용 테스트는 건너뛰며, 이 결과를 MySQL 검증 통과로 보고하지 않는다.

별도 JVM은 실제 Coordinator로 선점 후 halt(73), 또는 체크포인트 flush 후 커밋 전에 halt(74)한다. 부모 검증은 프로세스 종료·실제 DB 롤백을 확인하고 테스트용 SQL로 lease를 만료시킨 뒤 다른 소유자로 완료한다. 60초를 실시간 대기하는 테스트는 아니다. 스냅샷/완료 트랜잭션은 예외 주입 롤백으로 별도 검증하며 모든 장애 지점을 OS 종료로 재현했다고 주장하지 않는다. 쿼리 계획은 build/reports/outbox/mysql-query-plan.txt에 기록한다.
