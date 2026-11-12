# 전역 클릭·노출 SUM 조회 비용 검증 (#1843)

2026-11-12, `develop`의 `92afc490c04849583874ca097ad03fd43b250fe7`에 병합된 단일 SUM 조회를 실제 PostgreSQL에서 확인했다. 이번 변경은 테스트와 검증 기록이며 운영 쿼리·트랜잭션·스키마·캐시를 추가로 변경하지 않는다.

## 비교 범위와 재현

- 기준 `fc8834592765a149c425361dd75279a5829028e5`의 `resolveTotalClickCount`/`resolveTotalExposureCount`는 snapshot 전체에서 각 컬럼을 별도 SUM했다. 현재도 남아 있는 두 repository 메서드를 호출해 이 흐름을 재현하고, 현재 `sumGlobalCounts` projection과 비교했다.
- 두 경로 모두 같은 read-only 트랜잭션에 넣고 같은 DB·데이터·pool을 사용했다. 각 조회의 결과가 정확히 같은지, Hibernate가 실행한 SELECT가 각각 2개/1개인지 모든 표본에서 확인했다.
- fixture는 snapshot 1만/10만/100만 행이다. 동일 분포의 비음수 카운터와 고정 `updated_at`을 SQL로 적재하고 `VACUUM (ANALYZE)`한 후 읽기만 실행했다. 집계 대상 테이블만 채웠으며 이 스키마의 `place_id`에는 FK가 없다. 추천 후보 조회나 전체 HTTP 요청을 재현한 실험은 아니다.
- 동시성은 1/8이고, worker별 각 전략 3회 warmup 후 12회씩 측정했다. 같은 worker에서 구·신 순서를 번갈아 실행했다. 전략별 표본은 각각 12/96개다. 다른 Gradle·DB 성능 검증과 겹치지 않도록 실행했다.
- Darwin arm64 호스트, Temurin 21.0.13, 테스트 JVM 최대 heap 2GiB, Docker Desktop CPU 10개·메모리 8,217,059,328B를 사용했다. DB는 x86_64 컨테이너의 PostgreSQL 16.4, 이미지 `postgis/postgis:16-3.4`, digest `sha256:44126d872ac91993766c341e369c539e8196614321765d36a6f1bab0419a5fa5`다.

직접 실행 명령은 다음과 같다. JDK 21과 Docker daemon이 필요하다.

```sh
PINGDOM_GLOBAL_TOTALS_BENCHMARK=true ./gradlew --no-daemon --max-workers=2 --console=plain \
  test \
  --tests com.typenull.pingdom.place.application.service.recommendation.query.PlaceRecommendationAggregateLoaderTest \
  --tests com.typenull.pingdom.place.application.service.recommendation.query.PlaceRecommendationQueryServiceImplTest.loadsGlobalCountsOnceForARecommendation \
  --tests com.typenull.pingdom.place.application.service.recommendation.query.PlaceRecommendationQueryServiceImplTest.skipsGlobalCountsWhenThereAreNoCandidates \
  postgresIntegrationTest \
  --tests com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationSnapshotTotalsPostgreSqlIntegrationTest
```

환경변수 없이 실행하면 1천 행·동시성 1·전략별 1개 표본으로 실제 합계와 SQL 수의 회귀만 확인한다. 반복 성능 측정은 명시적으로 켜야 한다. 원시 수치와 실행계획은 `build/reports/global-totals-1843.json`에 생성된다. 일반 CI의 `test`와 `postgresSmokeTest`는 이 PostgreSQL 클래스의 `postgres-integration` 태그를 선택하지 않는다.

## 실제 조회 구간 비용

지연은 트랜잭션·pool 접근·JPA SUM 결과 변환을 포함한 집계 조회 구간의 ms다. HTTP 전체 추천 응답 p95가 아니다. 할당량은 현재 요청 스레드의 평균 B이며 `ThreadMXBean`으로 측정했다. SQL 수는 구 2개/신 1개로 모든 표본에서 고정됐다.

| snapshot 행 수 | 동시성 | 구 p50 ms | 신 p50 ms | 구 표본 p95 ms | 신 표본 p95 ms | 구/신 평균 할당 B |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 10,000 | 1 | 1.862 | 1.224 | 3.057 | 2.259 | 20,743 / 19,925 |
| 10,000 | 8 | 7.314 | 4.936 | 21.222 | 15.358 | 20,944 / 20,403 |
| 100,000 | 1 | 13.148 | 7.857 | 39.925 | 30.689 | 20,560 / 19,768 |
| 100,000 | 8 | 19.015 | 12.059 | 26.843 | 18.940 | 20,605 / 19,690 |
| 1,000,000 | 1 | 90.969 | 48.570 | 127.028 | 52.611 | 20,560 / 19,640 |
| 1,000,000 | 8 | 288.074 | 164.794 | 471.257 | 274.929 | 20,572 / 19,648 |

