# 로컬 개발 환경

`local` 프로필은 Pingdom 2.0 개발 중 로컬에서 바로 실행할 수 있는 기본 설정을 제공합니다.

## 실행

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

Spring Docker Compose 연동이 `docker-compose-local.yml`의 PostgreSQL, Redis를 함께 실행합니다.
별도 `.env`가 없어도 기본 DB/Redis 값으로 실행되며, 필요한 경우 환경 변수로 덮어씁니다.

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
