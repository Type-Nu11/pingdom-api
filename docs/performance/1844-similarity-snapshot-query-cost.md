# 유사도 snapshot 조회 비용 검증 (#1844)

## 비교 범위

`PlaceRecommendationSimilarityService.buildContext`와 대상 장소의 모든 쌍 scoring을 비교한다. 운영 코드 기준은 `92afc490c04849583874ca097ad03fd43b250fe7`이다. 구 경로는 `fe5f8d378e8b7ce7b4dddbc52d5792f4ca838d1b`의 eager 로딩 순서를 테스트 안에 재현한다. 현재 schema·JPA 저장소·점수 함수는 두 경로에서 공유하므로 다른 이슈의 변경이 비교에 섞이지 않는다.

기존 경로는 북마크 사용자, 좋아요 사용자, 추천 추세, 저장 유사도를 차례로 조회한다. 현재 경로는 저장 유사도를 먼저 읽고 누락 쌍의 양 끝 장소만 원시 반응을 조회한다. 실제 Spring Data JPA 쿼리가 격리된 PostgreSQL 컨테이너에 실행되며 저장소 mock 데이터는 사용하지 않는다.

## 재현

JDK 21과 Docker를 준비하고 다음 클래스만 실행한다. 전체 테스트 스위트는 필요하지 않다.

```sh
PINGDOM_SIMILARITY_BENCHMARK=true ./gradlew --no-daemon --max-workers=2 --console=plain \
  test --tests com.typenull.pingdom.place.PlaceRecommendationSimilarityServiceTest \
  postgresIntegrationTest --tests com.typenull.pingdom.place.PlaceRecommendationSimilarityPostgreSqlIntegrationTest
```

측정 옵션을 생략하면 DB 계약 7건만 검증하고 반복 성능 측정은 실행하지 않는다. 원시 측정 결과는 `build/reports/similarity-1844.json`, JUnit 결과는 `build/test-results/postgresIntegrationTest`에 생성된다.

## 조건과 계측

- PostGIS `postgis/postgis:16-3.4`, 현재 Flyway 스키마·인덱스, 장소 8개·28쌍.
- 장소마다 북마크 사용자 1천/1만 명. 사용자 범위를 4가지 offset으로 겹치게 구성한다.
- 장소마다 사진 2개에 같은 사용자들이 좋아요를 누른다. 실제 좋아요 반환 행은 북마크의 두 배이며 서비스의 사용자 집합은 중복을 제거한다.
- full은 28쌍 모두 저장, partial은 한 쌍만 누락, miss는 저장 쌍 0개다. 저장 점수는 동일 fixture의 실제 원시 반응으로 계산한다.
- 저장 시각은 2010-01-01이다. 기존 계약대로 오래된 저장 점수도 우선 사용하며 별도 만료 판단을 추가하지 않는다. 추세의 최신 게시 시각은 null로 고정해 실행 시계 차이를 제거한다.
- 전역 북마크 사용자 수는 fixture에서 한 번 집계해 양쪽에 같은 값으로 전달한다. 기존 1시간 사용자 수 캐시의 비용은 비교 대상에서 제외한다.
- Hibernate `StatementInspector`가 실제 SELECT 수를 센다. 저장소 반환 직후 실제 projection 행 수와 원시 조회·전송·JPA projection 생성 시간을 기록한다.
- 각 경로 warmup 3회 뒤 12회씩 구·신 순서를 번갈아 측정한다. 요청 전 GC 대기는 요청 지연에 포함하지 않는다.
- `ThreadMXBean`으로 요청 스레드의 할당량을 측정한다. 별도 스레드의 1ms 표본과 요청 직후의 heap 사용량으로 근사 peak를 기록하며, 1ms보다 짧은 순간 최대치까지 보장하지 않는다.
- 지연은 DB 트랜잭션 시작부터 context 생성·28쌍 scoring·정렬·트랜잭션 종료까지다. HTTP 전체 추천 API의 p95나 운영 SLO 측정 결과가 아니다.

## 검증 결과

