# Issue #1702: 음성 AI 외부 호출의 DB 잠금 점유 축소

## 문제

기존 `VoiceAiSessionService.send()`는 세션을 `PESSIMISTIC_WRITE`로 조회한 뒤 Gemini 호출과 replay 저장을 하나의 트랜잭션에서 처리했다.
따라서 provider 지연 시간만큼 JDBC 커넥션과 같은 세션 행 잠금이 유지됐다. 재전송·갱신·종료 요청도 모두 이 잠금을 기다려야 했다.

## 적용한 해결책

`voice_ai_replay`를 요청 결과 ledger이면서 처리 소유권 ledger로 확장했다.

1. 짧은 트랜잭션에서 세션을 검증하고 `(session_id, request_id)` replay를 `PROCESSING`으로 저장한다.
2. 이 트랜잭션을 커밋한 뒤 Gemini를 호출한다. 이 구간에는 DB 커넥션·행 잠금이 없다.
3. 짧은 트랜잭션에서 같은 소유권 token을 확인한 뒤 `COMPLETED` envelope를 저장한다.
4. 동일 requestId/text의 재전송은 새 provider 호출 없이 완료 결과를 반환한다. 처리 중이면 결과 저장까지 짧게 재조회한다.

세션 행 잠금은 claim, 완료 저장, refresh, close에만 사용한다. 따라서 기존 동기 HTTP 응답과 requestId 재전송 계약은 유지하면서 외부 호출 시간만 트랜잭션에서 분리한다.

## 경쟁 상황과 복구

| 상황 | 처리 |
| --- | --- |
| 동일 ID·동일 text 재전송 | `PROCESSING`을 관찰하고 완료 결과를 재사용한다. |
| 동일 ID·다른 text | `REPLAY_CONFLICT`(409)를 유지한다. |
| provider 실패 | token 소유 `PROCESSING` row를 삭제해 이후 재시도가 새 호출을 시작한다. |
| worker/프로세스 중단 | 최소 30초의 lease가 지나면 다음 재전송이 소유권을 인계받는다. |
| 늦게 끝난 이전 worker | token이 달라 결과 저장에 실패하므로 최신 결과를 덮어쓰지 못한다. |
| refresh/close 경쟁 | 진행 중 처리 완료를 기다린 뒤 반영한다. stale worker면 lease 만료 후 row를 제거하고 진행한다. |

## 운영 확인 항목

- `gemini.connect-timeout + read-timeout + 5초`와 30초 중 큰 값을 lease로 사용한다. provider의 최대 실행 시간은 이 lease를 넘지 않게 운영한다.
- 배포 후 DB 커넥션 풀의 active/idle 수와 `voice_ai_session` lock wait를 provider 지연 상황에서 비교한다.
- 같은 `sessionId/requestId` 동시 요청에서 Gemini 호출이 하나인지, lease 회수 상황에서 기존 결과가 덮어써지지 않는지 확인한다.

## 관련 파일

- `VoiceAiSessionService`: provider 호출 조정 및 재전송 대기
- `VoiceAiReplayTransactionService`: 짧은 트랜잭션과 소유권 fencing
- `V136__add_voice_ai_replay_processing_lease.sql`: 상태·token·lease 시각 추가
