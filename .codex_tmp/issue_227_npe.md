## 버그 설명
- PR #227 리뷰에서 지적된 후속 결함입니다.
- `PlaceRecommendationQueryServiceImpl`의 개인화 후보 적재 과정에서 `seedPlaces` 전체가 `personalCandidates`로 먼저 유입됩니다.
- 이후 `expansionSeeds`만 null 좌표 필터링하고 있어, 위도/경도가 `null`인 장소가 최종 `candidatePool`에 남을 수 있습니다.
- 추천 거리 계산 시 `candidate.place().getLatitude()` / `getLongitude()`가 `double`로 언박싱되면서 `NullPointerException`이 발생할 수 있습니다.
- 관련 위치
  - `src/main/java/com/typenull/pingdom/place/application/service/PlaceRecommendationQueryServiceImpl.java:288`
  - `src/main/java/com/typenull/pingdom/place/application/service/PlaceRecommendationQueryServiceImpl.java:111`
- 참고 PR: #227

## 재현 방법
1. 사용자의 상호작용 장소 목록(`interactedPlaceIds`)에 위도 또는 경도가 `null`인 `MapPlace`를 포함시킵니다.
2. `loadPersonalCandidates()`가 해당 seed를 `personalCandidates`에 추가한 상태에서 추천 API를 호출합니다.
3. `candidatePool` 순회 중 거리 계산(`calculateDistanceMeters`)에서 null 좌표가 언박싱되며 `NullPointerException`이 발생할 수 있습니다.

## 기대 동작
- null 좌표를 가진 장소는 개인화 후보 풀과 거리 계산 대상에서 일관되게 제외되어야 합니다.
- 추천 요청이 데이터 결함 때문에 런타임 예외로 실패하지 않아야 합니다.

## 스크린샷
- 없음
