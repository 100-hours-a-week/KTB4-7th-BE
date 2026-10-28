# 같은 매장·기간 겹침 업로드 검증

검증일: 2026-10-28 KST. 이슈: [#602](https://github.com/100-hours-a-week/KTB4-7th-BE/issues/602).
기준 dev: `abbef60ad14c5f5c8c47a10b96cabc94b9ef99cc`.
작업 브랜치: `feat/602-sales-overlap-validation`.

## 결과

- JDK 25.0.3, 격리된 loopback MySQL 8.4.11에서 `./gradlew build --offline` 성공(49초).
- 전체 1,110개: 1,105개 통과, 기존 Redis 전용 5개 건너뜀, 실패·오류 0개.
- `SalesOutboxMySqlIntegrationTest`: 기존 19개 + 신규 13개 = 32개 모두 통과, 건너뜀 0개.
- 기존 실제 MySQL 런타임 테스트 2개와 재시도 정책 테스트 3개도 실행·통과.
- 운영 소스/API/스키마/정책 변경 없이 기존 보호의 검증을 보강했다.
- 이 결과는 로컬 검증이다. PR 제출·원격 CI·dev 병합 상태는 연결된 PR에서 별도로 확인하며, 운영 적용 결과로 해석하지 않는다.

## 새 검증 13개

| 검증 | 확인한 결과 |
| --- | --- |
| A 1~10일 → B 5~15일 | A 1~4일 보존, 이후 B의 주문·집계·스냅샷 |
| A 1~10일 → B 1~10일 | 동일 기간의 주문이 B로 교체되고 합계 중복 없음 |
| A 1~15일 → B 5~10일 | B 기간 양쪽의 A 데이터 보존 |
| A 5~10일 → B 1~15일 | B 전체 기간으로 교체 |
| A 1~4일 → B 5~10일 | 인접한 기간 각각 보존 |
| A 1~5일 → B 5~10일 | 겹치는 경계일은 B로 교체 |
| A 분석을 latch로 멈추고 별도 연결에서 선점 | B는 대기, 다른 매장은 완료 가능, A 완료 후 B 완료 |
| DATA_APPLIED 뒤 분석 일시 실패 | B 대기, A 복구 때 매출 교체 1회, 이후 B 최종 반영 |
| lease 만료·새 토큰 인계·예전 Worker 복귀 | A부터 복구하고 B 완료, 예전 토큰의 실제 교체 시도 차단 |
| DATA_APPLIED 뒤 최종 DEAD | 전체 롤백을 가정하지 않고 남은 A 범위 보존, B 기간만 교체 |
| 기존 데이터가 있는 교체 후 flush·커밋 전 예외 | 기존 주문/항목 ID·값·집계와 체크포인트 복원, 재실행 성공 |
| B 기간 중 주문 없는 날짜 | 해당 기존 주문 제거, 일별 합계 0 및 UNKNOWN, 과거 스냅샷 유지 |
| 마지막 날 23:59:59.999999와 다음날 0시 | 마지막 날 교체·다음날 보존, 범위 밖 주문 ID/시각 유지 |

6개 기간 조합은 선행 기본 데이터와 다른 매장을 먼저 완료한 뒤 서로 다른 금액의 실제 XLSX 파일을 접수한다. 최종 주문의 날짜·uploadId·금액, 주문/항목 개수, 일별 총매출·메뉴매출·주문수·수량·상태, 원래 실행 ID에 연결된 스냅샷 매출·주문수를 검사한다. 다른 매장의 주문·항목·집계 행도 변경 전후 비교한다.

## 재현

JDK 25를 JAVA_HOME으로 지정하고 전용 MySQL 8.4 인스턴스에 `outbox_test`와 `outbox_runtime_test`만 준비한다. 테스트는 create-drop을 사용하므로 아래 URL을 사용자/운영 DB로 바꾸면 안 된다.

```sh
OUTBOX_MYSQL_TEST_URL='jdbc:mysql://127.0.0.1:33321/outbox_test?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true' \
OUTBOX_RUNTIME_MYSQL_URL='jdbc:mysql://127.0.0.1:33321/outbox_runtime_test?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true' \
./gradlew build --offline
```

기존 CI의 OUTBOX_MYSQL_TEST_URL이 설정되면 신규 테스트도 기존 통합 테스트 클래스와 함께 실행된다. 환경변수가 없으면 이 클래스는 건너뛰므로 테스트 결과의 skipped를 반드시 확인한다.

## 환경·해석 범위

- 전용 컨테이너 `codex-sales-overlap-mysql-20261028`, 호스트 loopback 포트 33321만 사용했다. 기존 사용자 DB/컨테이너에는 변경하지 않았다.
- 업무 테이블은 기존 테스트 설정의 Hibernate create-drop, Outbox 테이블은 실제 마이그레이션 SQL을 적용한다. 운영 전체 init 스키마 검증을 대신하지 않는다.
- 서버 두 대 대신 같은 JVM의 작업 스레드/서로 다른 DB 연결로 경쟁했다. 실제 ECS·ProxySQL·Router·S3 경로는 미검증이다.
- lease와 재시도 예약은 SQL로 시각을 앞당겼다. 실제 60초 만료와 지연 전체를 기다렸다는 뜻은 아니다.
- heartbeat 상실은 테스트 신호로 주입했다. 운영 네트워크 장애 실험은 아니다.
- 업무 날짜 경계는 애플리케이션과 같은 Timestamp 바인딩으로 준비·조회한다. UTC SQL 리터럴과 JVM 로컬 업무 시각을 혼합해 잘못된 경계를 만들지 않는다. Outbox 시각은 기존 DB UTC 정책을 유지한다.
- 초기 경계 테스트 1건은 검증 데이터 바인딩 불일치로 실패했으며, fixture를 수정한 뒤 단독 재검증과 전체 빌드가 통과했다. 실패 기록과 최종 통과 기록을 구분한다.
- 같은 매장의 Outbox 경로, 단계별 커밋과 원래 분석 스냅샷까지의 검증이다. 직접 Worker 혼용, AI/랭킹 후처리, 원본 저장소와 DB의 분산 원자성, 부하 성능을 보장하지 않는다.

증빙은 작업 공간 상위 `outbox-review/overlap-602-build.log`, `overlap-602-verification.json`, `overlap-602-test-results/`에 보존한다.
