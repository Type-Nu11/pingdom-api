<img width="7680" height="4320" alt="Frame 123677" src="https://github.com/user-attachments/assets/667fbadc-2b0e-4951-8aa7-e8e3cd9a904c" />

## Overview

이 저장소는 Pingdom 프로젝트의 **백엔드 API와 핵심 비즈니스 로직**을 관리합니다.

Pingdom App과 Admin에서 필요한 인증, 장소, 게시글, 추천, 사업자 운영, 예약, 혜택,
결제, 알림 및 관리 기능을 제공합니다. 핵심 데이터의 일관성을 보장하고 외부 서비스
연동과 비동기 후속 처리를 안정적으로 수행하는 것이 이 저장소의 주요 역할입니다.

이 저장소는 Pingdom 서버의 공개 소스 저장소입니다. 운영 환경의 인증정보와 배포 설정은
포함하지 않으며, 로컬 개발과 API 계약 검증에 필요한 구성만 제공합니다.

## Project Status

현재 **GA(General Availability)** 단계입니다.

안정화된 서비스를 제공하며, API와 데이터 구조의 변경은 Release와 변경 이력을 통해
관리합니다.

| Item | Status |
|---|---|
| Development | `Generally Available` |
| Release | `GA` |
| Stability | `Stable` |

## Repository Role

| Item | Description |
|---|---|
| Type | `Service` |
| Responsibility | Pingdom의 REST API, 핵심 비즈니스 규칙 및 데이터 일관성 관리 |
| Primary Output | 실행 가능한 Spring Boot 애플리케이션, REST API, OpenAPI 계약 및 DB 마이그레이션 |
| Target | Pingdom App, Pingdom Admin, 사업자 기능 및 외부 연동 시스템 |

## Scope

### Included

- 사용자 인증, 계정, 권한 및 개인정보 처리
- 지도 기반 장소, 사진 게시글, 북마크, 좋아요 및 장소 추천
- 사업자 인증, 장소 소유권, 상품, 예약, 혜택, 쿠폰 및 결제
- 사용자 알림, 신고, 제재, 현장 검증 및 관리자 운영 기능
- PostgreSQL/PostGIS 데이터 저장, Redis 기반 상태 관리 및 Outbox 후속 처리

### Not Included

- 사용자 및 사업자 화면의 UI/UX 구현
- 관리자 웹 화면의 UI/UX 구현
- 클라우드 리소스 프로비저닝과 네트워크 인프라 구성

## Key Capabilities

- **Identity and Access**: 이메일 계정, JWT, Google OAuth2/OIDC 및 사용자 상태를 관리합니다.
- **Place and Content**: 좌표 기반 장소, 사진 게시글, 북마크와 사용자 상호작용을 제공합니다.
- **Discovery and Recommendation**: 위치와 사용자 행동을 기반으로 장소 탐색과 추천을 지원합니다.
- **Merchant Operations**: 사업자 인증, 장소 소유권, 팀과 운영 정보를 관리합니다.
- **Reservation and Commerce**: 상품, 예약, 혜택, 쿠폰, 결제, 환불 및 정산 흐름을 처리합니다.
- **Operations and Reliability**: 신고, 제재, 알림, 개인정보 처리와 재시도 가능한 후속 작업을 관리합니다.

## Technology and Tools

| Category | Technology |
|---|---|
| Primary | Java 21, PostgreSQL 16, PostGIS, Redis 7.2 |
| Framework | Spring Boot 3.3.5, Spring MVC, Spring Security, Spring Data JPA, Hibernate Spatial, Flyway |
| Build | Gradle 9.4.1, Docker, Docker Compose |
| Quality | JUnit 5, Spring Boot Test, Testcontainers, H2, SpringDoc OpenAPI, OpenAPI Diff |
| Delivery | GitHub Actions, Docker Image, AWS EC2 |

## Getting Started

이 저장소를 실행하려면 Java와 Docker 기반의 로컬 개발 환경이 필요합니다.

### Requirements

- Git
- Java 21
- Docker 및 Docker Compose
- Google OAuth, AWS S3, Postmark, Firebase를 실제로 연동할 경우 해당 서비스의 자격 증명

### Setup

```bash
git clone https://github.com/Type-Nu11/pingdom-api.git
cd pingdom-api
./scripts/bootstrap-local-development.sh --verify
```

### Usage

```bash
./scripts/bootstrap-local-development.sh --run
```

