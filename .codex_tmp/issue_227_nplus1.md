## 버그 설명
- PR #227 리뷰에서 지적된 후속 결함입니다.
- `PlaceSimilaritySnapshotResyncService`는 기존 snapshot 엔티티를 `existingSnapshotByPair` 맵에 보관한 뒤 배치 저장 시 `entityManager.clear()`를 호출합니다.
- 이 시점 이후 맵에 남아 있던 엔티티들이 detached 상태가 되고, 이후 `saveAll()`에서 `merge()` 경로를 타면서 건별 `SELECT`가 발생할 수 있습니다.
- 결과적으로 snapshot resync 처리량이 커질수록 배치마다 N+1 SELECT가 누적되어 성능이 크게 저하될 위험이 있습니다.
- 관련 위치
  - `src/main/java/com/typenull/pingdom/place/application/service/PlaceSimilaritySnapshotResyncService.java:125`
  - `src/main/java/com/typenull/pingdom/place/application/service/PlaceSimilaritySnapshotResyncService.java:212`
  - `src/main/java/com/typenull/pingdom/place/application/service/PlaceSimilaritySnapshotResyncService.java:215`
- 참고 PR: #227

## 재현 방법
1. 기존 `PlaceSimilaritySnapshot` 데이터가 충분히 존재하는 상태에서 전체 resync를 수행합니다.
2. 첫 배치 저장 후 `entityManager.clear()`가 호출되도록 배치 크기 이상 데이터를 처리합니다.
3. 이후 배치에서 기존 snapshot 엔티티를 다시 `saveAll()`할 때 merge 대상마다 추가 `SELECT`가 발생하는지 SQL 로그로 확인합니다.

## 기대 동작
- resync 배치 저장 중 영속성 컨텍스트 정리 때문에 기존 엔티티가 불필요하게 개별 조회되지 않아야 합니다.
- 대량 snapshot 동기화 시 배치 저장이 예측 가능한 쿼리 수와 성능을 유지해야 합니다.

## 스크린샷
- 없음
