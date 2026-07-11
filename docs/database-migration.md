# Database Migration

DB 스키마 변경은 `src/main/resources/db/migration`의 Flyway migration으로 관리한다.
Hibernate는 스키마를 변경하지 않고 애플리케이션 시작 시 매핑 일치 여부만 검증한다.

모듈 리팩터링에 migration이 포함되면 [리팩터링 적용·복구
Runbook](refactoring-rollout-runbook.md)에서 변경 유형을 먼저 분류한 뒤 이 절차를 수행한다.

운영 DB에 migration을 적용하기 전에는 반드시 [DB 백업/복구 절차](database-backup-restore.md)를
먼저 완료한다.

데이터 backfill 또는 공개 API 변경이 migration과 함께 진행되면 단계 분리, 기존 앱·API
호환성, rollback 판단은 [데이터 마이그레이션, 호환 API, 롤백 정책](architecture/pingdom-2.0-migration-compatibility-rollback.md)을
먼저 확인한다. 이 문서는 Flyway 작성·적용 절차의 기준이며, 적용된 migration을 되돌리는
down migration을 제공하지 않는다.

Outbox와 notification delivery의 상태·시도 횟수 column은 각각 V5, V20 migration에서
도입됐다. 이 모델의 재시도 책임과 API 오류 코드의 경계는
[API 오류 코드 및 재시도 정책](api-error-code-retry-policy.md)을 따른다.

## 운영 정책

- 운영 스키마 변경은 Flyway SQL migration으로만 반영한다.
- 이미 운영에 적용된 migration 파일은 수정하지 않는다. 후속 변경은 새 version 파일로 추가한다.
- Hibernate `ddl-auto`는 `validate`를 유지한다. 운영에서 Hibernate가 schema를 생성하거나 변경하지 않는다.
- Flyway `validate-on-migrate`는 `true`를 유지한다. 검증 실패를 우회하기 위해 끄지 않는다.
- `flyway_schema_history`는 운영 DB migration 이력의 기준이므로 삭제하거나 수동 수정하지 않는다.
- migration 실패 후 `flyway repair`는 실패 원인과 실제 DB 상태를 확인한 뒤, 적용된 schema 변경이 없거나
  명시적으로 정리된 경우에만 사용한다.

## 기존 DB 최초 전환

기존 테이블이 있지만 `flyway_schema_history`가 없는 환경은 Flyway가 기본으로 기존
스키마를 version `1`로 baseline 처리한다.

```text
FLYWAY_BASELINE_ON_MIGRATE=true
```

이 값은 애플리케이션 기본값이므로 일반적으로 별도 설정이 필요 없다. Flyway는 기존
스키마를 version `1`로 baseline 처리하고 `V2` 이후 migration을 실행한다. 신규 빈 DB에는
baseline이 적용되지 않으며 `V1`부터 모든 migration이 실행된다.

스키마가 비어 있지 않은데 baseline 처리를 명시적으로 막아야 하는 검증 환경에서는 다음과
같이 끌 수 있다.

```text
FLYWAY_BASELINE_ON_MIGRATE=false
```

운영에서 이 값을 `false`로 변경하면 기존 schema만 있고 Flyway 이력이 없는 DB는 부팅 중
실패한다. #312 유형의 장애를 피하려면 최초 전환 대상 DB에서는 `true`를 유지한다.

## 배포 전 점검

1. [DB 백업/복구 절차](database-backup-restore.md)에 따라 배포 전 백업 파일을 생성한다.
2. 백업 파일 목록 조회 또는 임시 복구로 백업이 읽히는지 확인한다.
3. 운영 DB에 필요한 extension이 준비되어 있는지 확인한다.
4. migration별 사전 데이터 조건을 확인한다.
5. 배포 후 `docker compose ps`, 애플리케이션 로그, `flyway_schema_history`를 확인한다.

`V4`는 null이 아닌 `map_place.kakao_place_id` 중복이 있으면 실패한다. 배포 전에 다음
쿼리 결과가 없는지 확인하고, 중복 데이터의 정본을 결정한 뒤 정리해야 한다.

