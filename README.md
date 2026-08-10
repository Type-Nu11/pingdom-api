<img width="7680" height="4320" alt="Frame 123677" src="https://github.com/user-attachments/assets/667fbadc-2b0e-4951-8aa7-e8e3cd9a904c" />

## Overview

Pingdom Server는 Pingdom 사용자 애플리케이션과 관리자 시스템에서 사용하는
REST API와 핵심 비즈니스 로직을 제공하는 백엔드 애플리케이션입니다.

사용자 인증, 지도 기반 장소, 사진 게시글, 장소 추천을 비롯해 사업자 운영,
예약, 혜택, 결제, 알림 및 관리자 기능을 하나의 서비스에서 관리합니다.

## Project Status

현재 **SNAPSHOT 개발 단계**입니다.

핵심 기능과 서비스 정책을 검증하고 있으며, 안정화 이전까지 API, 데이터 구조 및
애플리케이션 동작이 예고 없이 변경될 수 있습니다.

| Item | Status |
|---|---|
| Development | `In Progress` |
| Release | `SNAPSHOT` |
| Stability | `Experimental` |
| Production Ready | `No` |

## Core Capabilities

| Domain | Description |
|---|---|
| Identity | 회원가입, 로그인, JWT, Google OAuth2/OIDC, 계정 및 사용자 상태 관리 |
| Place | 좌표 기반 장소 등록, 조회, 검색, 북마크 및 장소 정보 관리 |
| Post | 장소에 연결된 사진 게시글 등록, 조회, 수정 및 삭제 |
| Engagement | 게시글 좋아요, 신고 및 사용자 상호작용 관리 |
| Recommendation | 위치와 사용자 활동을 기반으로 장소 추천 후보 및 전환 데이터 관리 |
| Merchant | 사업자 인증, 장소 소유권, 사업자 프로필 및 팀 관리 |
| Product | 사업자가 제공하는 예약 가능 상품과 서비스 관리 |
| Reservation | 사용자 예약 생성, 조회, 확정 및 취소 |
| Offer | 혜택, 쿠폰 발급 및 사용 처리 |
| Payment | 결제 내역, 환불 및 정산 정보 관리 |
| Campaign | 프로모션과 캠페인 운영 |
| Verification | 위치 체크인, 방문 증빙, 현장 제보 및 검증 처리 |
| Notification | 이메일과 Firebase Cloud Messaging 기반 알림 처리 |
| Moderation | 신고, 사용자 제재, 콘텐츠 및 사업자 운영 관리 |
| Privacy | 개인정보 처리 이력, 데이터 내보내기 및 탈퇴 데이터 정리 |

## Architecture

Pingdom Server는 도메인 책임을 기준으로 분리한
**Event-Driven Modular Monolith** 구조를 사용합니다.

복잡한 조회 요구사항이 있는 영역에는 선택적으로 CQRS 성격의 Query Service를 사용하고,
이메일, 알림, 외부 저장소 처리와 같은 부수효과는 Outbox 기반 비동기 작업으로 분리합니다.

```text
User App ───────┐
Merchant App ───┼──> REST API
Admin System ───┘        │
                         ▼
              Application / Domain Modules
                         │
            ┌────────────┼────────────┐
            ▼            ▼            ▼
     PostgreSQL       Redis       Outbox Worker
       PostGIS                          │
                              ┌─────────┼─────────┐
                              ▼         ▼         ▼
                            AWS S3     FCM      Postmark
```

### Design Principles

- 도메인 책임을 기준으로 모듈 경계를 분리합니다.
- 핵심 상태 변경은 동기 트랜잭션으로 처리합니다.
- 외부 시스템 호출과 후속 처리는 비동기로 분리합니다.
- Outbox 작업은 재시도와 중복 처리를 고려합니다.
- 복잡한 조회 요구가 있는 영역에만 선택적으로 CQRS를 적용합니다.
- API와 데이터베이스 변경은 계약 및 마이그레이션으로 관리합니다.
- 장애 발생 시 추적과 복구가 가능하도록 운영 정보를 기록합니다.

