# 로컬 개발 환경과 dev seed 데이터

`local`, `dev` 프로필은 Pingdom 2.0 개발 중 로컬에서 바로 실행할 수 있는 기본 설정을 제공합니다.

## 실행

```bash
PINGDOM_DEV_PROFILE_ENABLED=true SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
# 또는
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

Spring Docker Compose 연동이 `docker-compose-local.yml`의 PostgreSQL, Redis를 함께 실행합니다.
별도 `.env`가 없어도 기본 DB/Redis 값으로 실행되며, 필요한 경우 환경 변수로 덮어씁니다.
Swagger UI는 `/swagger-ui`에서 활성화합니다.

## Gemini 첫 상담 안내

첫 상담 안내 API는 기본적으로 비활성화되어 있으며, 키가 없거나 Gemini 호출에 실패하면 기본 안내문을 반환합니다.
로컬에서 실제 호출을 확인할 때만 현재 셸 또는 로컬 비밀 관리 도구에 아래 값을 설정합니다. `.env`, 소스 코드,
테스트 fixture에는 실제 키를 저장하지 않습니다.

```bash
GEMINI_ENABLED=true
GEMINI_API_KEY=발급받은_서버_전용_키
```

운영에서는 `GEMINI_API_KEY`를 배포 서버의 비밀 환경 설정 또는 Secret Manager로 주입하고, Gemini API 전용·서버
출발 IP 제한을 적용합니다. `GEMINI_ENABLED`는 키 교체·장애 대응 시 즉시 `false`로 전환하는 kill switch로 사용합니다.

`dev` 프로필은 Swagger를 공개하므로 `PINGDOM_DEV_PROFILE_ENABLED=true`를 명시해야만 시작됩니다.
운영 배포에서는 `SPRING_PROFILES_ACTIVE`에 `dev`를 포함하거나 이 환경 변수를 설정하지 않습니다.
또한 dev 프로필의 관리자 seed는 기본 비활성화이며, 필요한 개발 환경에서만
`SEED_ADMIN_ENABLED=true`와 `SEED_ADMIN_PASSWORD`를 함께 지정합니다.
동일한 실행 순서를 재사용하려면 아래 보조 스크립트를 사용합니다. 기본 동작은 소스 기준 구성 검증이며,
컨테이너 기동과 애플리케이션 실행은 명시적인 옵션에서만 수행합니다.

```bash
./scripts/bootstrap-local-development.sh --verify
./scripts/bootstrap-local-development.sh --start-dependencies
./scripts/bootstrap-local-development.sh --run
```

전체 시작·중지·복구 기준은 [로컬 개발 환경 부트스트랩 Runbook](operations/development-bootstrap-runbook.md)을 따른다.

## seed 설정

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `SEED_ADMIN_USERNAME` | `admin` | 관리자 username |
| `SEED_ADMIN_EMAIL` | `admin@local` | 관리자 email |
| `SEED_ADMIN_PASSWORD` | `admin1234!` | 관리자 password |
| `SEED_DEV_DATA_ENABLED` | `true` | 개발용 사용자/장소/게시글/북마크 seed 적용 여부 |
| `SEED_DEV_USER_PASSWORD` | `pingdom1234!` | 개발용 일반 계정 공통 password |

## seed 인벤토리

| 구분 | 식별자 |
|---|---|
| 관리자 | `admin` / `admin@local` |
| 관광객 | `tourist01` / `tourist01@local` |
| 관광객 | `tourist02` / `tourist02@local` |
| 사장님 | `merchant01` / `merchant01@local` |
| 장소 | `dev-seed-place-001`, `dev-seed-place-002`, `dev-seed-place-003` |

seed는 중복 실행해도 기존 `username`, `email`, `kakao_place_id`, 사용자-장소 게시글/북마크 조합을 기준으로 재생성하지 않는다.

## 기본 계정

`local`, `dev` 프로필에서는 관리자 계정이 없을 때만 seed 계정을 생성합니다.

| 항목 | 기본값 |
|---|---|
| username | `admin` |
| email | `admin@local` |
| password | `admin1234!` |

변경이 필요하면 아래 환경 변수를 사용합니다.

```bash
SEED_ADMIN_USERNAME=admin
SEED_ADMIN_EMAIL=admin@local
SEED_ADMIN_PASSWORD=admin1234!
SEED_ADMIN_ENABLED=true
```

## 확인

- Swagger UI: `http://localhost:8080/swagger-ui`
- 관리자 로그인: `POST /auth/admin/login`
- DB: `jdbc:postgresql://localhost:5432/pingdom`
- Redis: `localhost:6379`

관리자 seed는 같은 `username` 또는 `email`이 이미 있으면 건너뜁니다.
