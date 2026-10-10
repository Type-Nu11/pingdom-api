# 음성 AI Gateway v1 계약

초기 기준 앱 schema: Type-Nu11/pingdom-app `f7cb022`,
`docs/architecture/voice-assistant/provider-envelope.v1.schema.json`.
서버에 고정한 원본은 `src/main/resources/openapi/provider-envelope.v1.schema.json`이다.
OpenAPI 3.0에서는 const를 단일 enum으로, $defs를 인라인으로 변환한다.
날짜의 실제 유효성, 시작 < 종료, UTF-16 텍스트 길이 등은 runtime validator가 검사한다.
앱도 generated type을 신뢰 경계로 사용하지 않고 최종 unknown을 기존 parser로 검증한다.

Gemini 생성 요청은 같은 고정 원본에서 변환한 `responseJsonSchema`를 사용한다.
숫자 `schemaVersion=1`, 필수 `kind`와 종류별 필드, 추가 필드 금지, 요청별 `id`를 생성 단계에
명시한다. 공급자가 지원하지 않는 길이·pattern 제약과 날짜·시간 등 의미 검증은 기존 runtime
validator를 유지한다. 생성 스키마는 응답 검증을 대신하지 않는다. 실패 분류 로그는
[운영 관측성](../observability.md#공급자-실패-추적)을 따른다.

## 일반 장소 탐색 (#1764)

`searchNearbyPlaces`를 ProviderEnvelope v1에 읽기 전용 명령으로 추가한다. 응답 union은
기존 8개에서 9개로 확장되며 예약 검색·availability·예약 초안 명령의 인자 계약은 유지한다.

```json
{
  "schemaVersion": 1,
  "id": "request-1",
  "kind": "command_request",
  "command": "searchNearbyPlaces",
  "args": { "useCurrentLocation": true, "touristCategory": "CAFE" }
}
```

필수 인자는 boolean `useCurrentLocation` 하나이다. `touristCategory`는 선택 조건이며
기존 `K_POP`, `BEAUTY`, `FASHION`, `CAFE`, `FOOD`, `POP_UP`, `EXHIBITION`, `NIGHTLIFE`, `OTHER`를
사용한다. 카테고리 없는 일반 탐색도 허용한다. 날짜·시간·인원·좌표·route/API·예약 확인 플래그 등
추가 필드는 거부한다. 일반 탐색은 예약 가능 여부를 보장하지 않는다.

“근처에 뭐 있는지 알려줘”, “주변 아무거나 보여줘”, “근처 카페 보여줘”에는 예약 조건을
질문하지 않는다. `useCurrentLocation=true`는 앱에 현재 위치 사용을 요청하는 의도이며,
위치 권한이나 좌표를 이미 확보했다는 의미가 아니다. 실제 권한 확인·좌표·검색 반경·API 실행은
앱의 Command Registry가 담당한다. 앱은 기존 `GET /places`의 위치·선택 카테고리 검색을
재사용하여 실제 결과를 최대 3개 표시하고, 결과가 없으면 장소를 만들어내지 않는다.
앱 결과 표시와 위치 처리는 이 서버 변경으로 구현되거나 검증된 사항이 아니다.

위치 사용이 불가능하고 검색 지역도 없다는 정보가 현재 입력에 있으면
`clarification_request(field=useCurrentLocation)`로 위치나 지역만 확인한다. 현재 명령에는 지역
인자가 없으므로 지역명이 주어진 경우에는 `assistant_message`로 앱의 지역 선택을 안내한다.
`useCurrentLocation=false`의 검색 기준 위치와 지역 선택 상태는 앱이 결정하며, 모델이 임의
좌표를 만들거나 지정한 지역을 현재 위치로 대체하지 않는다. 지역 선택·전달 계약은 후속 작업이다.

명시적인 예약 요청에는 기존 `searchNearbyReservablePlaces`를 사용한다. “내일 두 명 예약할 곳
찾아줘”에 포함된 날짜·인원을 유지하고 필수 시간대 등 누락 조건만 질문한다.
상대 날짜 해석을 위해 서버는 Gemini system instruction에 요청 시점의 현재 날짜와
`Asia/Seoul` 시간대를 제공한다. 사용자 발화에 없는 날짜·시간·인원은 추정하지 않는다.

### 맥락 전달과 선택 장소 제한

요청 DTO는 기존 `text`와 `requestId`이며, 공급자에 전달하는 사용자 입력도 현재 `text` 하나이다.
현재 입력에 포함된 대화·조건은 활용하지만 서버가 이전 발화를 별도로 조회하거나 유지하지 않는다.
대화 이력·구조화된 예약 조건·위치 사용 상태·도구 조회 결과·앱이 검증한 선택 장소 및 availability
전달 계약은 없다. 여러 차례 대화에서 조건을 유지하려면 별도 계약을 정의해야 한다.

“여기로 예약해 줘”는 앱이 검증한 선택 `placeId`·`availabilityId`가 있어야 예약 초안으로 이어질
수 있다. 현재 입력 계약에는 검증된 선택이 제공되지 않으므로 모델은 앱에서 장소를 선택하도록
안내한다. 사용자 텍스트의 숫자 ID를 앱 검증으로 취급하거나 ID를 추측하지 않는다.
조회되지 않은 현재 영업 여부·평점·가격·통화·취소 조건을 단정하지 않는다.
예약 Mutation과 최종 사용자 확인은 기존 앱 흐름의 책임이다.

### 호환성과 완료 검증

구형 앱 parser는 새 명령을 거부할 수 있으므로 서버 지침 활성화 전에 앱의 지원 버전과
반영 순서를 확인해야 한다. 공개 OpenAPI의 `ProviderEnvelopeV1`과 앱의 parser·Registry·생성 타입을
비교한다. 배포 후 인증된 요청으로 일반 탐색·카테고리 탐색·위치 미사용·예약 조건 유지·빈 결과를
검증하고 실제 앱 장소 카드와 최종 확인 전 예약 Mutation 0회를 확인한다.
스키마/validator/요청 구성 단위 테스트는 실제 Gemini 의미 분류나 배포된 응답을 증명하지 않는다.

## API

모든 API는 기존 Bearer JWT 인증을 유지한다. canonical 문서는 `/v3/api-docs`이다.

| API | 성공 | 도메인 오류 |
| --- | --- | --- |
| POST /voice-ai/sessions | 201 sessionId/expiresAt | 공통 인증 오류 |
| POST /voice-ai/sessions/{sessionId}/refresh | 200 sessionId/expiresAt | 403 SESSION_FORBIDDEN, 404 SESSION_NOT_FOUND, 410 SESSION_EXPIRED |
| POST /voice-ai/sessions/{sessionId}/messages | 200 ProviderEnvelopeV1 | 403/404/410, 409 REPLAY_CONFLICT, 502 PROVIDER_UNAVAILABLE/PROVIDER_RESPONSE_INVALID |
| DELETE /voice-ai/sessions/{sessionId} | 204 | 403 SESSION_FORBIDDEN, 404 SESSION_NOT_FOUND |

공통 401 INVALID_TOKEN/EXPIRED_TOKEN과 403 ACCESS_DENIED를 유지한다.
본문 검증 실패는 400 VALIDATION_FAILED와 errors map, JSON 파싱 실패는 400 INVALID_REQUEST_BODY이다.
그 외 도메인 오류는 ErrorResponse(message/code)를 반환한다.
메시지의 429 RATE_LIMIT_EXCEEDED는 controller AOP의 IP 기반 Redis 제한이다.
상담 intro와 제한을 공유하며 기본 1분 10회(설정 가능), replay도 포함한다.
Retry-After는 미지원이다. 즉시 재시도하지 않고 backoff 후 같은 ID/text로 재시도한다.
제한 저장소 장애는 fail-open=false일 때 503 RATE_LIMIT_UNAVAILABLE이다.

## 시간과 재전송

expiresAt은 필수 ISO-8601 offset 문자열(예: 2026-09-17T12:05:00+09:00)이다.
DB의 기존 LocalDateTime 의미와 JVM 기본 시간대를 유지하며 API에서 offset을 포함한다.
다중 인스턴스는 동일 JVM 시간대를 사용해야 하며 DB 시각을 UTC로 재해석하는 변경은 하지 않는다.
생성/갱신 시 5분, 현재 시각이 expiresAt 이상이면 만료이다. 종료·만료 세션은 갱신할 수 없다.
존재하는 본인 세션의 반복 DELETE는 만료 여부와 관계없이 204이다.

requestId는 세션 내 대소문자를 구분하며 최종 envelope.id와 동일하다.
text 원문 UTF-8 SHA-256을 비교하므로 공백 차이도 다른 요청이다.
동일 ID/text의 성공 결과는 재사용하며 다른 text는 409이다.
전송 시작·결과 저장·갱신·종료는 세션 행의 DB 쓰기 잠금으로 짧게 직렬화한다.
provider 호출 중에는 `PROCESSING` replay 소유권만 DB에 남기고 트랜잭션·행 잠금을 해제한다.
진행 중 재요청은 provider를 중복 호출하지 않고 결과 저장까지 잠금 없이 replay를 재조회한다.
소유권은 provider connect/read timeout과 여유 시간을 반영한 lease(최소 30초) 이후에만 인계한다.
인계 뒤 늦게 완료한 기존 worker는 token 비교를 통과하지 못해 결과를 덮어쓸 수 없다.
provider 호출 실패/롤백 후에는 저장 결과가 없으므로 다시 호출할 수 있다.
외부 provider 호출과 DB 커밋은 원자적이지 않으므로 프로세스 장애까지 포함한 exactly-once를 보장하지 않는다.

결과 재사용은 활성 세션에서만 가능하고 refresh로 기간이 연장된다.
세션/replay 자동 삭제 작업은 없으며 영구 보존 기간을 별도 보장하지 않는다.
앱의 epoch/generation, 256개 replay ledger는 앱 소유이다.

## 전송·취소

최종 JSON 1개만 반환하며 SSE/WebSocket/reconnect cursor는 지원하지 않는다.
최종 envelope의 compact JSON UTF-8 크기는 16 KiB 이하이다.
provider command_result와 source 필드는 허용하지 않는다.
provider timeout/RuntimeException은 502 PROVIDER_UNAVAILABLE로 통합한다.
잘못된 응답·크기 초과는 502 PROVIDER_RESPONSE_INVALID이다.
HTTP 200의 protocol_error.code는 HTTP 오류 body의 code와 별개이다.

provider connect/read timeout은 기본 2초/5초이며 설정 가능하다.
이 값은 DB 잠금 대기 등을 포함한 서버 전체 deadline이 아니다.
앱 30초 deadline/네트워크 단절은 서버 작업 취소를 보장하지 않는다.
이미 커밋한 결과는 동일 ID/text로 회수할 수 있다. DELETE는 진행 중 전송이 완료된 후 종료한다.
provider 지연 중에는 DB 커넥션과 세션 행 잠금을 점유하지 않는다.

## 배포 인계

로컬 fixture/OpenAPI 테스트와 실제 provider 호출 성공은 별도 검증이다.
배포 후 https://www.typenull.xyz/v3/api-docs 의 schema·오류 응답·필수 필드를 확인하고
배포 commit, 확인 시각, 인증된 생성/갱신/전송/종료 결과를 기록해야 이슈 완료 조건을 충족한다.
이 문서 자체는 배포 또는 실 provider 검증 완료 증거가 아니다.

### 인증 메시지 경로 검증 (#1759)

1. 실행 중인 애플리케이션 이미지의 commit과 Gemini 활성화·모델·connect/read timeout을
   확인한다. API 키는 존재 여부만 확인한다. `develop` 병합이나 Gemini 직접 진단 성공만으로
   실행 중인 Java 코드가 수정됐다고 판단하지 않는다.
2. 기존 테스트 계정의 정상 로그인으로 Bearer JWT를 확보하고 세션을 생성한다.
   새 requestId로 `안녕` 또는 `hi`를 전송한다. JWT·사용자 식별자·발화 원문을 공유 로그에 남기지 않는다.
3. 응답 HTTP 200, 숫자 `schemaVersion=1`, 전송한 requestId와 같은 `id`,
   `kind=assistant_message`, 문자열 `text`를 확인한다. 같은 ID/text의 재요청은 같은 결과를
   반환하고 공급자를 추가 호출하지 않아야 한다. 테스트 세션을 종료한다.
4. 각 응답의 `X-Request-Id`로 시작·HTTP 결과·검증 실패 로그를 연결한다.
   앱의 traceId가 이 헤더와 같은 값인지 먼저 확인한다. 메시지 본문 requestId는 별도 replay
   식별자이므로 대신 사용하지 않는다. 모델·HTTP 상태·elapsedMs·고정 실패 사유를 기록하고,
   두 종류의 502를 별도로 분류한다. 전체 API가 약 5초 걸렸다는 이유만으로 timeout으로 단정하지 않는다.
5. 앱의 기존 Command Registry와 validator로 command 응답을 확인한다. 필수 인자 누락·잘못된
   자원 ID·금지 필드가 거절되고 예약 준비 후에도 최종 사용자 확인 전 예약 Mutation이 0회인지
   확인한다. 운영에서 의도하지 않은 예약·결제를 실행하지 않는다.

완료 증거에는 배포 commit·확인 시각·응답 상태·X-Request-Id·계약 검사 결과를 남긴다.
개별 모델의 JSON schema 지원·quota·timeout은 실제 실행 설정에서 확인하며, 필요한 추가 수정은
재현된 실패에 한정한다. DB 잠금·replay lease·인증·명령 실행 경계를 변경해야 한다면 별도 범위로 검토한다.
