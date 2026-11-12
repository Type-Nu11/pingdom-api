# #1846 트렌드 과거 이력 조회 비용 검증

2026-11-12 기준으로 첫 구현 PR #1915의 **요청 내 count/목록 상태 복원 재사용을 유지**한다. 같은 데이터에서 이전 count/list 2 SQL과 현재 1 SQL의 결과가 같고, 100만 과거 이력에서 보고된 event 처리 행이 약 절반으로 줄었다. 과거 이력 전체를 읽는 차수는 남아 있으므로 snapshot 또는 요청 간 순위 재사용은 별도 운영 비용 조건이 확인될 때 재검토한다.

## 비교 대상과 조건

- 현재 코드: `92afc490c04849583874ca097ad03fd43b250fe7`의 `PlaceTrendQueryRepository.findTrendPage`.
- 이전 SQL: 첫 구현 직전 `81737090f002986fa5545c59efe87d2d15aa0c53`의 `countTrends`, `findTrends`. 테스트 리소스는 두 `@Query` 원문이며 목록 끝의 `LIMIT :limit OFFSET :offset`만 기존 Spring Data Pageable과 동일하게 명시했다.
- 이전 서버를 따로 실행한 비교가 아니다. 동일한 현재 runtime·Flyway schema·DB fixture·JDBC mapper에서 이전 SQL 2개와 현재 저장소 `@Query`에서 직접 읽은 SQL 1개를 실행한다. 양쪽 모두 읽기 전용 `REPEATABLE_READ` 트랜잭션으로 감싼다.
- JDK 21.0.13, Docker 29.3.1, Testcontainers `postgis/postgis:16-3.4`, PostgreSQL 16.4, `work_mem=4MB`. 16GB 공유 개발 호스트에서 다른 무거운 Gradle/DB 검증을 중단한 상태로 한 번 실행했다.
- 고정 기간은 2026-08-20 12:00 이상 ~ 2026-08-27 12:00 이하, 공개·운영 장소 20개, 최근 ADDED 1,000건, 현재 북마크 1,000건이다. 사용자·장소 조합이 같은 과거 1,000개 stream을 짝수 번 토글해 초기/최종 상태 모두 REMOVED로 고정하며 과거 행 수만 1만/10만/100만으로 늘린다. 장소별 순증 50, total 20이 모든 규모에서 같다.
- 규모마다 `ANALYZE`, 흐름별 2회 warmup 후 각 7회 측정한다. 이전/현재의 실행 순서를 번갈아 준다. 측정에는 JDBC 결과 매핑과 트랜잭션 비용이 포함되며 fixture 생성, EXPLAIN, HTTP·인증·tracking 조회는 제외한다. 서버의 장기 p95, 부하 한계 또는 end-to-end 지연시간은 아니다.

## 실제 결과

p50/p95는 7개 표본의 nearest-rank이며 p95는 표본 최댓값이다. 단위는 ms이다. event 행은 실행계획의 해당 relation 노드에서 `(Actual Rows + Rows Removed by Filter) × Actual Loops`를 합한 값이다. executor의 조기 종료·반복 및 반올림 영향을 받을 수 있어 테이블 전체 행 수와 일치하는 물리 I/O 건수라는 의미는 아니다. 버퍼는 각 SQL root의 shared hit+read 합계이며 이전 값은 count/list 두 계획의 합이다.

| 과거 이력 | 이전 p50 / p95 | 현재 p50 / p95 | 보고된 event 처리 행 | shared hit+read 블록 |
| ---: | ---: | ---: | ---: | ---: |
| 10,000 | 17.04 / 17.46 | 9.12 / 9.69 | 44,000 → 22,000 | 12,866 → 6,468 |
| 100,000 | 83.27 / 245.16 | 24.80 / 134.57 | 401,802 → 200,901 | 13,786 → 6,928 |
| 1,000,000 | 442.85 / 694.52 | 220.65 / 248.96 | 4,000,002 → 2,000,001 | 83,618 → 41,844 |

100만 이력의 p50 감소는 50.2%이며 p95는 694.52→248.96ms였다. 각 흐름의 단 한 번 `EXPLAIN ANALYZE`에서 나온 Execution Time 합계는 아래와 같다. 이 값은 위 JDBC 표본과 별도 계측이며 instrumentation·캐시 상태에 영향을 받는다.

| 과거 이력 | 이전 count+list Execution Time | 현재 Execution Time | 임시 read/write 블록 |
| ---: | ---: | ---: | ---: |
| 10,000 | 34.729 | 9.742 | 양쪽 0 / 0 |
| 100,000 | 80.961 | 35.671 | 양쪽 0 / 0 |
| 1,000,000 | 615.298 | 337.495 | 양쪽 0 / 0 |

9개 원본 계획에서 Disk sort는 관측되지 않았다. 100만 이력의 root shared read 블록은 이전 합계 37,679, 현재 18,788이었다. warm cache만 보장하는 실험이나 통제된 cold-cache 실험은 아니며, 10만 규모 표본은 21.93~245.16ms까지 변동했다. 유리한 수치를 고르기 위한 재측정은 하지 않았다.