상세한 설계 원칙은 [Architecture Documentation](docs/architecture/README.md)을 참고합니다.

## Tech Stack

| Category | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.5 |
| Web | Spring MVC, Spring Validation |
| Security | Spring Security, JWT, BCrypt, Google OAuth2/OIDC |
| Persistence | Spring Data JPA, Hibernate Spatial |
| Database | PostgreSQL, PostGIS |
| Cache | Redis |
| Migration | Flyway |
| Async | Spring Scheduling, Outbox Worker |
| Storage | AWS S3 |
| Notification | Firebase Cloud Messaging, Postmark |
| API Documentation | SpringDoc OpenAPI |
| Test | JUnit 5, Spring Boot Test, Testcontainers, H2 |
| Build | Gradle 9.4.1 |
| Infrastructure | Docker, Docker Compose, GitHub Actions, AWS EC2 |

## Getting Started

### Prerequisites

로컬 실행을 위해 다음 도구가 필요합니다.

- Git
- Java 21
- Docker 및 Docker Compose

Gradle은 저장소에 포함된 Gradle Wrapper를 사용하므로 별도로 설치하지 않아도 됩니다.

### Clone

```bash
git clone https://github.com/Type-Nu11/Pingdom_Server.git
cd Pingdom_Server
```

### Run with Local Profile

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

`local` 프로필을 사용하면 Spring Docker Compose 연동을 통해
`docker-compose-local.yml`에 정의된 PostgreSQL과 Redis가 함께 실행됩니다.

### Run with Development Profile

```bash
PINGDOM_DEV_PROFILE_ENABLED=true \
SPRING_PROFILES_ACTIVE=dev \
./gradlew bootRun
```

`dev` 프로필은 개발용 API 문서를 제공하므로
`PINGDOM_DEV_PROFILE_ENABLED=true`가 설정된 경우에만 실행됩니다.

### Bootstrap Script

로컬 개발 환경은 제공되는 스크립트로 검증하고 실행할 수 있습니다.

```bash
./scripts/bootstrap-local-development.sh --verify
./scripts/bootstrap-local-development.sh --start-dependencies
./scripts/bootstrap-local-development.sh --run
```

상세한 실행 방법은 [Local Development Guide](docs/local-development.md)를 참고합니다.

## Configuration

애플리케이션은 환경변수와 Spring Profile을 통해 실행 환경을 구성합니다.

| Category | Environment Variables |
|---|---|
| Profile | `SPRING_PROFILES_ACTIVE` |
| Database | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` |
| Redis | `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` |
| Authentication | `JWT_SECRET`, `JWT_ACCESS_TOKEN_EXPIRATION_SECONDS` |
| Google OAuth | `GOOGLE_OAUTH_CLIENT_ID`, `GOOGLE_OAUTH_CLIENT_SECRET` |
| AWS S3 | `AWS_S3_BUCKET`, `AWS_REGION`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` |
| Email | `POSTMARK_SERVER_TOKEN`, `POSTMARK_FROM_EMAIL` |
| Firebase | `FCM_KEY_PATH` |
| CORS | `CORS_ALLOWED_ORIGINS` |

> 실제 인증정보, API Key, 비밀 값 및 운영 환경 설정은 저장소에 커밋하지 않습니다.

로컬 프로필에는 개발 환경 실행을 위한 기본값이 제공되며,
운영 환경에서는 반드시 별도의 안전한 값을 사용해야 합니다.

## API Documentation

애플리케이션 실행 후 Swagger UI에서 API 문서를 확인할 수 있습니다.

```text
http://localhost:8080/swagger-ui
```

OpenAPI 문서는 목적에 따라 다음 그룹으로 구분됩니다.

| Group | URL |
|---|---|
| App API | `/v3/api-docs/app` |
| Common API | `/v3/api-docs/common` |
| Web/Admin API | `/v3/api-docs/web` |

