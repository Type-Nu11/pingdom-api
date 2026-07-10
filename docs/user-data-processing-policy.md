# 사용자 역할별 개인정보 처리 및 동의 정책

이 문서는 현재 구현된 사용자 데이터 export, 탈퇴·익명화·최종 삭제, 개인정보 처리 이력의
책임과 운영 기준을 정리한다. 개인정보 처리 이력은 처리 사실을 추적하기 위한 감사 이력이며,
사용자의 동의 사실을 대체하지 않는다.

## 역할, 책임 및 동의 범위

| 주체 | 현재 권한·책임 | 개인정보 처리 이력 |
|---|---|---|
| 사용자(`USER`) | 인증된 본인 데이터 export 요청과 본인 계정 탈퇴를 수행한다. 다른 사용자의 데이터에는 접근할 수 없다. | export, 탈퇴 요청, 익명화 이벤트의 대상·수행자로 기록된다. |
| 관리자(`ADMIN`) | `/admin/privacy-processing-histories`에서 처리 이력을 조회한다. 현재 관리자가 사용자를 대신해 export·탈퇴·삭제를 실행하는 API는 없다. | `actor_type=ADMIN` enum은 존재하지만, 현재 구현은 관리자 처리 이벤트를 발행하지 않는다. |
| 시스템(`SYSTEM`) | 보존 기간이 지난 탈퇴 사용자를 배치로 최종 삭제하고 개인정보 처리 이력을 정리한다. | 최종 삭제 이벤트의 수행자로 기록되며 `actor_user_id`는 `null`이다. |

이 문서에서 말하는 **동의(consent)** 는 특정 목적의 개인정보 처리에 대한 명시적 의사 표시와
그 버전·시각·철회 이력을 뜻한다. 현재 애플리케이션에는 동의 수집·철회·버전 관리용
Entity, Flyway migration, 사용자 API, 관리자 조회 API, 처리 이벤트가 없다. 따라서 이 문서는
동의 획득 여부나 법적 근거를 판정하지 않으며, 동의 기능이 필요하면 아래 항목을 별도 이슈에서
결정한다.

- 처리 목적과 필수·선택 동의 구분
- 동의 문서 버전, 동의·철회 시각, 보관 기간
- 철회 시 사용자 기능과 보관 데이터에 미치는 영향
- 사용자·관리자 API, Flyway migration, OpenAPI 계약 및 운영 절차

## 상태 전이와 처리 이력

```text
정상 사용자
  ├─ GET /users/me/export ────────────────> 정상 사용자 + EXPORT_REQUESTED
  └─ DELETE /users/me ────────────────────> WITHDRAWN
                                                └─ WITHDRAWAL_REQUESTED + ANONYMIZED
WITHDRAWN
  └─ 보존 기간 만료 후 시스템 배치 ───────> 계정 최종 삭제 + DELETED
```

- export는 사용자 상태를 바꾸지 않으며 이력만 남긴다.
- 탈퇴는 사용자명을 포함한 개인정보를 즉시 익명화하고 `WITHDRAWN` 상태로 전환한다.
- 최종 삭제는 기본 30일 보존 기간이 지난 `WITHDRAWN` 사용자만 대상으로 한다.
- 개인정보 처리 이력의 기본 보관 기간은 3개월이며, 사용자 계정 보존 기간과 독립적으로 정리된다.

## Export 대상 데이터 정리

사용자는 인증된 상태에서 본인의 데이터만 export할 수 있다.

```http
GET /users/me/export
Authorization: Bearer {accessToken}
```

클라이언트에서 별도 `userId`를 받지 않고, 서버의 인증 컨텍스트에서 사용자 ID를 가져와 조회한다.

응답에는 신고 데이터가 포함되지 않는다.

| 영역 | 소스 | 응답 필드 | 제공 기준 |
|---|---|---|---|
| 사용자 기본 정보 | `users` | `id`, `username`, `profileImageUrl` | 본인 데이터 |
| 북마크 | `map_bookmark` | `id`, `placeId` | 전체 제공 |
| 좋아요 | `map_image_like` | `mapImageId` | 최신 최대 50개 |

응답 형태:

```json
{
  "user": {
    "id": 1,
    "username": "pingdom_user",
    "profileImageUrl": "https://cdn.pingdom.com/profiles/user1.png"
  },
  "bookmarks": [
    {
      "id": 10,
      "placeId": 123
    }
  ],
  "likedMapImageIds": [981, 812, 700]
}
```

현재 범위에서는 별도 파일 생성, 큐, 다운로드 링크 없이 동기 JSON 응답으로 처리한다.

## 탈퇴 유예/삭제 정책 정리

