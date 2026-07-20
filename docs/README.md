![Pingdom Server Repository](https://github.com/user-attachments/assets/b55dadc6-fe93-4989-84ec-77b9ce57b920)

## Service

- **인증/계정**: 이메일 회원가입, 로그인, JWT 토큰 재발급, 로그아웃, Google OAuth2/OIDC, 이메일 인증, 회원 탈퇴와 탈퇴 사용자 정리를 처리합니다.
- **지도 장소**: 좌표 기반 장소 생성, 장소 목록/상세 조회, 장소 검색, 장소 삭제, 장소 북마크를 관리합니다.
- **게시글/이미지**: 지도 장소에 연결된 사진 게시글 업로드, 수정, 삭제, 상세 조회와 S3 이미지 저장을 처리합니다.
- **상호작용**: 게시글 좋아요, 좋아요 취소, 신고 접수와 신고 처리 후속 흐름을 제공합니다.
- **장소 추천**: 위치, 사용자 행동, 장소 품질, 신선도, 유사도, 전환 데이터를 기반으로 장소 추천 후보와 추천 로그를 관리합니다.
- **알림**: 이메일 인증 메일과 Firebase Cloud Messaging 기반 사용자 알림을 발송합니다.
- **관리자 운영**: 사용자 제재, 신고 승인/반려, 게시글 삭제, 광고 관리, 장소 병합, 추천 지표 조회를 제공합니다.
- **비동기 처리**: Outbox 기반으로 이메일 발송, 좋아요 알림, S3 객체 삭제 같은 외부 부수효과를 재시도 가능하게 처리합니다.

## Product Surface

| 영역 | 설명 |
|---|---|
| REST API | Pingdom 앱과 관리자 웹에서 사용하는 HTTP API |
| Auth API | 이메일/비밀번호 로그인, JWT, Google OAuth2/OIDC 인증 API |
| Admin API | 신고, 게시글, 사용자 제재, 장소, 광고, 추천 지표 관리 API |
| Worker/Scheduler | Outbox 이벤트 처리, 만료 제재 해제, 탈퇴 사용자 보관 기간 만료 정리 |
| Persistence | PostgreSQL, PostGIS, Flyway 기반 데이터 저장과 마이그레이션 |
| External Integrations | Google OAuth2, AWS S3, Firebase Cloud Messaging, Postmark |
| API Docs | dev 프로필에서 제공되는 SpringDoc OpenAPI 문서 |
| Build & Deploy | Gradle, Docker, Docker Compose, GitHub Actions, AWS EC2 배포 |

## 운영 문서

- [로컬 개발 환경과 dev seed 데이터](local-development.md)
- [v1 API 경로 정책](api-path-policy.md)
- [API 오류 코드 및 재시도 정책](api-error-code-retry-policy.md)
- [운영 관측성](observability.md)
- [DB 마이그레이션 운영 Runbook](database-migration.md)
- [DB 백업/복구 절차](database-backup-restore.md)
- [로컬 개발 환경](local-development.md)
- [Pingdom 2.0 출시 전환·적용·복구 Runbook](refactoring-rollout-runbook.md)

## 아키텍처 문서

- [Pingdom 2.0 리팩터링 범위와 성공 지표](architecture/pingdom-2.0-refactoring.md)
- [장소 추천 행동 전환 도메인 기준](architecture/place-recommendation-conversion.md)
- [Pingdom 2.0 목표 아키텍처와 도메인 이벤트](architecture/pingdom-2.0-domain-events.md)
- [데이터 마이그레이션, 호환 API, 롤백 정책](architecture/pingdom-2.0-migration-compatibility-rollback.md)
- [목표 아키텍처](architecture/README.md)

## Features

- **Auth**: 회원가입, 로그인, 관리자 로그인, 이메일 인증, 토큰 재발급, 로그아웃, Google OAuth2/OIDC
- **User/Profile**: 내 정보 조회, 비밀번호 변경, 아이디 변경, 회원 탈퇴, 사용자 상태 검증
- **Place**: 좌표 기반 장소 등록, 장소 업로드, 장소 목록/상세 조회, 검색, 삭제, 성장 레벨 계산
- **Bookmark**: 장소 북마크 생성, 삭제, 사용자 북마크 목록 조회
- **Post**: 지도 게시글 목록/상세 조회, 이미지 업로드, 수정, 삭제, 북마크 장소 기준 게시글 조회
- **Engagement**: 좋아요, 좋아요 취소, 좋아요 알림 복귀 처리, 게시글 신고
- **Recommendation**: 장소 추천 조회, 추천 클릭 기록, 노출/전환/피처 로그, 추천 스냅샷과 유사도 스냅샷 재구성
- **Notification**: FCM 토큰 등록, 알림 저장, 이메일 인증 메일 발송
- **Moderation**: 신고 승인/반려, 사용자 제재/해제, 제재 이력 조회, 관리자 게시글/장소/광고 관리
- **Outbox**: 이메일 인증 요청, 지도 이미지 좋아요, S3 객체 삭제 이벤트의 재시도와 보관 기간 정리

## Tech Stack

| 분류 | 기술 |
|---|---|
| Core | Java 21, Spring Boot 3.3.5 |
| Web | Spring MVC, Spring Validation, Spring Security |
| Auth & Security | Spring Security OAuth2 Client, JJWT, BCrypt |
| Persistence | Spring Data JPA, Hibernate Spatial, PostgreSQL, PostGIS, Flyway |
| Geo | JTS, PostGIS geometry/geography support |
| Async & Scheduling | Spring Scheduling, Outbox Worker |
| External API | Google OAuth2/OIDC, Firebase Admin SDK, Postmark |
| Storage | AWS S3 |
| API Docs | SpringDoc OpenAPI |
| Test | Spring Boot Test, JUnit 5, Testcontainers, H2 |
| Build & Deploy | Gradle, Docker, Docker Compose, GitHub Actions |

## Reverse Proxy Header

`X-Forwarded-For`와 `X-Forwarded-Proto`는 직접 연결된 프록시가 `TRUSTED_PROXY_IPS_REGEX`에 일치할 때만 해석합니다. 기본값은 loopback 주소뿐이므로, 운영 로드밸런서 또는 리버스 프록시의 IP 대역을 Tomcat 정규식으로 반드시 설정해야 합니다.

## OpenAPI Contract
## OpenAPI Export

OpenAPI 계약 JSON은 아래 명령으로 export할 수 있습니다.

```bash
./gradlew exportOpenApiSpecs
```

OpenAPI 호환성 검증은 아래 명령으로 실행할 수 있습니다.

```bash
./gradlew verifyOpenApiContract
```

기준 스펙은 `src/test/resources/openapi-baseline` 아래에 저장하며, 의도된 API 변경 시 export 결과로 함께 갱신합니다.
생성 파일은 `build/openapi` 아래에 저장됩니다.

- `openapi.json`
- `app.json`
- `common.json`
- `web.json`

## Architecture

Pingdom Server는 이벤트 기반 모듈러 모놀리스를 기준으로 도메인 책임을 분리합니다. <br>
조회 요구가 복잡한 관리자 화면, 장소 추천 지표, 게시글 목록 같은 영역에는 선택적으로 Query Service를 분리해 CQRS 성격의 구조를 사용합니다.

```text
src/main/java/com/typenull/pingdom
├── identity        # 회원가입, 로그인, OAuth, 토큰, 사용자 상태
├── place           # 장소, 좌표, 북마크, 장소 추천
├── post            # 지도 이미지 게시글, S3 이미지 처리
├── engagement      # 좋아요, 신고, 사용자 상호작용
├── notification    # 이메일, FCM, 알림, Outbox handler
├── moderation      # 관리자 조회, 신고 처리, 사용자 제재, 광고 관리
├── privacy         # 개인정보 처리 이력, 사용자 데이터 내보내기, 탈퇴 데이터 정리
└── shared          # 보안, 설정, 공통 예외, Outbox, 관측성, 외부 저장소 지원
