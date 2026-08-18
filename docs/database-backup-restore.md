# Database Backup and Restore

이 문서는 운영 DB migration 또는 배포 전에 PostgreSQL 백업을 만들고, 장애 시 복구하는 절차를
정리한다. 기준 환경은 EC2에서 `compose.yaml`을 사용하는 Docker Compose 배포이다.

애플리케이션 rollback으로 충분한 경우와 DB 복구가 필요한 경우의 판단은
[데이터 마이그레이션, 호환 API, 롤백 정책](architecture/pingdom-2.0-migration-compatibility-rollback.md)을
따른다. 이 문서의 `dropdb`와 복구 명령은 결정된 복구 절차를 실행하는 용도이며, 일반적인
배포 실패의 기본 대응이 아니다.

## 원칙

- DB schema 변경이 포함된 배포 전에는 항상 백업을 먼저 만든다.
- 백업 파일이 생성된 것만으로 완료로 보지 않고, 목록 조회 또는 임시 복구로 읽기 가능성을 확인한다.
- 복구가 필요한 상황에서는 애플리케이션을 먼저 중지해 추가 쓰기를 막는다.
- 백업 파일은 배포가 안정화될 때까지 EC2 외부 저장소에도 보관하는 것을 권장한다.
- 자동 백업은 보관 주기, 암호화, 접근 권한, 복구 리허설 기준이 정해진 뒤 별도 작업으로 도입한다.

## 배포 전 백업

EC2의 배포 디렉터리에서 실행한다.

```bash
cd ~/Pingdom_Backend
set -a
source .env
source "$HOME/.pingdom-deploy.env"
set +a

BACKUP_DIR="$HOME/pingdom-backups"
BACKUP_FILE="$BACKUP_DIR/pingdom-$(date +%Y%m%d-%H%M%S).dump"

mkdir -p "$BACKUP_DIR"
docker compose exec -T postgres pg_dump \
  -U "$POSTGRES_USER" \
  -d "$POSTGRES_DB" \
  --format=custom \
  --no-owner \
  --no-privileges \
  > "$BACKUP_FILE"

ls -lh "$BACKUP_FILE"
```

## 백업 파일 확인

백업 파일이 PostgreSQL custom archive로 읽히는지 확인한다.

```bash
docker compose exec -T postgres pg_restore --list < "$BACKUP_FILE" | head
```

가능하면 운영 DB와 분리된 임시 DB에 복구 리허설을 수행한다.

```bash
RESTORE_CHECK_DB="${POSTGRES_DB}_restore_check"

docker compose exec -T postgres dropdb -U "$POSTGRES_USER" --if-exists "$RESTORE_CHECK_DB"
docker compose exec -T postgres createdb -U "$POSTGRES_USER" "$RESTORE_CHECK_DB"
docker compose exec -T postgres psql -U "$POSTGRES_USER" -d "$RESTORE_CHECK_DB" -c \
  'CREATE EXTENSION IF NOT EXISTS postgis; CREATE EXTENSION IF NOT EXISTS pg_trgm;'
docker compose exec -T postgres pg_restore \
  -U "$POSTGRES_USER" \
  -d "$RESTORE_CHECK_DB" \
  --clean \
  --if-exists \
  --no-owner \
  --no-privileges \
  < "$BACKUP_FILE"
docker compose exec -T postgres dropdb -U "$POSTGRES_USER" "$RESTORE_CHECK_DB"
```

## 장애 시 복구

복구는 데이터 손실 가능성이 있으므로 장애 원인, 마지막 정상 백업, 배포 시점을 먼저 확인한다.
복구 결정을 내린 뒤 다음 순서로 진행한다.

```bash
cd ~/Pingdom_Backend
set -a
source .env
source "$HOME/.pingdom-deploy.env"
set +a

BACKUP_FILE="$HOME/pingdom-backups/pingdom-YYYYMMDD-HHMMSS.dump"

docker compose stop app
docker compose exec -T postgres dropdb -U "$POSTGRES_USER" --if-exists "$POSTGRES_DB"
docker compose exec -T postgres createdb -U "$POSTGRES_USER" "$POSTGRES_DB"
docker compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c \
  'CREATE EXTENSION IF NOT EXISTS postgis; CREATE EXTENSION IF NOT EXISTS pg_trgm;'
docker compose exec -T postgres pg_restore \
  -U "$POSTGRES_USER" \
  -d "$POSTGRES_DB" \
  --clean \
  --if-exists \
  --no-owner \
  --no-privileges \
  < "$BACKUP_FILE"
docker compose up -d app
```

복구 후 확인한다.

```bash
docker compose ps
docker compose logs --tail=200 app
docker compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c \
  'SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;'
```

## 주의 사항

- `docker/postgres/initdb` 스크립트는 새 volume 최초 생성 시에만 실행된다.
- 기존 운영 volume에는 extension 생성, migration, 데이터 정리를 명시적으로 수행해야 한다.
- `dropdb`는 현재 DB를 삭제하므로 복구 대상과 백업 파일을 다시 확인한 뒤 실행한다.
- 복구 후 같은 migration이 다시 실패하면 백업을 반복 적용하지 말고 실패 원인을 먼저 제거한다.
