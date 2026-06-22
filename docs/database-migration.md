# Database Migration

DB 스키마 변경은 `src/main/resources/db/migration`의 Flyway migration으로 관리한다.
Hibernate는 스키마를 변경하지 않고 애플리케이션 시작 시 매핑 일치 여부만 검증한다.

## 기존 DB 최초 전환

기존 테이블이 있는 환경은 최초 전환 배포에만 다음 환경 변수를 설정한다.

```text
FLYWAY_BASELINE_ON_MIGRATE=true
```

Flyway는 기존 스키마를 version `1`로 baseline 처리하고 `V2` 이후 migration을 실행한다.
전환이 완료되면 환경 변수를 제거하거나 `false`로 되돌린다. 신규 빈 DB에는 이 설정이
필요하지 않으며 `V1`부터 모든 migration이 실행된다.

## 배포 전 점검

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

## 마이그레이션 통합 테스트

기존 애플리케이션 테스트는 빠른 실행을 위해 H2와 Hibernate `create-drop`을 유지한다.
Flyway SQL은 `FlywayMigrationIntegrationTest`가 PostGIS Testcontainers 환경에서 별도로
실행하고, migration version과 핵심 컬럼 및 제약 조건을 검증한다. 해당 테스트를
실행하려면 로컬 Docker daemon이 필요하다.
