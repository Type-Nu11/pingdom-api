# 음성 AI Gateway v1 계약

기준 앱 schema: Type-Nu11/pingdom-app `f7cb022`,
`docs/architecture/voice-assistant/provider-envelope.v1.schema.json`.
서버에 고정한 원본은 `src/main/resources/openapi/provider-envelope.v1.schema.json`이다.
OpenAPI 3.0에서는 const를 단일 enum으로, $defs를 인라인으로 변환한다.
날짜의 실제 유효성, 시작 < 종료, UTF-16 텍스트 길이 등은 runtime validator가 검사한다.
앱도 generated type을 신뢰 경계로 사용하지 않고 최종 unknown을 기존 parser로 검증한다.

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
