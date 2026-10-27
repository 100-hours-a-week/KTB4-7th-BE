# 기상청 API허브 특보 인증키 설정·운영 확인

기상청 API허브 특보 조회는 공공데이터포털 단기예보 키와 다른 인증키를 사용합니다.

| 항목 | 값 |
| --- | --- |
| 환경변수 | `KMA_API_HUB_AUTH_KEY` |
| 용도 | 기상청 API허브 특보현황 조회 (`wrn_now_data.php`) |
| 로컬 설정 | 프로젝트 루트의 무시 대상 `.env` |
| 운영 설정 | ECS 태스크의 Secret 주입 |

`DATA_GO_KR_SERVICE_KEY`를 대신 사용하거나 두 키를 서로 바꾸어 넣지 않습니다. API허브에서 발급된 키는 포털이 제공한 URL 인코딩 형태 그대로 입력합니다. 별도로 URL 인코딩하거나 디코딩하면 인증에 실패할 수 있습니다.

## 로컬 실행

1. `.env.example`의 키 이름을 확인합니다. 이 예시 파일에는 실제 키를 기록하지 않습니다.
2. 실제 키는 Git에 추적되지 않는 로컬 `.env`에 `KMA_API_HUB_AUTH_KEY=<발급된 URL 인코딩 키>` 형태로 설정합니다.
3. `.env`, 실제 키가 포함된 명령어, 전체 요청 URL을 터미널·이슈·PR·로그에 출력하지 않습니다.

## 운영 환경 설정

1. 키를 AWS Secrets Manager 또는 Systems Manager Parameter Store의 `SecureString`에 등록합니다.
2. 솔루션 생성 코드를 실행하는 BE ECS 태스크 정의의 `secrets` 항목에서 환경변수 이름 `KMA_API_HUB_AUTH_KEY`를 해당 Secret/파라미터에 연결합니다. 웹·배치 실행이 분리돼 있다면 솔루션 생성을 실제 수행하는 모든 태스크에 연결합니다.
3. 태스크 실행 역할에 해당 Secret/파라미터를 읽을 최소 권한이 있는지 확인합니다.
4. 새 태스크 정의 리비전을 배포하고 실행 중인 BE 태스크가 새 리비전으로 교체됐는지 확인합니다.
5. 태스크 정의와 설정 화면에서는 Secret의 참조와 환경변수 이름만 확인합니다. Secret 값, 전체 API 요청 URL 또는 셸의 전체 환경변수 목록은 출력하지 않습니다.

운영 키 값은 저장소, `.env.example`, 태스크 정의의 평문 환경변수 값, 애플리케이션 로그에 넣지 않습니다.

## 배포 후 확인 순서

1. 기존 운영 헬스체크에서 애플리케이션이 정상 기동했는지 확인합니다. `/actuator/health`는 BE 프로세스의 헬스 확인이며 기상청 API허브 인증 성공을 보장하지 않습니다. 키가 없어도 BE 기동은 막지 않습니다.
2. 기존 솔루션 생성 경로가 한 번 실행된 뒤 CloudWatch에서 다음 안전한 진단 로그를 확인합니다.
   - `Weather alert lookup unavailable for solution context ... reason=KMA_ALERT_AUTH_ERROR` — 인증 실패
   - `... reason=KMA_ALERT_TIMEOUT` — 타임아웃
   - `... reason=KMA_ALERT_KEY_MISSING` — 환경변수 미설정
   - 다른 `KMA_ALERT_*` 값 — 해당 분류의 조회 실패. Secret 값이나 예외 전문은 로그에 남기지 않습니다.
3. 조회가 성공하면 `Solution AI request metrics`에서 `weatherContextIncluded=true`와 `alertsCount`를 확인합니다.
   - `alertsCount=0`: API 조회 성공, 매장 권역에 발효 중인 특보 없음
   - `alertsCount`가 1 이상: API 조회 성공, 해당 개수의 특보가 솔루션 요청에 포함됨
   - `alertsCount=null`: 특보 조회 실패로 `alerts`가 생략됐거나 날씨 문맥 자체가 없는 상태. 같은 시각의 `Weather alert lookup unavailable` 경고와 함께 판별합니다.
4. 특보 조회가 실패해도 기존 예보 기반 `weatherContext`와 솔루션 생성은 유지됩니다. 실패는 특보 없음(`alerts: []`)으로 오인되지 않으며, `alerts` 필드만 생략됩니다.

## 민감정보 보호 확인

- 특보 API 요청은 인증키를 쿼리 파라미터로 전송하므로 전체 URL을 로그하지 않습니다.
- 애플리케이션은 조회 실패 시 예외 전문 대신 제한된 `KMA_ALERT_*` 분류만 로그에 기록합니다.
- `RestClient`의 wiretap/전체 요청 URI 로깅을 켜지 않습니다. CloudWatch·APM에서도 outbound HTTP URL 또는 쿼리 문자열 수집이 활성화돼 있지 않은지 배포 환경에서 확인합니다.
- 인증 오류 테스트와 로그 진단 테스트는 아래처럼 실행합니다.

```bash
./gradlew test --tests 'com.memme.service.solution.external.KmaWeatherAlertServiceTest'
./gradlew test --tests 'com.memme.service.solution.SolutionWeatherContextServiceTest'
./gradlew test --tests 'com.memme.service.solution.RestSalesSolutionAiClientTest'
```