회원 탈퇴, 익명화, 탈퇴 사용자 최종 삭제 기능은 기존 구현을 따른다. 이번 작업에서는 해당 흐름에서 발생하는 개인정보 처리 이벤트를 기록한다.

탈퇴 요청이 완료되면 사용자는 즉시 `WITHDRAWN` 상태가 되고, 로그인 및 보호 API 접근이 차단된다.

탈퇴 즉시 처리되는 데이터:

| 데이터 | 처리 |
|---|---|
| `username` | `withdrawn_user_{userId}`로 변경 |
| `email` | `withdrawn_user_{userId}@withdrawn.local`로 변경 |
| `password` | 랜덤 탈퇴 비밀번호 값으로 변경 |
| `profileImageUrl` | `null` 처리 |
| `birthYear` | `0` 처리 |
| `language` | `und` 처리 |
| `country` | `UNKNOWN` 처리 |
| `refreshToken`, `fcmToken` | `null` 처리 |
| 이메일 인증 정보 | 초기화 |
| 사용자 제재 상태 | 해제 |

탈퇴 시 연관 데이터 처리:

| 데이터 | 처리 |
|---|---|
| 게시글 작성자 표시명 | `탈퇴 사용자`로 변경 |
| 장소 등록자 표시명 | `탈퇴 사용자`로 변경 |
| 좋아요 | 삭제 |
| 북마크 | 삭제 |
| 알림 | 삭제 |
| FCM 토큰 | 삭제 |
| 알림 설정 | 삭제 |

탈퇴 사용자는 기본 30일 동안 보존한다.

```yaml
user:
  withdrawal:
    retention: P30D
    cleanup-enabled: true
    cleanup-delay: PT24H
    cleanup-initial-delay: PT1H
```

보존 기간이 만료되면 기존 `WithdrawnUserPurgeWorker`가 최종 삭제를 수행한다.

최종 삭제 시 처리:

| 단계 | 처리 |
|---|---|
| 만료 대상 조회 | `status = WITHDRAWN`, `withdrawnAt <= now - retention` |
| 콘텐츠 참조 제거 | 게시글/장소의 `userId`를 `null` 처리 |
| 인증 연계 데이터 삭제 | OAuth 계정 연결 삭제 |
| 연관 데이터 삭제 | FCM 토큰 및 알림 설정 삭제 |
| 계정 최종 삭제 | `users` row 삭제 |

## 개인정보 처리 이력 보관 기준 정리

개인정보 처리 이력은 `privacy_processing_history` 테이블에 저장한다.

| 컬럼 | 의미 |
|---|---|
| `subject_user_id` | 개인정보 처리 대상 사용자 ID |
| `actor_user_id` | 처리를 실행한 사용자 ID. 시스템 처리면 `null` |
| `actor_type` | `USER`, `SYSTEM`, `ADMIN` |
| `action` | 처리 유형 |
| `details` | 처리 설명 |
| `request_id` | 요청 추적 ID |
| `created_at` | 이력 생성 시각 |

`request_id`는 요청 처리 중 MDC에 존재할 때만 기록되므로 시스템 배치 또는 요청 컨텍스트가 없는
처리에서는 `null`일 수 있다.

기록 대상 action:

| Action | 발생 시점 |
|---|---|
| `EXPORT_REQUESTED` | 사용자가 데이터 export를 요청할 때 |
| `WITHDRAWAL_REQUESTED` | 사용자가 회원 탈퇴를 요청할 때 |
| `ANONYMIZED` | 탈퇴 과정에서 개인정보 익명화가 수행될 때 |
| `DELETED` | 보존 기간 만료로 탈퇴 사용자가 최종 삭제될 때 |

일반적인 트랜잭션 경로에서는 `AFTER_COMMIT` 이벤트 리스너가 별도 트랜잭션으로 이력을 저장한다.
다만 listener에 `fallbackExecution=true`가 설정되어 활성 트랜잭션 없이 발행된 이벤트도 처리될 수
있으므로, 이력이 항상 커밋 후에만 기록된다고 가정하지 않는다. 이력 저장 실패는 메인 요청 성공
여부에 영향을 주지 않도록 로그만 남기고 예외를 전파하지 않으며 자동 재시도도 하지 않는다.

최종 삭제처럼 여러 사용자를 한 번에 처리하는 경우에는 벌크 이벤트를 사용해 이력을 `saveAll`로 저장한다.

개인정보 처리 이력은 기본 3개월 보관 후 자동 삭제한다.

```yaml
privacy:
  processing-history:
    retention-months: 3
    cleanup-batch-size: 100
    cleanup-delay: PT24H
    cleanup-initial-delay: PT1H
    cleanup-enabled: true
```

cleanup worker는 만료 이력이 더 이상 없을 때까지 배치 단위 삭제를 반복한다.

