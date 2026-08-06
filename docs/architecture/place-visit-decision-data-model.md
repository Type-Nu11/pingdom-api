# 장소 상세 방문 결정 데이터 모델

## 범위

장소 상세 방문 결정 화면은 별도 집계 테이블을 만들지 않고 기존 장소 데이터를 조합한다.
이 화면은 장소를 노출할지 여부가 아니라 관광객이 지금 방문할지 판단하는 데 필요한 최신 상태를 제공한다.

## 데이터 원천

| 화면 데이터 | 원천 모델/테이블 | 호환 규칙 |
|---|---|---|
| 장소명·주소·좌표·카테고리 | `MapPlace` / `map_place` | 기존 장소도 그대로 조회한다. |
| 탐색 노출·영업 상태 | `MapPlace` / `map_place` | `discovery_status=VISIBLE`이고 영구 폐업이 아닌 장소만 관광객 API 대상이다. |
| 현재 영업 여부 | `PlaceRegularOperatingHour`, `PlaceOperatingException`, `map_place_operating_notice` | 저장된 일정과 예외를 평가하며, 임시 휴업은 상태로 표시한다. |
| 정보 출처·검증 상태 | `MapPlace`, `PlaceInformationVerificationSummary` | 기존 장소는 `LEGACY`·`UNVERIFIED` 기본값을 유지한다. |
| Merchant 설명·예약 링크 | `MerchantPlaceInformation` | 정보가 없으면 선택적 값으로 응답한다. |
| 진행 중 이벤트 | `PlaceEvent` / `place_event` | 공개 상태이며 현재 시각이 기간 안인 이벤트만 사용한다. |
| 예약 가능 시간 | `PlaceAvailability` / `place_availability` | 활성 상태·미래 종료·잔여 수량 조건을 적용한다. |
| 관광객 혜택 | `TouristOffer` / `tourist_offer` | 공개 상태·유효 기간·재고 조건을 적용한다. |

## Migration 판단

이번 API는 기존 영속 데이터를 읽어 조합하므로 새로운 상태 저장이나 enum이 필요하지 않다.
따라서 `V88` migration을 추가하지 않는다. 기존 `V1~V87` 순서와 스키마를 유지하며, 새 API가 기존 장소 데이터에서 안전하게 동작하는지를 호환성 테스트로 검증한다.

새로운 방문자 검증 신호나 Merchant 입력을 저장하는 기능이 추가될 때는 별도 이슈에서 해당 입력 모델과 상태 전이를 먼저 정의한다.