## 계약 검증

대상 PostgreSQL 클래스 **7건**과 서비스 단위 **4건**, 합계 **11건**이 실패·오류·skip 없이 통과했다. 전체 suite는 실행하지 않았다.

- 기존 PostgreSQL 5건에서 실제 저장소 projection의 모든 응답 필드·순서·total을 이전 SQL 결과와 비교했다. 반복 토글, 숨김 제외, `< periodStart` 초기 상태, `<= periodEnd` 최종 상태, 같은 시각의 마지막 id, 병합 origin의 `BOOL_OR`, 네 정렬 기준, 요청자 북마크, 최신 ACTIVE 이미지와 직접 이미지 우선 규칙을 보존한다.
- 빈 데이터와 범위 밖 페이지 모두 이전 count를 유지하며, 현재의 metadata 전용 행은 항목에서 제외된다. 서비스 단위 4건은 page=0/limit=0 보정, MAX_LIMIT, totalPages 최솟값 1, 순위·hasNext·tracking 시작 보정을 확인한다.
- PostgreSQL 새 1건은 다른 연결에서 북마크·event의 병합 대상 변경과 원본 숨김을 한 트랜잭션으로 커밋한다. 기존 `REPEATABLE_READ` 안의 페이지/total/북마크는 이전 snapshot을 유지하고, 읽기 종료 후 새 조회에는 병합 대상이 나타난다. 실제 전체 병합 서비스의 동시 부하 시험은 아니다.
- opt-in 성능 1건은 세 이력 규모에서 첫 페이지·범위 밖 페이지의 모든 필드와 total 동등성, 최근 이력 1,000건, 장소별 순증 50을 assertion으로 확인한다. 지연시간을 pass/fail 기준으로 삼지 않는다.

## 실행과 증거

일반 관련 회귀(성능 사례는 환경 flag가 없어 skip):

```sh
./gradlew test --tests '*PlaceTrendQueryServiceTest' \
  postgresIntegrationTest --tests '*PlaceTrendQueryRepositoryPostgreSqlIntegrationTest' \
  --no-daemon --max-workers=2 --console=plain
```

이번 실제 실행과 동일한 opt-in 범위(JDK 21 및 Docker 필요):

```sh
TREND_QUERY_BENCHMARK=true TREND_QUERY_EVIDENCE_DIR=/tmp/pingdom-trend-1846 \
  ./gradlew test --tests '*PlaceTrendQueryServiceTest' \
  postgresIntegrationTest --tests '*PlaceTrendQueryRepositoryPostgreSqlIntegrationTest' \
  --no-daemon --max-workers=2 --console=plain
```

Gradle 캐시가 해당 테스트를 up-to-date로 판단하면 지정 클래스만 다시 실행하도록 `--rerun-tasks`를 추가한다. 측정은 명시적인 환경 flag가 있을 때만 실행된다. 측정 결과 경로를 함께 지정해야 하며, raw plan/표본 JSON은 Git에 넣지 않는다. 이번 기록은 `completion-run/evidence-1846/`의 `measurement.json`, 규모별 `*-plan.json` 9개, `baseline-comparison.json`, `gradle-targeted.log`, 두 JUnit XML과 체크섬으로 보존한다. baseline SQL resource의 SHA-256은 `f5adef1f4844dd4a30cd843ce4f2d3220af38a625eeacecfb733261a47ebff30`이다.

## 판단과 남은 제약

요청 하나의 중복 상태 복원을 제거한 효과와 API 결과 보존은 확인했다. schema·snapshot·cache를 추가하지 않고 현재 구현을 유지하며, 별도 데이터 마이그레이션이나 롤백 절차는 필요하지 않다. 추가한 파일은 테스트·검증 자료이므로 되돌릴 때 운영 데이터에는 영향이 없다.

현재 p50도 과거 1만의 9.12ms에서 100만의 220.65ms로 늘었다. `DISTINCT ON` 상태 복원이 여전히 O(history)이며 이 개선이 이력 증가 비용의 상한을 만드는 것은 아니다. 이번 fixture는 장소 20개와 stream 수를 고정했으므로 장소·사용자 증가, 오래 활성 상태인 stream의 비율, 병합 빈도, 동시 요청 증가에 대한 용량 보장은 하지 않는다. 운영 지연 예산이 주어지지 않아 SLO 충족도 판정하지 않는다.

운영 예상 이력·장소·stream 수와 peak concurrency에서 합의된 p95/DB CPU·read I/O 예산을 초과하거나, 정렬/임시 파일 spill 또는 connection 대기가 확인되면 기준 상태 snapshot 또는 요청 간 계산 순위 재사용을 별도 설계한다. 그 단계에서는 rolling 7일 경계, 허용 freshness, 토글·병합/복원, 원자 갱신·재구축·롤백 정책을 함께 확정해야 한다. 단순 최근 7일 WHERE로 과거 복원을 잘라내는 변경은 이번 결과가 허용하지 않는다.
