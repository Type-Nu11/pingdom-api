# 사용자 데이터 처리 정책

사용자 데이터 export와 기존 탈퇴/삭제/익명화 흐름에서 발생하는 개인정보 처리 이력 관리 기준을 정리한다.

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

기록 대상 action:

| Action | 발생 시점 |
|---|---|
| `EXPORT_REQUESTED` | 사용자가 데이터 export를 요청할 때 |
| `WITHDRAWAL_REQUESTED` | 사용자가 회원 탈퇴를 요청할 때 |
| `ANONYMIZED` | 탈퇴 과정에서 개인정보 익명화가 수행될 때 |
| `DELETED` | 보존 기간 만료로 탈퇴 사용자가 최종 삭제될 때 |

이력 저장은 메인 트랜잭션 커밋 이후 이벤트 리스너에서 처리한다. 이력 저장 실패는 메인 요청 성공 여부에 영향을 주지 않도록 로그만 남기고 예외를 전파하지 않는다.

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

## 운영 대응 절차 작성

### 사용자 export 문의

1. 사용자가 로그인 가능한 상태인지 확인한다.
2. 사용자가 `GET /users/me/export`를 호출하도록 안내한다.
3. export 범위가 사용자 기본 정보, 전체 장소 북마크, 최신 좋아요 50개임을 안내한다.
4. 관리자는 개인정보 처리 이력에서 `action=EXPORT_REQUESTED`를 조회해 요청 여부를 확인한다.
5. 신고, 게시글 등 현재 export 범위 밖의 데이터 요청은 별도 정책 검토 후 후속 작업으로 분리한다.

### 회원 탈퇴 문의

1. 사용자가 탈퇴 요청을 완료했는지 확인한다.
2. 탈퇴 직후 사용자는 `WITHDRAWN` 상태이며 로그인 및 보호 API 접근이 차단됨을 안내한다.
3. 사용자명, 이메일, 프로필 이미지 등 개인정보는 즉시 익명화/삭제됨을 안내한다.
4. 관리자는 개인정보 처리 이력에서 `WITHDRAWAL_REQUESTED`, `ANONYMIZED` action을 확인한다.

### 최종 삭제 문의

1. 탈퇴 보존 기간 설정인 `user.withdrawal.retention`을 확인한다. 기본값은 `P30D`다.
2. 보존 기간이 지나지 않은 사용자는 아직 최종 삭제 대상이 아니다.
3. `WithdrawnUserPurgeWorker` 로그에서 최종 삭제 배치 수행 여부를 확인한다.
4. 관리자는 개인정보 처리 이력에서 `DELETED` action을 확인한다.

### 개인정보 처리 이력 장애 대응

1. 이력 저장 실패 로그를 확인한다.
2. 이력 저장 실패는 export, 탈퇴, 삭제 요청 성공 여부에 영향을 주지 않는다.
3. DB 연결, migration 적용 상태, `privacy_processing_history` 테이블 존재 여부를 확인한다.
4. cleanup 지연 시 `privacy.processing-history.*` 설정과 worker 로그를 확인한다.