이 명령은 `docker-compose-local.yml`의 PostgreSQL과 Redis를 시작한 뒤 `local` 프로필로
애플리케이션을 실행합니다. 애플리케이션 실행 후
`http://localhost:8080/swagger-ui`에서 API 문서를 확인할 수 있습니다.

## Configuration

애플리케이션 설정과 로컬 개발 기준은 다음 파일과 문서에서 관리합니다.

```text
src/main/resources/application.yaml
src/main/resources/application-local.yaml
src/main/resources/application-dev.yaml
docs/local-development.md
```

데이터베이스, Redis, JWT, Google OAuth, AWS S3, Postmark, Firebase 및 CORS 설정은
환경변수로 주입합니다. 실제 인증정보, API Key, 비밀 값 및 운영 환경 정보는
저장소에 커밋하지 않습니다.

브라우저에서 S3 presigned URL 업로드를 사용하는 배포는 [S3 CORS 운영 문서](docs/operations/s3-cors.md)에
따라 버킷 CORS를 별도로 적용해야 합니다.

## Verification

저장소 변경사항은 테스트, API 계약, 로컬 개발 환경 및 시스템 인벤토리 기준으로 검증합니다.

```bash
./gradlew test
./gradlew verifyOpenApiContract
./gradlew verifyDevelopmentBootstrap
./gradlew verifyCurrentSystemInventory
```

| Verification | Purpose |
|---|---|
| `./gradlew test` | 단위 및 통합 테스트 실행 |
| `./gradlew verifyOpenApiContract` | 기준 OpenAPI와 현재 API 계약의 호환성 검증 |
| `./gradlew verifyDevelopmentBootstrap` | 로컬 프로필, 의존성 및 개발 환경 구성 검증 |
| `./gradlew verifyCurrentSystemInventory` | API, 스키마 및 스케줄러 인벤토리 검증 |

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
│   │   │   ├── availability      # 예약 및 이용 가능 상태
│   │   │   ├── offer             # 혜택과 쿠폰
│   │   │   ├── payment           # 결제, 환불 및 정산
│   │   │   ├── campaign          # 캠페인
│   │   │   ├── boost             # 장소 노출 강화
│   │   │   ├── verification      # 방문 및 현장 검증
│   │   │   ├── notification      # 이메일과 FCM 알림
│   │   │   ├── moderation        # 관리자 운영 및 제재
│   │   │   ├── privacy           # 개인정보 처리
│   │   │   └── shared            # 공통 설정과 기술 지원
│   │   └── resources             # 설정과 데이터베이스 마이그레이션
│   └── test                      # 단위 및 통합 테스트
├── docs                          # 아키텍처와 운영 문서
├── scripts                       # 개발 환경 및 검증 스크립트
├── docker                        # 로컬 데이터베이스 초기화
├── docker-compose-local.yml      # 로컬 PostgreSQL 및 Redis
├── Dockerfile                    # 애플리케이션 컨테이너 이미지
├── build.gradle                  # Gradle 빌드 구성
└── README.md
```

주요 도메인은 독립된 책임을 가지며, 공통 설정과 기술 지원은 `shared`에서 관리합니다.
상세한 모듈 책임과 의존 방향은 아키텍처 문서를 기준으로 합니다.

## Related Repositories

| Repository | Relationship |
|---|---|
| [pingdom-app](https://github.com/Type-Nu11/pingdom-app) | Pingdom 사용자 및 사업자 애플리케이션 |
| [pingdom-admin](https://github.com/Type-Nu11/pingdom-admin) | 서비스 운영을 위한 관리자 웹 애플리케이션 |
| [pingdom-infra](https://github.com/Type-Nu11/pingdom-infra) | 클라우드 인프라와 배포 환경 구성 |
| [pingdom-loadbalancer](https://github.com/Type-Nu11/pingdom-loadbalancer) | 서비스 트래픽 진입점과 로드밸런싱 구성 |

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

현재 버전은 GA(General Availability) 단계입니다.

- 변경사항은 저장소의 Release 또는 변경 이력을 기준으로 확인합니다.
- 데이터베이스 변경은 Flyway 마이그레이션으로 관리합니다.
- 호환성에 영향을 주는 변경사항은 Release와 API 계약을 통해 안내합니다.

## License

이 프로젝트의 사용 및 배포 조건은 [MIT License](LICENSE)를 따릅니다.

---

<div align="center">

Part of **Pingdom**

</div>
