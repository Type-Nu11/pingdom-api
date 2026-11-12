# 인증 메일 Outbox 재처리 계약 (#1865)

인증·비밀번호 재설정 handler는 `eventId + EMAIL + SHA-256("EMAIL:" + recipient.trim())`의 저장된 성공 또는 `EMAIL_REQUEST_STALE` 종료 기록을 먼저 조회한다. 성공은 후속 실패·stale 결과로 덮어쓰지 않는다. stale는 기존 delivery 상태 `FAILED`, `retryable=false`로 남기고 Outbox 처리는 정상 완료한다. 따라서 Outbox `SUCCEEDED`는 메일이 실제 전송되었다는 의미가 아니다. 조회 장애는 stale로 확정하지 않고 발송 없이 재시도한다. stale 종료 기록의 저장 실패도 전파하여 terminal 근거가 커밋될 때까지 Outbox를 완료하지 않는다. FCM 기록 계약은 변경하지 않는다.

발송 준비는 identity의 짧은 읽기 트랜잭션에서 수행한다. 계정의 불변 userId·현재 수신자·탈퇴/정지 상태를 확인하고 인증은 미인증 상태·현재 코드·발급 만료를, 재설정은 token SHA-256·소유자·미사용·만료를 확인한다. 조회 이후 공급자 호출 사이의 상태 변경은 원자적으로 차단하지 않으며 최종 인증/재설정 API가 다시 검증한다. 인증 만료와 같은 시점은 기존대로 허용하고 reset token은 같은 시점에 만료된다.

## Payload 전환과 구형 이벤트

신규 인증 payload에는 `expiresAt`을 포함한다. 인증 코드가 다시 같아져도 이전 발급 만료가 다르면 stale다. userId 또는 인증 발급 만료가 없는 구형 인증 payload는 안전한 요청 식별 근거가 없으므로 미발송 종료한다. userId가 없는 구형 reset payload도 동일하다. 이메일로 다른 계정을 역조회하지 않는다. 운영자는 새 인증 메일/비밀번호 재설정 요청을 안내한다.

`users.email_verification_expires_at`(V1)와 `password_reset_token.expires_at`(V15)은 `TIMESTAMP(6)`이다. 신규 발행자는 저장과 payload를 모두 microsecond 절삭값으로 맞춘다. 기존 reset은 userId와 고유 token hash가 있으므로 나노초 expiry를 실제 DB 표현으로 정확히 정규화하여 대조한다. 임의 ±시간 오차로 허용 범위를 넓히지 않는다. pgjdbc 42.7.4 `TimestampUtils`는 nanos 나머지가 500 이상일 때 다음 microsecond로 반올림한다([공식 소스](https://github.com/pgjdbc/pgjdbc/blob/REL42.7.4/pgjdbc/src/main/java/org/postgresql/jdbc/TimestampUtils.java#L1145-L1164)). 이 변환의 경계와 초 이월은 PostgreSQL 회귀 대상이다.

전환과 롤백 전에는 이메일 worker를 멈추고 pending payload 형식·구형 이벤트 수를 확인한다. 신규 필드를 구버전 consumer가 읽을 수 있다고 가정하지 않는다. 저장된 성공/terminal delivery를 지우거나 이전 이벤트를 새 사용자 요청으로 재구성하지 않는다. 사용자에게 새 요청을 안내하는 UI/관리자 화면의 추가는 이 변경 범위 밖이다.

## 외부 전달과 결과 저장의 한계

#1832의 claim/lease는 Outbox 작업 소유권이다. claim token은 이메일 업무 멱등키가 아니다. lease 상실 후 이미 진행 중인 외부 호출 중첩은 남으며 성공 조회와 조건부 UPSERT도 공급자 호출 자체를 원자적으로 묶지 않는다.

메일 성공 기록은 기존 best-effort 계약을 유지한다. 공급자가 성공한 뒤 delivery 저장이 실패하면 경고 로그의 `status=SUCCEEDED`로 식별하지만 성공 사실을 복구할 수 없다. 이때 Outbox 완료까지 실패하거나 프로세스가 종료되면 재처리에서 중복 전송할 수 있다. 응답 불명·발송 후 기록 전 프로세스 종료도 동일하다. delivery 저장이 커밋된 경우에만 다음 처리에서 발송을 생략한다. 로그에는 eventId·상태·오류 코드·예외 유형만 남기고 수신자·원문 token·인증 코드·원문 예외/stack을 기록하지 않는다.

[Postmark 공식 지원 문서](https://postmarkapp.com/support/article/what-is-an-idempotency-key)는 idempotency key를 지원하지 않는다고 명시한다. 이 구현은 외부 전달의 exactly-once를 보장하지 않는다. 별도 경보 기준이나 공급자 대사/복구 정책은 운영에서 정해야 한다.
