# 로컬 개발 환경 부트스트랩 Runbook

이 문서는 #548에서 추가한 반복 가능한 로컬 개발 환경 구성·seed·인벤토리 실행 절차다.
대상은 `local`, `dev` 프로필뿐이며 운영 환경에 seed를 적용하거나 운영 컨테이너를 제어하는 용도가 아니다.

## 목적과 범위

- PostgreSQL/PostGIS와 Redis를 같은 Compose 파일로 준비한다.
- `local` 프로필로 애플리케이션을 시작해 개발용 관리자와 예제 데이터를 seed한다.
- API·테이블·배치 인벤토리와 부트스트랩 계약을 소스 기준으로 검증한다.

스크립트는 Docker, DB, Redis, 외부 API에 연결하지 않는 `--verify`를 기본 동작으로 둔다.
실제 컨테이너 기동은 `--start-dependencies` 또는 `--run`을 명시했을 때만 수행한다.

## 사전 조건과 프로필

- JDK 21과 Docker Compose v2를 준비한다.
- 루트 디렉터리에서 명령을 실행한다.
- 기본 실행 프로필은 `local`이다. `dev`도 같은 seed·Compose 계약을 사용하지만, 보조 스크립트는
  실수로 다른 환경을 선택하지 않도록 `local`로 고정한다.

`application-local.yaml`, `application-dev.yaml`은 Docker Compose와 Swagger UI를 활성화한다.
Compose 파일은 `postgres`와 `redis`의 health check를 포함하므로, 애플리케이션을 실행하기 전에 두
의존성이 준비됐는지 기다릴 수 있다.

## 시작 절차

먼저 소스 기준 계약을 검증한다.

```bash
./scripts/bootstrap-local-development.sh --verify
# 또는
./gradlew verifyDevelopmentBootstrap
```

의존성만 먼저 기동하려면 아래 명령을 사용한다.

```bash
./scripts/bootstrap-local-development.sh --start-dependencies
```

애플리케이션까지 한 번에 실행하려면 아래 명령을 사용한다. 이 명령은 내부적으로
`SPRING_PROFILES_ACTIVE=local ./gradlew bootRun`을 실행한다.

```bash
./scripts/bootstrap-local-development.sh --run
```

## seed 적용과 재실행

`DevAdminSeedConfig`는 `local`, `dev` 프로필에서만 등록된다. 기본값으로 관리자 1명과 관광객·사장님·장소·이미지·북마크 예제 데이터가 생성된다.

| 설정 | 역할 |
| --- | --- |
| `SEED_ADMIN_ENABLED` | 개발용 관리자 생성 여부 |
| `SEED_DEV_DATA_ENABLED` | 예제 사용자·장소·게시글·북마크 생성 여부 |
| `SEED_ADMIN_USERNAME`, `SEED_ADMIN_EMAIL`, `SEED_ADMIN_PASSWORD` | 개발용 관리자 식별자와 비밀번호 |
| `SEED_DEV_USER_PASSWORD` | 예제 일반 계정의 공통 비밀번호 |

개발 데이터 없이 애플리케이션만 확인할 때는 아래처럼 토글을 끈다.

```bash
SEED_ADMIN_ENABLED=false SEED_DEV_DATA_ENABLED=false \
  ./scripts/bootstrap-local-development.sh --run
```

seed는 사용자 username/email, 장소 kakao place ID, 사용자-장소 게시글·북마크 존재 여부를 확인한 뒤 삽입하므로
같은 로컬 DB에서 재실행해도 같은 레코드를 중복 생성하지 않는다.

## 확인 절차

애플리케이션이 시작된 뒤에는 다음 최소 확인을 수행한다.

```bash
curl -fsS http://localhost:8080/actuator/health/liveness
curl -fsS http://localhost:8080/actuator/health/readiness
./gradlew collectDevelopmentBootstrapManifest
./gradlew verifyDevelopmentBootstrap
```

- Swagger UI는 `http://localhost:8080/swagger-ui`에서 확인한다.
- 생성 매니페스트는 `build/inventory/development-bootstrap-manifest.md`에 저장되며 Git에 커밋하지 않는다.
- API 경로·Flyway·배치 실행 지점은 `verifyDevelopmentBootstrap` 내부에서 기존
  `verifyCurrentSystemInventory`를 함께 실행해 확인한다.
- 기본 비밀번호와 JWT secret은 로컬 전용 값이므로 로그, 이슈, 인벤토리 산출물에 기록하지 않는다.

## 중지와 복구

의존성 컨테이너만 중지하려면 다음 명령을 사용한다. 기본 명령은 볼륨을 삭제하지 않으므로 로컬 DB 데이터는 유지된다.

```bash
./scripts/bootstrap-local-development.sh --stop-dependencies
```

Compose 기동, health check, 애플리케이션 시작 중 어느 단계에서 실패했는지는 해당 단계의 출력으로 구분한다.
소스 계약만 다시 점검할 때는 Docker를 재시작하지 말고 `--verify`를 실행한다.

로컬 데이터 자체를 초기화해야 할 때는 팀의 DB 운영 절차를 먼저 확인하고, 필요한 경우에만 Compose volume을 명시적으로 삭제한다.
이 Runbook과 자동화는 운영 DB, Redis, AWS S3, Postmark, FCM에 연결하거나 값을 변경하지 않는다.