```sql
SELECT kakao_place_id, COUNT(*)
FROM map_place
WHERE kakao_place_id IS NOT NULL
GROUP BY kakao_place_id
HAVING COUNT(*) > 1;
```

`V2`는 PostGIS extension을 직접 생성하지 않고 설치 여부만 검증한다. 로컬 환경은
`docker/postgres/initdb/01_enable_postgis.sql`이 extension을 생성한다. 운영 환경에서는
인프라 관리자 또는 DBA가 최초 Flyway 실행 전에 PostGIS extension을 생성해야 한다.
설치되지 않은 경우 `V2`는 명시적인 오류와 함께 중단된다.

`V8`은 장소명/주소 부분일치 검색용 trigram index를 사용하므로 `pg_trgm` extension이
필요하다. PostGIS와 동일하게 migration에서 extension을 직접 생성하지 않고 설치 여부만
검증한다. 로컬 신규 DB는 `docker/postgres/initdb/01_enable_postgis.sql`에서 생성되며,
기존 로컬 볼륨이나 운영 DB는 Flyway 실행 전에 다음 작업이 선행되어야 한다.

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;
```

`V28`은 관광객 탐색용 영문명과 요약을 nullable column으로 추가하고, 다중 관광 카테고리를
별도 테이블로 확장한다. 기존 `category`는 자유 문자열 계약을 유지하며 기존 장소를 임의로
관광 카테고리에 backfill하지 않는다. 따라서 적용 후 기존 행의 신규 column이 `NULL`이고
`map_place_tourist_category`와 `map_place_tourist_guard`에 자동 생성된 행이 없는지 확인한다.
신규 앱은 영문명·요약·관광 카테고리 중 하나라도 저장할 때 내부 guard row를 함께 관리한다.
guard와 관광 카테고리 FK는 `ON DELETE NO ACTION`으로 두므로 구버전 앱 롤백 후 신규 정보를
모르는 삭제·병합은 데이터를 연쇄 삭제하지 않고 안전하게 실패한다. 신규 앱은 JPA collection
정리 후 장소를 삭제하므로 정상 삭제·병합·복구 흐름은 유지된다.

`V29`는 영문명 검색용 trigram index를 `CONCURRENTLY` 생성한다. Flyway PostgreSQL parser가
이 migration을 non-transactional statement로 실행하므로 `map_place` 쓰기를 장시간 차단하지
않는다. 실패한 concurrent build가 남긴 invalid index는 migration 시작 시 제거해 재실행할 수
있게 한다.

`V29` 실패는 일반 transactional migration과 다르게 index artifact가 남을 수 있으므로 다음
순서로 복구한다.

1. `flyway_schema_history`에서 실패 version이 `29`인지 확인한다.
2. 아래 쿼리로 index 존재 여부와 `indisvalid`, `indisready`를 확인한다.
3. V29가 영문명 index 외의 데이터를 변경하지 않았음을 확인한 뒤 실패 history를 `repair`한다.
4. migration을 재실행한다. V29가 기존 valid/invalid index를 concurrent drop한 뒤 다시 생성한다.
5. `indisvalid = true`, `indisready = true`를 확인한 후 배포를 계속한다.

```sql
SELECT c.relname, i.indisvalid, i.indisready
FROM pg_class c
JOIN pg_index i ON i.indexrelid = c.oid
WHERE c.relname = 'idx_map_place_english_name_trgm';
```

`V30`은 기존 `address`를 호환용 대표 주소로 유지하면서 `road_address`, `jibun_address`,
`postal_code`, `geocoding_source`를 추가한다. 기존 주소 문자열은 임의로 파싱하지 않고 신규
주소 필드는 `NULL`, 출처는 `LEGACY`로 backfill한다. 신규 애플리케이션은 도로명 주소, 지번
주소, 기존 대표 주소 순서로 대표 주소를 결정한다. 출처 값과 null 불가 제약은 `NOT VALID`로
추가한 뒤 검증하고 `NOT NULL`을 설정해 장시간의 강한 table lock을 피한다.

`V31`은 정규화 주소 검색용 trigram index를 `CONCURRENTLY` 생성한다. 실패 시 V29와 동일하게
invalid index 여부를 확인한 뒤 실패 history를 `repair`하고 재실행한다. 확인 대상 index는
`idx_map_place_road_address_trgm`, `idx_map_place_jibun_address_trgm`이다. migration은
`executeInTransaction=false`로 실행하며 PostgreSQL transactional advisory lock도 비활성화한다.

## validate 실패 대응

Flyway validate 실패는 migration 파일과 DB 이력의 불일치로 봐야 한다.

1. 애플리케이션 재시작을 반복하지 않고 실패 로그를 보존한다.
2. 운영 DB의 `flyway_schema_history`에서 실패 version, checksum, success 값을 확인한다.
3. 이미 적용된 migration 파일이 수정되었는지 Git 이력과 비교한다.
4. checksum mismatch라면 운영에 적용된 파일을 원복하고, 필요한 변경은 새 migration으로 작성한다.
5. failed row가 있고 실제 schema 변경이 없음을 확인한 경우에만 `flyway repair`를 검토한다.
   단, V29와 V31은 위 전용 절차로 invalid index만 남았음을 확인한 경우 repair 후 재실행할 수 있다.
6. schema가 일부 변경된 상태라면 수동 보정 대신 백업 복구를 우선 검토한다.

확인 쿼리:

```sql
SELECT installed_rank, version, description, type, script, checksum, installed_on, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

