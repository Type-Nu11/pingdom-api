# #1057 북마크·좋아요·장소별 이미지 조회 인덱스 검증

## 검증 목적

운영 PostgreSQL에서 북마크 장소 조회, 사용자별 이미지 좋아요 조회, 장소별 이미지 조회의
실행계획을 확인하고 추가 인덱스가 필요한지 판단한다. 검증 결과에 근거하지 않은 인덱스는
쓰기 비용과 저장 공간만 늘릴 수 있으므로 추가하지 않는다.

## 검증 환경

- 검증일: 2026-08-10
- DB: PostgreSQL 16 / PostGIS 3.4
- Flyway 기준: V91 (`add admin notification idempotency`)
- 트랜잭션: `READ ONLY`
- statement timeout: 5초
- 실제 사용자·장소 식별자를 문서에 남기지 않기 위해 존재하지 않는 ID로 실행계획을 확인했다.

통계 기반 추정 행 수는 세 테이블 모두 0이었지만 정확한 `COUNT(*)` 결과는 다음과 같았다.
운영 판단에는 정확한 행 수를 사용한다.

| 테이블 | 정확한 행 수 |
| --- | ---: |
| `map_bookmark` | 13 |
| `map_image_like` | 24 |
| `map_image` | 71 |

## 기존 인덱스

| 테이블 | 관련 인덱스 | 열 |
| --- | --- | --- |
| `map_bookmark` | `uk_map_bookmark_user_place` | `(user_id, place_id)` |
| `map_image_like` | `uk_map_image_like_user_image` | `(user_id, map_image_id)` |
| `map_image` | `idx_map_image_visibility_created` | `(visibility_status, created_time DESC, map_image_id DESC)` |
| `map_image` | `uk_map_image_user_place` | `(user_id, map_place_id)` |

각 테이블의 primary key 인덱스는 표에서 생략했다.

## 실행계획 결과

| 조회 | 주요 실행계획 | Buffer | 실행 시간 |
| --- | --- | ---: | ---: |
| 장소별 북마크 수 | `Seq Scan` 후 `Aggregate`; 13행 필터 확인 | shared hit 1 | 0.019ms |
| 사용자별 최신 좋아요 | 기존 unique index의 `Bitmap Index Scan` 후 25kB quicksort | shared hit 7 | 0.071ms |
| 장소별 최신 이미지 | 기존 unique index의 `Index Scan` 후 25kB quicksort | shared hit 4 | 0.033ms |

`map_bookmark.place_id` 조회는 현재 sequential scan을 사용하지만 전체 행이 13건이고 실행 시간이
0.019ms이므로 별도 역방향 인덱스의 유지 비용을 정당화하지 못한다. 좋아요와 장소별 이미지 조회도
기존 인덱스를 사용하며 전체 실행 시간이 0.1ms 미만이다.

## 결정

- 추가 Flyway migration을 만들지 않는다.
- 현재 데이터 규모와 실행 시간에서는 추가 인덱스의 실효성을 확인할 수 없다.
- migration을 적용하지 않았으므로 적용 후 실행계획과 rollback 대상도 없다.
- 향후 재검증 결과에서 실제 병목이 확인될 때 새로운 migration으로 필요한 인덱스만 추가한다.

## 재검증 조건

다음 중 하나가 발생하면 같은 쿼리를 실제 운영 분포의 비식별 파라미터로 다시 검증한다.

- 대상 테이블 행 수가 현재보다 10배 이상 증가한다.
- 조회 지연 또는 DB CPU·buffer read 증가가 운영 관측에서 확인된다.
- 해당 조회의 조건이나 정렬 순서가 변경된다.
- `Seq Scan`, sort 또는 heap fetch가 실제 응답 지연의 주요 비용으로 확인된다.

재검증은 읽기 전용 트랜잭션과 짧은 `statement_timeout`을 사용하고, 결과에는 실제 사용자·장소
식별자를 기록하지 않는다. 후보 인덱스는 적용 전후 동일한 파라미터와 cache 조건에서 비교하며,
개선이 확인되지 않으면 migration에 포함하지 않는다.