### Export OpenAPI Specifications

```bash
./gradlew exportOpenApiSpecs
```

생성된 OpenAPI 파일은 `build/openapi`에 저장됩니다.

### Verify API Compatibility

```bash
./gradlew verifyOpenApiContract
```

기준 계약은 `src/test/resources/openapi-baseline`에서 관리합니다.

## Testing

### Run Tests

```bash
./gradlew test
```

### Run a Specific Test

```bash
./gradlew test --tests "{TestClassName}"
```

### Verify Development Bootstrap

```bash
./gradlew verifyDevelopmentBootstrap
```

### Verify System Inventory

```bash
./gradlew verifyCurrentSystemInventory
```

## Repository Structure

```text
.
├── src
│   ├── main
│   │   ├── java/com/typenull/pingdom
│   │   │   ├── identity          # 인증, 사용자 및 사업자 계정
│   │   │   ├── place             # 장소, 좌표 및 북마크
│   │   │   ├── post              # 사진 게시글
│   │   │   ├── engagement        # 좋아요, 신고 및 상호작용
│   │   │   ├── merchant          # 사업자 도메인
│   │   │   ├── product           # 예약 가능 상품
│   │   │   ├── reservation       # 예약 처리
│   │   │   ├── offer             # 혜택과 쿠폰
│   │   │   ├── payment           # 결제, 환불 및 정산
│   │   │   ├── availability      # 이용 가능 상태
│   │   │   ├── campaign          # 캠페인
│   │   │   ├── boost             # 장소 노출 강화
│   │   │   ├── verification      # 방문 및 현장 검증
│   │   │   ├── notification      # 이메일과 FCM 알림
│   │   │   ├── moderation        # 관리자 운영 및 제재
│   │   │   ├── privacy           # 개인정보 처리
│   │   │   └── shared            # 공통 설정과 기술 지원
│   │   └── resources             # 애플리케이션 설정과 DB 마이그레이션
│   └── test                      # 단위 및 통합 테스트
├── docs                          # 아키텍처와 운영 문서
├── scripts                       # 개발 환경 및 검증 스크립트
├── docker                        # 로컬 데이터베이스 초기화
├── docker-compose-local.yml      # 로컬 PostgreSQL 및 Redis
├── Dockerfile                    # 애플리케이션 컨테이너 이미지
├── build.gradle                  # Gradle 빌드 구성
└── README.md
```

## Documentation

| Document | Description |
|---|---|
| [Documentation Index](docs/README.md) | 전체 기술 및 운영 문서 안내 |
| [Architecture](docs/architecture/README.md) | 목표 아키텍처와 모듈 설계 원칙 |
| [Local Development](docs/local-development.md) | 로컬 실행 환경과 개발 데이터 |
| [API Path Policy](docs/api-path-policy.md) | API 경로와 버전 정책 |
| [Error and Retry Policy](docs/api-error-code-retry-policy.md) | 오류 응답과 재시도 정책 |
| [Observability](docs/observability.md) | 로그, 메트릭 및 운영 관측성 |
| [Database Migration](docs/database-migration.md) | Flyway 마이그레이션 정책 |
| [Database Backup and Restore](docs/database-backup-restore.md) | 데이터베이스 백업과 복구 절차 |

## Release and Compatibility

현재 버전은 안정화 이전의 SNAPSHOT 버전입니다.

- API와 데이터 구조는 변경될 수 있습니다.
- 이전 SNAPSHOT 버전과의 호환성을 보장하지 않습니다.
- 데이터베이스 변경은 Flyway 마이그레이션을 통해 관리합니다.
- 의도된 API 변경은 OpenAPI 기준 계약과 함께 갱신합니다.
- 정식 버전 출시 이후 별도의 버전 및 호환성 정책을 적용할 예정입니다.

## License

이 프로젝트는 [MIT License](LICENSE)를 따릅니다.

---

<div align="center">

Part of **Pingdom**

</div>
