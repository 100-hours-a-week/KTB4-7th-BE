# V2 웹 2개·배치 1개 Outbox 실행 설정

근거: 사용자 제공 V2 운영 구조, 승인 Outbox 명세(#556), 설정 이슈 #590.
Cloud 저장소 `README.md`·`deploy/ecs/deploy_backend.py`의 웹2(100/200)·배치1(0/100), ProxySQL→MySQL Router→Primary, 공유 S3 구성을 따른다.

## 역할별 설정

기존 `SPRING_PROFILES_ACTIVE`의 `production-redis`, `production-pool-evict` 등은 **유지하고** 아래 역할 프로필 하나를 추가한다. 기존 프로필을 역할 프로필 하나로 대체하지 않는다.

| 설정 | 웹 서비스(2개) | 배치 서비스(1개) |
| --- | --- | --- |
| 추가 프로필 | sales-outbox-web | sales-outbox-batch |
| SALES_OUTBOX_ENABLED | false → 검증 후 별도 true | false → 검증 후 별도 true |
| SALES_OUTBOX_DISPATCH_ENABLED | false | true |
| SALES_STORAGE_TYPE | 활성화 시 s3 필수 | 활성화 시 s3 필수 |
| SALES_STORAGE_S3_BUCKET | 같은 버킷·객체 경로 | 같은 버킷·객체 경로 |
| DB_URL | 기존 TLS ProxySQL writer 경로 | 같은 writer 경로 |

두 역할 프로필을 함께 사용하면 기동을 거부한다. 웹에서 Dispatcher가 켜졌거나 Outbox 활성 배치의 Dispatcher가 꺼져 있어도 거부한다. 프로필 자체는 Outbox를 활성화하지 않는다. 일반 로컬 실행과 이전 비동기 경로는 새 프로필을 사용하지 않으면 기존대로 동작한다.

웹의 접수는 `sales_uploads`·`analysis_runs`·Outbox를 함께 저장한 뒤 202/PENDING을 반환한다. 배치가 DB 작업을 선점하고 기존 Worker로 실행한다. S3 버킷은 같아야 하며 각 태스크의 개별 임시 디렉터리에 원본을 저장하지 않는다. 태스크 IAM 역할에서 웹의 PutObject/GetObject, 배치의 GetObject와 암호화 시 KMS 권한을 확인한다. 실제 접근 권한은 설정 문자열만으로 확인할 수 없다.

## 기동 시 DB 점검

`SalesOutboxRuntimeConfiguration`은 역할 프로필과 `app.sales.outbox.enabled=true`일 때만 실행한다. 공유 S3 타입·버킷을 확인하고, **기존 DataSource**의 연결로 짧은 READ WRITE 트랜잭션을 열어 다음을 두 번 읽는다.

```sql
START TRANSACTION READ WRITE;
SELECT @@server_uuid, @@read_only, @@super_read_only, CONNECTION_ID();
SELECT @@server_uuid, @@read_only, @@super_read_only, CONNECTION_ID();
ROLLBACK;
```

DB가 MySQL이고 두 조회 모두 같은 server_uuid/connection_id이며 read_only=0, super_read_only=0이어야 한다. 애플리케이션 데이터는 변경하지 않고 커넥션의 autoCommit을 복원한다. 실패 메시지에 JDBC URL·비밀번호·DB 응답 내용을 노출하지 않는다. 기존 Hikari·Redis 풀 제어를 교체하지 않는다.

이것은 시작 시점의 사전 점검이다. **모든 후속 SQL의 라우팅, 모든 풀 커넥션, ProxySQL의 잠금 쿼리 처리, 장애 전환 이후의 정합성을 보장하는 증명은 아니다.** 같은 writer로 작업 관련 조회·잠금·변경이 전달되도록 운영 ProxySQL/Router 설정과 실제 업무 흐름을 별도로 검증해야 한다. 기존 토큰/행 잠금 보호는 그대로 유지한다.

## 운영 적용 순서

1. #569까지의 기본 Outbox 변경과 이 역할 설정을 검증된 이미지에 포함한다. 아직 미병합인 선행 PR을 운영에 반영됐다고 가정하지 않는다.
2. Cloud CD의 기존 승인·이미지 digest·Redis 호환성 게이트를 유지한다. Outbox 작업 때문에 `BACKEND_ECS_CD_ENABLED`를 자동으로 켜지 않는다.
3. 스키마 적용·기존 직접 Worker 배출·공유 S3 확인 후 웹/배치 모두 같은 모드가 되도록 전환한다. 웹은 먼저 Outbox로 받고 배치가 아직 기존 직접 실행 중인 혼용을 피한다.
4. Cloud 이슈 #22의 읽기 전용 ECS 메타데이터 검사와 실제 Primary/S3 검증을 수행한다.
5. 두 웹 각각의 업로드가 같은 DB Outbox에 등록되고, 배치에서 같은 storageKey를 읽어 DONE에 도달하는지 확인한다. 웹 Dispatcher 실행이 없는지도 확인한다.
6. 격리된 검증 환경에서 배치 중지·재시작과 두 작업자 경쟁을 검증한다. 운영 장애 실험을 임의로 실행하지 않는다.

## 로컬 검증과 한계

설정 단위 테스트는 프로필 파일 로딩·웹 실행 차단·공유 S3 누락·DB 읽기 전용/대상 변경 거부·비밀값 비노출·기존 비활성 경로를 확인한다. 실제 MySQL 전용 테스트는 loopback의 `outbox_runtime_test` DB만 허용한다. 읽기 전용 거부 테스트는 전용 컨테이너의 글로벌 read_only를 일시 변경하고 finally에서 복원한다. 사용자 DB를 연결하면 안 된다.

```sh
OUTBOX_RUNTIME_MYSQL_URL='jdbc:mysql://127.0.0.1:33320/outbox_runtime_test?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true' ./gradlew build --offline
```

실제 ProxySQL/Router를 통과하지 않은 MySQL 직접 연결 결과를 운영 프록시 검증으로 보고하지 않는다. 운영 AWS 접근 및 실제 S3 권한 검증이 안 되면 미검증으로 기록한다.

참고: [MySQL READ WRITE 트랜잭션](https://dev.mysql.com/doc/refman/8.4/en/commit.html), [ProxySQL transaction_persistent](https://proxysql.com/documentation/users-configuration/).