2026-11-12에 JDK `21.0.13+9-LTS`, 테스트 JVM 최대 heap 2GiB에서 실행했다. 단위 7건과 PostgreSQL 7건 모두 실패·오류·건너뜀 0건이다. PostgreSQL 클래스는 68.489초, Gradle 전체는 1분 36초였다. 각 coverage/규모에서 모든 28쌍의 구성 점수·합계·snapshot 표시와 affinity 정렬이 정확히 같았다. 재동기화는 저장 점수를 무시하고 중복 좋아요를 집합으로 처리했다.

| 장소별 사용자 | coverage | 실제 원시 행 수 구→신 | SELECT 구→신 | 요청 p50 ms 구→신 | 요청 p95 ms 구→신 | 평균 할당 MiB 구→신 | sampled peak heap MiB 구→신 | 평균 원시 조회 ms 구→신 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1,000 | full | 24,000 → 0 | 4 → 2 | 50.21 → 4.83 | 119.74 → 6.99 | 72.37 → 0.12 | 184.35 → 112.35 | 33.40 → 0.00 |
| 1,000 | partial | 24,000 → 6,000 | 4 → 4 | 45.50 → 16.50 | 59.39 → 32.07 | 72.37 → 18.20 | 184.44 → 129.44 | 23.40 → 9.57 |
| 1,000 | miss | 24,000 → 24,000 | 4 → 4 | 47.09 → 46.16 | 73.40 → 151.61 | 72.34 → 72.34 | 184.35 → 184.37 | 27.67 → 30.13 |
| 10,000 | full | 240,000 → 0 | 4 → 2 | 405.39 → 3.33 | 617.46 → 9.88 | 720.86 → 0.11 | 514.37 → 112.44 | 205.20 → 0.00 |
| 10,000 | partial | 240,000 → 60,000 | 4 → 4 | 455.23 → 124.17 | 824.60 → 423.54 | 720.85 → 180.37 | 580.48 → 269.47 | 245.15 → 70.91 |
| 10,000 | miss | 240,000 → 240,000 | 4 → 4 | 429.05 → 431.62 | 497.64 → 604.64 | 720.82 → 720.82 | 569.40 → 488.40 | 205.06 → 205.27 |

1만 사용자 fixture에서 full hit는 원시 24만 행을 전혀 읽지 않았다. 한 쌍 누락은 두 장소만 읽어 원시 행 수와 평균 할당량이 약 75% 감소했다. 전체 miss는 같은 24만 행을 읽어 할당량 감소가 없었으며, 이번 표본의 p95는 오히려 증가했다. 전체 miss의 sampled heap 차이도 GC·표본 시점에 따른 값이므로 구조적 메모리 개선으로 해석하지 않는다.

최종 판정: snapshot 적중·부분 누락에서 불필요한 로딩 제거와 모든 경로의 점수 계약 유지가 실제 DB에서 확인됐다. 전체 miss의 원시 반응 비용을 제한했다고 주장하지 않는다. 이번 결과만으로 캐시·스키마·점수 정책을 추가 변경할 근거는 없으며 운영 적중률과 비용을 기존 계측으로 관측한다.

## 운영 관측과 적용 한계

기존 계측인 `pingdom.recommendation.similarity.pairs`의 hit/miss, `pingdom.recommendation.similarity.reaction.rows`, `pingdom.recommendation.similarity.reaction.query`와 JVM 메모리 지표를 함께 관측한다. 저장 쌍이 많아도 누락 쌍이 모든 장소에 걸치면 전체 장소의 반응이 필요하다. 전체 누락에서 원시 행 수 상한을 줄이는 변경은 이번 구현에 포함하지 않는다.

실험은 고정된 로컬 fixture의 해당 서비스 단계 비교다. 운영 적중률·반응 분포·동시 요청·DB 지연을 대변하지 않으며, 12개 표본의 p95는 최대 표본값과 같다. 운영 지연 예산이 따로 정해지고 미달이 관측되면 공동 사용자 DB 집계나 별도 상한을 검토한다.

운영 동작은 이번 후속 변경에서 바뀌지 않는다. 측정 테스트와 문서를 되돌려도 서비스 계약과 schema는 유지된다.
