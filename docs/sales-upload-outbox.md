# 매출 업로드 Outbox — 정책·ERD 검토안

작성일: 2026-10-25. 설계 이슈: [BE #556](https://github.com/100-hours-a-week/KTB4-7th-BE/issues/556).

**상태: 2026-10-25 사용자 승인 완료. 승인된 정책·ERD 명세이며, 구현·검증 완료 여부는 별도 기록한다.**

## 1. 확인한 근거와 빠진 정의

- 기존 Outbox 노션에는 업로드·분석 실행 PENDING 기록과 Outbox를 같은 DB 트랜잭션에 저장한다는 방향만 있다. 테이블·재시도 횟수·선점 만료·중복 실행 처리 기준은 없다.
- API 정의서에는 이미 POST 접수 202/PENDING과 GET /status가 있다. 이번 안은 새 공개 API와 상태 enum을 추가하지 않는다.
- 최신 BE dev `4a2c83c`의 LifecycleService는 접수 트랜잭션 후 메모리 Worker를 호출하는 구조다. ProcessingService는 예외 시 바로 FAILED를 기록한다. Entity의 startProcessing은 PENDING만 허용하므로 PROCESSING 상태 복구에 그대로 재호출할 수 없다.
- 매출 교체 트랜잭션과 분석 스냅샷·완료 기록이 분리돼 있다. 작업 테이블만 추가하고 처음부터 무조건 재실행하면 나중 업로드의 매출을 이전 파일로 덮어쓰거나 중복 부수효과를 만들 수 있다.
- 기존 QueryService/LifecycleService는 최신 analysisRun을 조회하는 부분이 있다. Outbox는 접수 당시 analysisRunId에 고정해 AI 인사이트 재시도 실행과 혼동하지 않아야 한다.
- 검색 가능한 Notion에서 별도 공식 ERD 페이지를 찾지 못했다. 저장소 docs/sales-erd.md와 실제 init SQL에는 기존 FK·필드 설명 차이도 있어, 새 테이블을 이 문서로 임의 확정하지 않는다. 공식 요구사항 원본으로 연결된 이전 Notion 주소는 접근되지 않아 현재 Outbox 계획·API·코드를 확인 근거로 삼았다.

## 2. 이번에 승인받을 범위

### 포함

1. 매출 업로드 접수와 기본 매출 분석 완료까지 DB 작업 기록으로 전달·복구한다.
2. 기존 salesUploadExecutor와 Worker를 유지하고 DB 폴링 Dispatcher를 추가한다. 메시지 브로커는 도입하지 않는다.
3. 접수 기록과 작업 기록의 원자적 저장, 같은 작업 중복 선점 방지, 실행 중 장애 복구를 함께 구현한다.
4. 재전달을 전제로 단계별 체크포인트와 DB 쓰기 시 소유권 검증을 구현한다.
5. 같은 매장의 이 Outbox 작업은 접수 DB 기록 순서로 직렬 실행한다. 복구된 이전 작업이 이후 작업의 데이터를 덮어쓰지 않기 위한 최소 보호다.

### 별도 후속 범위

- Kafka/RabbitMQ 전환, 범용 이벤트 프레임워크, 외부 운영용 재실행 API.
- AI 인사이트·예측·솔루션·랭킹 후처리까지의 영속 전달 보장. 기본 분석 DONE 이후의 기존 후처리는 별도 복구 보장을 하지 않는다.
- 기존 Outbox 밖 경로를 포함한 모든 같은 매장·겹침 기간 업로드 정합성 고도화. Outbox 적용 중에는 직접 Worker 호출 경로를 함께 사용하지 않아야 한다.
- 기존 FAILED/PROCESSING 업로드 전체의 무조건 재처리, 작업 레코드 자동 삭제.

이 안은 Outbox 저장과 중복 선점 방지를 분리 배포하지 않는다. 기존 일정의 ‘동일 업로드 중복 선점 방지’ 작업은 이번 최소 보호와 연결해 중복 구현하지 않고, 일반적인 다중 인스턴스·기간 겹침 검증은 별도 작업과 연결한다.

## 3. 정책 제안

| 항목 | 제안 | 의미 |
| --- | --- | --- |
| 작업 종류 | SALES_UPLOAD_BASIC_ANALYSIS 1종 | 업로드 접수부터 기본 분석까지 |
| 전달 방식 | MySQL 8.4/InnoDB 폴링 + 기존 실행기 | DB에 접수됐으면 실행기 포화 때문에 접수 실패로 바꾸지 않음 |
| 시도 한도 | 최초 포함 총 5회 | 무한 재시도 방지. 실행 중 프로세스 장애도 해당 시도를 소비 |
| 재시도 간격 | 10초 → 30초 → 2분 → 5분, ±20% jitter | 최대 4회 재시도. 최적값을 측정한 수치는 아님 |
| 폴링 | 1초, 인스턴스별 실행 여유만큼 최대 1건 선점 | 메모리 대기열에 DB 작업을 미리 많이 쌓지 않음 |
| 실행 lease | 60초, 10초 간격 갱신 | 처리 최대 시간이 아니라 소유권 유효 기간. DB 기준 시각 사용 |
| 같은 매장 | 가장 이른 비종료 작업 우선 | 앞선 작업이 재시도 대기 중이면 뒤 작업도 대기 |
| 정상 오류 | 파일 형식/내용 불일치·빈 파일·확인된 원본 부재 등 즉시 최종 실패 | 같은 입력의 무의미한 반복 방지 |
| 일시 장애 | 분류 가능한 DB deadlock/일시 연결 오류·일시 저장소 장애만 재시도 | 모든 RuntimeException을 자동 재시도하지 않음 |
| 알 수 없는 오류 | 안전한 자동 재실행 대상으로 간주하지 않고 최종 실패·조사 | 오류 메시지 문자열이 아니라 예외 타입/원인으로 분류 |
| 조회 중 상태 | 최초 실행 전 PENDING, 실행·재시도 대기 중 PROCESSING | 새 공개 RETRYING 상태를 추가하지 않음 |
| 최종 실패 | 업로드·접수 당시 분석 실행 FAILED + Outbox DEAD | 한 트랜잭션에서 기록 |
| 성공 | 기본 분석 스냅샷·업로드/분석 실행 COMPLETED·Outbox DONE | 한 트랜잭션에서 기록 |
| 파일 접근 | 복구할 인스턴스에서도 같은 storageKey를 읽을 수 있어야 함 | 다중 인스턴스는 공유 저장소 또는 공유 볼륨 필요 |
| 보관 | 첫 버전 자동 삭제 없음 | READY/RUNNING 작업의 원본 파일도 삭제하지 않음 |

원본 파일·사용자 정보·SQL 상세를 작업 오류 메시지에 그대로 저장하지 않는다. 내부 오류 분류 코드와 정제된 최대 500자 메시지만 기록한다. 모든 시간은 DB의 UTC 기준으로 판정하고 화면 표시는 기존 정책을 따른다.

## 4. 상태와 체크포인트

```text
READY ── 선점 ──→ RUNNING ── 기본 분석 커밋 ──→ DONE
 ↑                   │
 └─ 재시도 가능 ──────┤
                     └─ 영구 오류 / 시도 한도 ──→ DEAD

checkpoint:
ACCEPTED → DATA_APPLIED → BASIC_COMPLETED
```

- READY는 새 작업과 재시도 대기를 포함한다. available_at이 지난 작업만 가져온다.
- RUNNING은 현재 lease 소유자만 작업을 진행할 수 있다. Dispatcher에 전달됐다는 이유만으로 DONE으로 바꾸지 않는다.
- DONE/DEAD는 이 버전에서 재실행하지 않는 종료 상태다.
- DATA_APPLIED는 매출 교체·일별 집계와 같은 트랜잭션에서 기록한다. 이 체크포인트가 있으면 재시도 시 파싱은 다시 할 수 있지만 매출 교체는 반복하지 않는다.
- BASIC_COMPLETED는 분석 스냅샷·업로드 및 초기 분석 실행 완료·Outbox DONE과 함께 커밋한다. 완료 후 재전달은 무시한다.

FAILED를 PENDING으로 무조건 되돌리는 구현은 하지 않는다. 실패 확정 전 일시 장애는 PROCESSING을 유지하며 Outbox가 다음 시각을 관리한다. lease 만료 후 복구는 현재 체크포인트부터 재개하고 기존 Entity 상태 전이 규칙을 명시적으로 확장한다.

## 5. 트랜잭션과 중복 실행 보호

### 5.1 접수 트랜잭션

```text
원본 파일 저장
DB 트랜잭션 시작
  stores 행 잠금: 같은 매장의 접수 DB 기록 순서를 직렬화
  sales_uploads PENDING 생성
  analysis_runs PENDING 생성
  sales_upload_outbox READY 생성, 초기 analysis_run_id 고정
커밋
202 / PENDING 반환
```

파일 저장과 DB 저장은 여전히 분산 트랜잭션이 아니다. DB 접수 실패 시 저장 파일 정리는 별도 보상/정리 작업으로 다루며 원자성을 과장하지 않는다. 기존 Worker 직접 호출은 Outbox 적용 경로에서 제거한다.

### 5.2 선점과 만료 복구

Dispatcher는 실행 슬롯을 확보한 후 짧은 트랜잭션으로 후보를 찾는다. 매장 행 → 작업 행의 공통 잠금 순서를 지키고, 같은 매장에 더 이른 READY/RUNNING 작업이 없는지 잠금 안에서 재확인한다. 첫 작업의 lease가 살아 있거나 재시도 시각이 미래라면 뒤 작업으로 건너뛰지 않는다.

작업 선점마다 새 claim_token을 발급하고 lease_until, lease_owner, attempt_count를 갱신한다. 실행 등록이 거절되면 해당 토큰을 조건으로 READY로 되돌리고 그 등록 실패가 시도 한도를 소비하지 않게 조정한다. 이미 소유권이 바뀐 행에는 이 처리를 적용하지 않는다. 선점 직후 프로세스가 죽으면 lease 만료로 복구한다.

MySQL FOR UPDATE/SKIP LOCKED는 후보 경합을 줄이는 수단으로 검토한다. SKIP LOCKED만으로 매장별 순서가 지켜진다고 가정하지 않는다. 최종 쿼리와 인덱스는 실제 MySQL의 2개 연결 경쟁 테스트 및 EXPLAIN으로 확정한다.

### 5.3 lease 만료가 중복 실행 안전성을 자동 보장하지 않는다

60초가 지나도 이전 Worker가 살아 있을 수 있다. 마지막 Outbox 완료 UPDATE만 토큰으로 검사하면, 이미 이전 Worker가 매출을 잘못 쓴 뒤일 수 있다.

따라서 **매출 교체·체크포인트·분석 스냅샷·업로드 상태 변경 등 각 DB 쓰기 트랜잭션은 매장 행 → 작업 행을 잠근 뒤 현재 토큰·RUNNING·lease·최선행 작업 여부를 확인하고 같은 트랜잭션 안에서 쓴다.** 소유권을 잃은 Worker는 DB 부수효과를 수행하지 않는다. 완료/실패/재시도 갱신도 같은 보호를 사용한다.

파일 읽기·파싱·분석 계산 동안에는 긴 DB 트랜잭션을 유지하지 않는다. 계산 후 결과 저장 전에 소유권을 다시 검사한다. DB 쓰기 중에는 해당 작업 행 잠금 때문에 다른 선점자가 교체할 수 없으며, 작업 갱신 후 lease를 연장한다. 계산 중 갱신에 실패하면 다음 쓰기를 중단하고 복구를 기다린다.

### 5.4 체크포인트와 데이터는 반드시 함께 커밋

```text
트랜잭션 A: 매출 교체 + 일별 집계 + 적용 건수 + DATA_APPLIED
트랜잭션 B: 기본 분석 스냅샷 + 초기 분석 실행 COMPLETED
            + 업로드 COMPLETED + BASIC_COMPLETED + DONE
```

현재 LifecycleService의 REQUIRES_NEW 완료 기록을 그대로 트랜잭션 B 안에서 호출하면 분리 커밋되므로 위 원자성을 만족하지 못한다. Outbox용 원자적 완료 메서드를 별도로 설계해야 한다. 초기 analysisRunId를 직접 사용하고 최신 실행 조회로 대체하지 않는다.

같은 매장 다음 작업은 앞 작업이 DONE/DEAD일 때만 시작한다. 이 규칙과 DATA_APPLIED 체크포인트를 함께 사용해 이전 파일의 복구가 이후 업로드 반영을 뒤집지 않게 한다. 독립적인 다른 매장 간에는 별도 인스턴스에서 병행할 수 있다.

## 6. 승인된 ERD 정의 — 운영 DB 미적용

새 테이블명 제안: `sales_upload_outbox`. 매출 기본 분석 전용의 영속 작업 테이블이며 외부 브로커 발행 완료를 뜻하는 PUBLISHED 상태는 사용하지 않는다. 범용 메시지 Outbox로의 확장은 별도 설계한다.

| 컬럼 | 제안 타입 | NULL | 용도 |
| --- | --- | --- | --- |
| id | BIGINT UNSIGNED | N | PK, 자동 증가 |
| store_id | BIGINT UNSIGNED | N | 매장별 작업 순서·조회 |
| upload_id | BIGINT UNSIGNED | N | 업로드 참조 |
| analysis_run_id | BIGINT UNSIGNED | N | 접수 당시 분석 실행 참조 |
| event_type | VARCHAR(50) | N | SALES_UPLOAD_BASIC_ANALYSIS |
| status | VARCHAR(16) | N | READY/RUNNING/DONE/DEAD |
| checkpoint | VARCHAR(24) | N | ACCEPTED/DATA_APPLIED/BASIC_COMPLETED |
| attempt_count / max_attempts | INT UNSIGNED | N | 시도 수/한도, 기본 0/5 |
| available_at | DATETIME(6) | N | 최초/재시도 실행 가능 시각 |
| claim_token | CHAR(36), ascii_bin | Y | 선점마다 새 UUID, 이전 Worker 차단 |
| lease_owner | VARCHAR(100) | Y | 인스턴스 식별 정보 |
| lease_until | DATETIME(6) | Y | 선점 만료 시각 |
| applied_record_count | INT UNSIGNED | Y | DATA_APPLIED 이후 완료 기록에 사용 |
| last_error_code | VARCHAR(50) | Y | 내부 오류 분류 |
| last_error_message | VARCHAR(500) | Y | 정제된 오류 설명 |
| created_at / updated_at | DATETIME(6) | N | 생성/갱신 시각 |
| finished_at | DATETIME(6) | Y | DONE/DEAD 확정 시각 |

관계·제약 제안:

- stores 1:N Outbox, sales_uploads 1:1 해당 작업, analysis_runs 1:1 해당 작업. FK 삭제 연쇄는 사용하지 않는다.
- UNIQUE(event_type, upload_id): 같은 업로드에 같은 기본 분석 작업을 두 번 등록하지 않는다. 별도 파일 재업로드는 다른 uploadId이므로 허용한다.
- 인덱스 (status, available_at, id), (status, lease_until, id), (store_id, status, id).
- RUNNING인 경우에만 claim_token·lease_owner·lease_until이 존재하도록 CHECK를 검토한다.
- DATA_APPLIED/BASIC_COMPLETED는 applied_record_count 필수, DONE은 BASIC_COMPLETED 필수, 종료 상태는 finished_at 필수.
- store_id와 analysis_run_id는 접수 트랜잭션에서 해당 업로드와 일치하는 값만 저장한다. UNIQUE/FK만으로 업로드·실행·매장의 모든 조합 일치를 증명한다고 주장하지 않는다.

실행하지 않은 검토용 DDL은 `schema-proposal.sql`에 별도 작성했다. 승인 후 공식 ERD의 타입·제약과 맞춰 Entity/Repository·운영 마이그레이션·테스트 스키마를 함께 정합화한다. MySQL init SQL만 수정해 이미 존재하는 운영 DB가 바뀐다고 간주하지 않는다.

## 7. 기존 API·화면에 미치는 영향

- POST 202/PENDING, 기존 ID 필드와 GET /status 계약을 유지한다.
- DB 작업 기록이 커밋됐으면 실행기 포화만으로 접수를 500으로 바꾸지 않는다. Dispatcher가 여유가 생기면 처리한다. DB 기록 자체가 실패하면 접수 오류다.
- 일시 장애 동안 PROCESSING이 길어질 수 있으므로 운영 메트릭으로 재시도·최장 대기를 확인한다. 화면에 새 상태 필드나 재시도 횟수를 노출하는 API는 이번에 추가하지 않는다.
- processingPhase는 마지막 기본 처리 단계다. 재시도 횟수나 정확한 잔여 시간을 뜻하지 않는다.
- AI 인사이트 FAILED·retryable 계약은 기존대로 별도 유지한다. 전체 업로드 복구와 AI 인사이트 재시도를 섞지 않는다.

## 8. 승인 후 구현 순서와 역할별 이슈 초안

| 순서 | 역할 | 산출물·완료 기준 |
| --- | --- | --- |
| 0 | 정책·ERD 승인 | 이 문서의 범위·수치·스키마 검토, 공식 정의에 반영 |
| 1 | Entity·Repository·스키마 | Outbox 저장/선점/토큰 검사, 유일성·인덱스·FK, MySQL 경쟁 테스트 |
| 2 | 접수 Service | PENDING·분석 실행·Outbox 원자 저장, 직접 Worker 호출 제거 |
| 3 | 처리 Service | 접수 당시 실행 고정, 실패 분류, 단계 재개, 데이터+체크포인트 원자성 |
| 4 | Dispatcher·실행 설정 | 실행 슬롯, 폴링·lease 갱신·만료 복구·종료 처리 |
| 5 | 통합·장애 검증·관측 | 실제 MySQL 다중 연결, 프로세스 종료 복구, 메트릭·운영 절차 |

공개 DTO/Controller 변경이 없으면 빈 이슈·커밋은 만들지 않는다. 각 구현 이슈는 해당 작업 시작 전에 만들고 dev 기준 역할별 브랜치·한글 커밋·관련 테스트로 관리한다. DB·설정만 선행 병합될 때 새 Dispatcher는 꺼진 상태로 두고, 적용 경로가 완성된 뒤 명시적으로 전환한다. 기본값은 비활성화로 제안한다.

설계 #556 사용자 승인 후 역할별 구현 이슈 #557(Entity·Repository·스키마), #558(접수), #559(처리 복구), #560(Dispatcher), #561(검증·관측)을 생성했다. PR은 각 역할의 선행 변경이 dev에 반영되고 해당 브랜치 테스트·빌드가 통과한 뒤 생성한다.

## 9. 필수 검증 시나리오

| 장애/경쟁 지점 | 기대 결과 |
| --- | --- |
| Outbox INSERT 실패 | 업로드·초기 분석 실행도 롤백, 접수 성공 반환 금지 |
| 접수 커밋 직후 프로세스 종료 | DB에 READY 작업 남음, 재시작 후 실행 |
| 두 Dispatcher가 같은 작업 조회 | 유효 소유자는 하나, 다른 선점자는 처리하지 않음 |
| 선점 후 Worker 실행 전 종료 | lease 만료 후 재선점, 최대 시도 규칙 적용 |
| lease 만료 후 이전 Worker가 복귀 | 이전 토큰으로 매출/스냅샷/상태/체크포인트 변경 불가 |
| 매출 교체 도중 트랜잭션 실패 | 데이터와 DATA_APPLIED 모두 롤백 |
| 매출 교체 커밋 후 프로세스 종료 | DATA_APPLIED부터 재개, 매출 재교체 금지 |
| 기본 분석 완료 커밋 전 종료 | 스냅샷·COMPLETED·DONE 모두 같이 롤백 또는 같이 커밋 |
| DONE 작업 재전달 | 매출·분석·AI 후처리 중복 실행 안 함 |
| 같은 매장 작업 2개와 첫 작업 재시도 | 뒤 작업은 첫 작업 종료 전 대기, 데이터 적용 순서 유지 |
| 실행기 포화/등록 거절 | DB 작업 유실·업로드 FAILED 전환 없음, 작업은 다시 대기 |
| 파일 내용 오류 | 재시도 없이 DEAD와 업로드/초기 실행 FAILED |
| 일시 장애 후 회복 | 최대 5회 안에서 성공 또는 최종 실패 |
| DB 장애로 heartbeat 실패 | lease 소유권 검증 없는 DB 쓰기 금지 |
| 다른 매장 작업·파일 재업로드 | 독립 처리 가능, 같은 파일의 새 uploadId 접수 허용 |

H2 테스트만으로 SKIP LOCKED·잠금 순서를 검증했다고 보고하지 않는다. 프로세스 종료 테스트는 전용 테스트 DB·원본 파일에서 수행하고 운영 데이터를 사용하지 않는다. 초안 작성 단계에서는 실행하지 않았으며, 승인 후 실제 MySQL 검증과 전체 회귀 결과는 운영 문서 및 구현 보고서에 별도 기록한다.

## 10. 전환·관측·롤백

- 기존 메모리 Worker를 사용하는 서버가 남은 상태에서 Outbox 경로를 동시에 켜지 않는다. 접수를 잠시 제어하고 기존 실행/대기를 비운 뒤 전환하는 절차가 필요하다.
- 기존 PENDING/PROCESSING 이력은 원본 접근·실행 여부를 확인한 목록만 별도 이관한다. 기존 FAILED/COMPLETED를 자동 재생하지 않는다.
- 다중 인스턴스 전 원본 저장소 공유 여부, DB 연결/잠금·복제 방식, lease 갱신 여유와 실행기 용량을 확인한다.
- 메트릭은 상태별 작업 수·가장 오래된 대기 시간·재시도 수·최종 실패 수·lease 복구 수·처리 지연을 사용한다. uploadId·사용자 ID·파일명은 메트릭 태그로 넣지 않는다.
- 롤백 때 이미 접수된 Outbox 작업을 버리고 이전 직접 호출로 돌아가지 않는다. 새 접수 중지·대기 작업 배출 또는 Dispatcher 유지 계획이 먼저 필요하다. 테이블과 작업 기록은 삭제하지 않는다.

## 11. 구현 전 승인 기록 (2026-10-25 완료)

1. ‘기본 매출 분석까지’의 DB 폴링 Outbox 범위와 AI 후처리 영속화 제외.
2. 총 5회·10초/30초/2분/5분 재시도, lease 60초·갱신 10초·폴링 1초의 초기 설정.
3. 같은 매장 직렬 처리 + 단계별 체크포인트 + 모든 DB 쓰기 시 소유권 검사.
4. 신규 sales_upload_outbox 테이블과 제안 관계·제약을 공식 ERD에 반영하는 방향.
5. 이 안을 기준으로 역할별 구현 이슈를 만들고 코드를 진행할지.

저장소 AGENTS.md 및 memme-backend-development/SKILL.md가 공식 문서에 없는 테이블·컬럼·정책을 임의 확정하지 않도록 요구하므로, 이 다섯 결정을 확인받기 전에는 스키마/Entity/Service를 수정하지 않는다. 검토안 작성과 공식 정의 확정은 구분한다.

## 참고

- [MySQL 8.4 Locking Reads](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html): 잠금 조회와 SKIP LOCKED의 큐 처리 용도. 매장별 순서/체크포인트/토큰 정책은 이 프로젝트의 제안 설계다.
- [Transactional Outbox 패턴](https://microservices.io/patterns/data/transactional-outbox.html): 업무 기록과 이벤트 기록의 원자 저장 및 재전달·멱등 처리 필요성. 이번에는 외부 브로커 대신 DB 작업 실행으로 한정한 설계안이다.


## 현재 구현 연결

기본값은 비활성화다. 적용·복구·격리 MySQL 테스트 절차와 관측 범위는 [운영 문서](sales-upload-outbox-operations.md)를 따른다. 날짜는 Outbox 컬럼에만 JDBC LOCAL_DATE_TIME을 지정하고 DB UTC 기준으로 비교해 JVM 시간대 변환을 피한다. 기존 공개 응답·일반 날짜 정책은 변경하지 않는다.
