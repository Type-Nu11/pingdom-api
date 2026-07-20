# 로컬 개발 환경과 dev seed 데이터

## 실행

```bash
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

`dev` 프로필은 `docker-compose-local.yml`의 PostgreSQL, Redis를 자동으로 사용하고 Swagger UI를 `/swagger-ui`에서 활성화한다.

## seed 설정

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `SEED_ADMIN_USERNAME` | `admin` | 관리자 username |
| `SEED_ADMIN_EMAIL` | `admin@local` | 관리자 email |
| `SEED_ADMIN_PASSWORD` | `admin1234!` | 관리자 password |
| `SEED_DEV_DATA_ENABLED` | `true` | 개발용 사용자/장소/게시글 seed 적용 여부 |
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