p50는 정렬한 짝수 표본의 위쪽 중앙값이다. p95는 `ceil(표본 수 × 0.95)`번째 값이며, 동시성 1의 12개 표본에서는 최대값이다. 작은 로컬 표본이고 x86_64 컨테이너이므로 이 숫자를 운영 지연이나 용량 보장으로 쓰지 않는다. 지연·할당량 자체에는 성공/실패 hard assertion을 두지 않았다.

같은 SQL에 `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)`을 각 1회 적용한 DB 결과는 다음과 같다. 구 수치는 두 별도 SUM 실행을 합한 값이며 planning time은 제외했다. 모든 조회에서 shared read는 0이었다.

| snapshot 행 수 | 구 DB 실행 ms | 신 DB 실행 ms | 구 shared hit blocks | 신 shared hit blocks |
| ---: | ---: | ---: | ---: | ---: |
| 10,000 | 2.178 | 1.043 | 268 | 134 |
| 100,000 | 20.456 | 17.677 | 2,668 | 1,334 |
| 1,000,000 | 121.001 | 70.095 | 26,668 | 13,334 |

1만/10만은 각 SUM의 Seq Scan, 100만은 각 SUM의 병렬 Seq Scan이었다. 구 경로는 각각 전체 행을 읽는 plan 두 개, 신 경로는 두 컬럼을 함께 읽는 plan 한 개다. 따라서 table scan 반복과 논리 buffer 접근은 줄었지만, 총 snapshot 행 수 P에 대한 O(P) scan은 남는다. 단일 실행계획의 시간은 반복 표본의 p95와 별개의 관측이다.

## 정합성·회귀 결과

직접 선택 실행한 단위 10건과 PostgreSQL 5건, 총 15건이 통과했다. 실패·오류·건너뜀은 0건이고 전체 Gradle 실행은 1분 6초였다.

- PostgreSQL 기존 3건: 빈 snapshot의 null, 실제 0의 유지, 32bit를 넘는 Long 합계 매핑.
- PostgreSQL 추가 2건: committed snapshot 변경이 다음 조회에 즉시 반영됨, 각 규모/동시성에서 구·신 합계의 정확한 동등성과 SELECT 2개/1개 유지.
- aggregate loader 8건: 양쪽/한쪽 null과 absent projection의 기존 원천 fallback, 실제 0 보존, 분모 0의 유한 점수, 쿼리 실패 전파, 같은 합계·후보·시각에서 실제 scoring의 점수·순위 동등성.
- query service 직접 2건: 추천 요청당 전역 집계 1회, 후보가 없을 때 집계 0회.

점수 회귀는 3개 후보와 일반/0/분모 0 경계의 네 가지 전역 합계 조합에서 동일 시각을 고정해 비교했다. DB 비교와 점수 비교는 서로 분리된 검증이며 전체 추천 HTTP 부하 시험으로 확대 해석하지 않는다.

## 유지 결정과 재검토 조건

이번 범위에서는 단일 SUM을 유지한다. 측정한 모든 규모·동시성에서 2회 scan과 2개 SQL을 1회로 줄이고 결과·순위는 그대로 보존했다. 새 캐시나 전역 집계 행은 도입하지 않아 **추가 캐시 지연은 0, 합계 및 점수 허용 오차도 0**이다. 기존 snapshot 자체가 원천 데이터를 반영하는 주기까지 바꾼다는 뜻은 아니다. 원천 fallback과 기존 snapshot 갱신 경로도 유지한다.

운영 대표 행 수·실제 요청률·집계 구간에 배정한 SLO 예산은 현재 확인 자료에 없다. 따라서 이 실험만으로 운영 비용 예산을 충족했다고 판정하지 않는다. 아래 조건에서는 실제 운영 지표와 허용 stale 시간을 먼저 확정하고 TTL 공통 캐시 또는 전역 집계 행을 별도 설계한다.

- 운영 snapshot 수 또는 동시성이 이번 최대 100만 행·8요청 범위를 넘어서는 경우.
- 실제 집계 조회의 p95 또는 누적 DB 시간이 정해진 요청·DB 예산을 초과하는 경우.
- 쓰기·전체/부분 재동기화 빈도와 캐시 지연 허용 요구가 구체화되는 경우.

현재 결과로 운영 구조를 더 바꾸거나 측정되지 않은 TTL을 선택하지 않는다. 이번 테스트·문서 변경의 롤백은 해당 변경만 제거하면 되며 운영 데이터·스키마 이관은 없다.
