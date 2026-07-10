# v1 API 경로 정책

## 목적

Pingdom Backend v1 API에서 사용하는 경로 규칙을 문서화한다.
이 문서는 신규 endpoint 추가, 기존 endpoint 리팩터링, Swagger 검수 시 기준으로 사용한다.
오류 응답의 코드·본문 형태와 클라이언트 재시도 판단은
[API 오류 코드 및 재시도 정책](api-error-code-retry-policy.md)을 따른다.
리팩터링 시 모듈 경계, OpenAPI 호환성, 운영 확인은
[Pingdom 2.0 리팩터링 범위와 성공 지표](architecture/pingdom-2.0-refactoring.md)를 함께 따른다.
DB migration과 함께 공개 API를 변경하는 배포 순서와 rollback 판단은
[데이터 마이그레이션, 호환 API, 롤백 정책](architecture/pingdom-2.0-migration-compatibility-rollback.md)을
따른다.

## 현재 prefix 분류

| 영역 | prefix | 용도 |
|---|---|---|
| 공통/인증 | `/auth` | 회원가입, 로그인, 토큰 재발급, 로그아웃, 이메일 인증, 비밀번호 재설정 |
| 사용자 | `/users` | 내 정보, 회원 탈퇴, 북마크 조회, OAuth 계정 연결 |
| 앱 지도/게시글 | `/map` | 게시글, 좋아요, 신고, 좌표 기반 장소 생성, 북마크 생성/삭제 |
| 앱 장소 조회 | `/place` | 장소 목록/상세, 자동완성, 추천 조회 |
| 알림 | `/notifications`, `/firebase` | 알림 설정, FCM 토큰 관리 |
| 관리자 | `/admin` | 관리자 조회/처리 API |

## 현재 경로 구성 메모

- 조회/컬렉션 성격 resource는 복수형을 우선 사용한다.
- 단건 식별은 `/{id}` 형태를 사용한다.
- 사용자 본인 resource는 `/me`를 사용한다.
- 관리자 API는 `/admin` 하위에 도메인 resource를 둔다.
- v1에는 `/map`, `/place`처럼 도메인 기준 prefix와 `/firebase` 같은 외부 연동 기준 prefix가 함께 존재한다.

대표 예시는 아래와 같다.

| 분류 | 예시 |
|---|---|
| 컬렉션 조회 | `/map/posts`, `/admin/reports`, `/admin/notification-deliveries` |
| 단건 조회 | `/place/{placeId}`, `/map/posts/{postId}`, `/admin/places/{placeId}` |
| 내 resource | `/users/me`, `/users/me/export`, `/users/me/oauth-accounts/google` |
| 개인정보 처리 | `DELETE /users/me`, `GET /admin/privacy-processing-histories` |
| 하위 resource | `/admin/reports/reported-users/{userId}`, `/place/recommendations/{requestId}/explanation` |

## resource naming 규칙

### 1. 기본 규칙

- 신규 endpoint는 resource 명을 영어 소문자 kebab-case로 작성한다.
- 컬렉션 resource는 복수형을 기본값으로 사용한다.
- 단건 조회만 존재하더라도 resource 자체 의미가 컬렉션이면 복수형을 유지한다.
- path variable 이름은 resource 의미가 드러나는 `/{userId}`, `/{postId}`, `/{historyId}`를 우선 사용한다.
- 단일 resource에 대한 기본 CRUD 경로에서 의미가 이미 충분히 명확하면 `/{id}` 사용도 허용한다.

### 2. 단수형 허용 기준

- aggregate root 또는 도메인 prefix 자체를 나타내는 경우 단수형을 허용한다.
- v1에서 이미 공개된 prefix는 하위 호환을 위해 유지한다.

허용 예시:

- `/auth`
- `/place`
- `/map`

### 3. 복수형 권장 대상

- 실제 목록, 컬렉션, 관리 대상 집합을 표현하는 경우

예시:

- `/users`
- `/posts`
- `/reports`
- `/notifications`
- `/notification-deliveries`
- `/report-appeals`

## action path 규칙

### 1. 우선 원칙

- 가능하면 HTTP Method로 의도를 표현하고 resource path에는 명사를 유지한다.
- 상태 변경은 `POST`, `PATCH`, `DELETE`와 resource path 조합으로 우선 설계한다.

권장 예시:

- `PATCH /admin/report-appeals/{appealId}`
- `PATCH /admin/places/{placeId}/coordinates`
- `DELETE /users/me/oauth-accounts/google`

### 2. action path 사용 허용 기준

다음 중 하나에 해당하면 action path를 허용한다.

- 단순 CRUD보다 명시적 비즈니스 행위가 더 중요할 때
- 동일 resource에 대해 일반 수정과 구분되는 명령을 드러내야 할 때
- 외부 클라이언트가 이미 사용 중인 v1 레거시 경로를 유지해야 할 때

예시:

- `/auth/token/refresh`
- `/admin/report-appeals/{appealId}/approve`
- `/admin/reports/{reportId}/accept`
- `/admin/reports/{reportId}/decline`
- `/admin/places/recommendation-snapshots/resync`

### 3. 신규 action path 작성 규칙

- action 명은 동사 원형 또는 의미가 명확한 명령형 명사를 사용한다.
- 불필요한 `do`, `process`, `handle` 같은 포괄적 단어는 지양한다.
- 같은 도메인 안에서는 `approve/reject`, `accept/decline`처럼 용어를 한 쌍으로 맞춘다.

## 하위 호환 유지 기준

- 운영 중인 v1 공개 경로는 클라이언트 협의 없이 rename 하지 않는다.
- 경로 의미만 개선하려는 리팩터링은 내부 코드 구조, Swagger 설명, 문서 정리로 우선 대응한다.
- 신규 규칙 적용이 필요하더라도 기존 path 제거 대신 다음 순서로 진행한다.

1. 신규 path 추가
2. Swagger 및 문서에 deprecate 표기
3. 클라이언트 전환 완료 후 구버전 제거

## v1 레거시 예외

아래 경로는 현재 규칙상 완전한 이상형은 아니어도 하위 호환을 위해 유지한다.

- `/map/post/create`
- `/map/post/{id}/update`
- `/map/post/{id}/delete`
- `/admin/ad`
- `/firebase/fcm-token`

신규 endpoint에서는 위 패턴을 그대로 복제하지 않고, 가능하면 복수형 resource와 HTTP Method 중심으로 설계한다.