## 구현 계약과 문서 연결

| 영역 | 현재 기준 | 이번 문서의 판단 |
|---|---|---|
| 사용자 API | `GET /users/me/export`, `DELETE /users/me` | 기존 경로·응답·인증 계약을 변경하지 않는다. |
| 관리자 API | `GET /admin/privacy-processing-histories` | `ADMIN` 권한의 이력 조회만 제공하며, 사용자 데이터를 대신 처리하는 API는 없다. |
| 개인정보 이력 schema | [V22 개인정보 처리 이력 migration](../src/main/resources/db/migration/V22__create_privacy_processing_history.sql) | 기존 테이블·인덱스를 사용하며 새 Flyway migration은 추가하지 않는다. |
| 처리 이벤트 | `PrivacyProcessingEvent`, `PrivacyProcessingBulkEvent` | export·탈퇴·익명화·최종 삭제 사실을 기록하며, 동의 이력을 기록하지 않는다. |
| OpenAPI 기준선 | `src/test/resources/openapi-baseline` | API 코드와 DTO를 바꾸지 않으므로 갱신하지 않는다. |

관련 기준은 [v1 API 경로 정책](api-path-policy.md),
[도메인 이벤트 기준](architecture/pingdom-2.0-domain-events.md),
[DB migration 운영 Runbook](database-migration.md),
[리팩터링 적용·복구 Runbook](refactoring-rollout-runbook.md)을 함께 따른다.

## 운영 대응 절차

### 사용자 export 문의

1. 사용자가 로그인 가능한 상태인지 확인한다.
2. 사용자가 `GET /users/me/export`를 호출하도록 안내한다.
3. export 범위가 사용자 기본 정보, 전체 장소 북마크, 최신 좋아요 50개임을 안내한다.
4. 관리자는 개인정보 처리 이력에서 `action=EXPORT_REQUESTED`를 조회해 요청 여부만 확인한다. 현재 관리자 API는 export 응답 본문을 재조회하거나 사용자를 대신해 export를 실행하지 않는다.
5. 신고, 게시글 등 현재 export 범위 밖의 데이터 요청은 별도 정책 검토 후 후속 작업으로 분리한다.

### 회원 탈퇴 문의

1. 사용자가 탈퇴 요청을 완료했는지 확인한다.
2. 탈퇴 직후 사용자는 `WITHDRAWN` 상태이며 로그인 및 보호 API 접근이 차단됨을 안내한다.
3. 사용자명, 이메일, 프로필 이미지 등 개인정보는 즉시 익명화/삭제됨을 안내한다.
4. 관리자는 개인정보 처리 이력에서 `WITHDRAWAL_REQUESTED`, `ANONYMIZED` action을 확인한다.
5. 이력 확인은 탈퇴 성공 여부의 유일한 근거가 아니다. 원 요청의 응답, `X-Request-Id`, 애플리케이션 로그를 함께 확인한다.

### 최종 삭제 문의

1. 탈퇴 보존 기간 설정인 `user.withdrawal.retention`을 확인한다. 기본값은 `P30D`다.
2. 보존 기간이 지나지 않은 사용자는 아직 최종 삭제 대상이 아니다.
3. `WithdrawnUserPurgeWorker` 로그에서 최종 삭제 배치 수행 여부를 확인한다.
4. 관리자는 개인정보 처리 이력에서 `DELETED` action을 확인한다.
5. 운영자가 사용자 row 또는 개인정보 처리 이력을 직접 삭제해 최종 삭제를 우회하지 않는다.

### 개인정보 처리 이력 장애 대응

1. 이력 저장 실패 로그를 확인한다.
2. 이력 저장 실패는 export, 탈퇴, 삭제 요청 성공 여부에 영향을 주지 않는다.
3. 원 요청의 응답과 `X-Request-Id`로 사용자 상태가 이미 확정됐는지 먼저 확인한다. 같은 요청을 임의로 재전송하지 않는다.
4. DB 연결, V22 migration 적용 상태, `privacy_processing_history` 테이블 존재 여부를 확인한다.
5. cleanup 지연 시 `privacy.processing-history.*`, `user.withdrawal.*` 설정과 각 worker 로그를 확인한다.
6. 누락 이력의 재생성은 자동 재시도 대상이 아니므로, 대상·처리 시각·중복 가능성을 확인하는 별도 보정 작업으로 판단한다.

## 문서 변경 이력

| 일자 | 이슈 | 내용 |
|---|---|---|
| 2026-07-10 | #646, #647, #648, #649 | 사용자·관리자·시스템 역할, 동의 미구현 범위, 상태 전이, 구현 계약 대조와 운영·장애 대응 기준을 정리했다. |