## 장애 시 복구 기준

- migration 실행 전 실패: 설정, DB readiness, extension, 권한 문제를 수정한 뒤 재배포한다.
- migration 도중 실패: 애플리케이션을 중지하고 `flyway_schema_history`와 실제 schema 변경 여부를
  확인한다.
- 데이터 변경 또는 DDL 일부 적용이 의심되는 실패: 배포 전 백업으로 복구하는 것을 기본 원칙으로 한다.
- 복구 후에는 같은 이미지 재기동 전에 실패 원인을 제거하고, 필요한 경우 새 migration을 작성한다.

복구 명령과 절차는 [DB 백업/복구 절차](database-backup-restore.md)를 따른다.

## 마이그레이션 통합 테스트

기존 애플리케이션 테스트는 빠른 실행을 위해 H2와 Hibernate `create-drop`을 유지한다.
Flyway SQL은 `FlywayMigrationIntegrationTest`가 PostGIS Testcontainers 환경에서 별도로
실행하고, migration version과 핵심 컬럼 및 제약 조건을 검증한다. 해당 테스트를
실행하려면 로컬 Docker daemon이 필요하다.

검증 범위:

- 신규 빈 PostGIS DB에 `V1`부터 최신 migration까지 적용되는지 확인한다.
- 기존 `V1` schema가 있지만 `flyway_schema_history`가 없는 DB에서 baseline 후 incremental
  migration이 적용되는지 확인한다.
- migration 관련 파일이 변경된 PR은 `Migration Verification` GitHub Actions workflow에서
  동일 테스트를 실행한다.
- `release` 브랜치 push 배포 경로는 기존 build workflow의 migration gate로 한 번 더 검증한다.

로컬 실행:

```bash
./gradlew --no-daemon --build-cache --configuration-cache test --tests com.typenull.pingdom.migration.FlywayMigrationIntegrationTest
```

## Compose readiness 확인

배포용 `compose.yaml`과 로컬용 `docker-compose-local.yml`은 DB readiness를 healthcheck로 확인한다.
배포용 `compose.yaml`은 Postgres와 Redis host port를 공개하지 않고 Compose 내부 네트워크에서만 접근한다.
운영 `.env`에는 Redis 인증에 사용할 `REDIS_PASSWORD`를 반드시 설정한다.
배포 후 다음 명령으로 상태를 확인한다.

```bash
docker compose ps
docker compose logs postgres redis app
```

`postgres`가 unhealthy라면 애플리케이션 재시작보다 DB 로그, `.env`, volume 초기화 상태,
extension 생성 여부를 먼저 확인한다.
`redis`가 unhealthy라면 `REDIS_PASSWORD` 누락 여부와 Redis 인증 설정을 먼저 확인한다.
