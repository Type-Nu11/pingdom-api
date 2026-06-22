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

`V2`의 `CREATE EXTENSION postgis`를 실행하려면 DB 계정에 extension 생성 권한이 있어야
한다. 운영 애플리케이션 계정에 권한을 부여하지 않는 환경에서는 인프라 관리자가 배포
전에 PostGIS extension을 생성해야 한다.
