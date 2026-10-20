# 단일 예약 확인 계약 (#1792)

## 적용 범위

예약은 장소·시간·인원을 선택하는 단일 흐름이다. 예약 유형, 상품 선택/연결, 예약 가격 및 신규 결제 기능을 제공하지 않는다. AI는 예약 초안을 준비하고, 앱은 서버에서 확인 조건을 조회해 사용자의 최종 확인 후 `POST /reservations`를 호출한다.

주변 예약 가능 장소 검색, 슬롯 등록·수정·조회, 예약 생성·목록·상세 및 점주·관리자 예약 조회에서 `productType`, `productId`, `productName`을 제거한다. `/merchant-owner/reservable-products` 및 활성화·비활성화 API는 종료한다. 장소 메뉴 및 Verified Boost 상품 기능은 이 변경의 대상이 아니다.

슬롯 등록·수정 본문에 `productId` 또는 `productType`이 있거나, 예약 조건 설정 본문에 `unitAmountMinor`, `additionalAmountMinor`, `currency`가 있으면 값이 null이어도 `400 INVALID_REQUEST_BODY`로 거절한다. 구버전 클라이언트는 해당 필드를 완전히 제거해야 한다. 그 외 알 수 없는 필드의 기존 처리 방식과 전역 JSON 설정은 유지한다.

예약 조건/견적/확인 응답에는 단가, 추가 비용, 총액, 통화, 통화 소수 자릿수, 결제 필요 여부, 취소 수수료, 환불액, 상품 버전이 없다. 누락 값을 0이나 무료로 표시하지 않는다. 기존 결제 내역 API의 금액은 실제 과거 거래 이력이므로 유지한다.

## 시간대·취소 조건 등록

활성 점주는 본인 슬롯에 `PUT /merchant-owner/availabilities/{availabilityId}/reservation-terms`로 다음 조건을 설정한다.

```json
{
  "timezone": "Asia/Seoul",
  "cancellable": true,
  "cancellationCutoffMinutes": 60
}
```

`cancellable=true`이면 시작 전 취소 기한(분)은 0 이상이어야 한다. false이면 기한은 null이다. 취소 기한에 도달한 시점부터 취소할 수 없다. `reservationTerms=null`은 미설정이며 취소 불가를 뜻하지 않는다. 서버는 시간대·취소 정책이 없는 슬롯의 견적을 `QUOTE_TERMS_UNAVAILABLE`로 거절하며 임의의 정책을 생성하지 않는다.

점주 슬롯 목록의 `reservationTerms`는 저장된 편집값이며, `conditionsVersion`은 재고와 별개로 조건 변경을 추적한다. 기존 `LocalDateTime`은 주입 Clock의 시간대 기준으로 해석하고, 지정 IANA 시간대는 같은 예약 순간의 표시 기준으로 사용한다.

## 견적 준비와 최종 확인

1. `GET /places/{placeId}/availabilities/{availabilityId}/quote?quantity=2`로 조건을 조회한다. 견적은 예약 생성·재고 점유를 수행하지 않는다.
2. 서버는 사용자 소유의 `confirmationToken`, `confirmation`, `remainingCapacity`를 반환한다. 확인 조건은 아래 형태다.
3. 앱은 장소·시간·인원·취소 조건을 표시한다. 최종 확인 직전에 다시 조회하고 조건이 바뀌었으면 사용자의 재확인을 받는다.
4. 사용자가 확인한 토큰을 예약 생성 본문에 넣는다. 사용자 → 견적 → 슬롯 잠금 순서로 토큰·기간·인원·조건·재고를 검증한다.
5. 예약 생성·상세 응답의 `confirmation`에는 실제 수락한 조건을 저장한다. 견적의 최대 유효기간은 5분이며 슬롯 시작 전까지다.

```json
{
  "placeId": 70095,
  "placeName": "예시 장소",
  "availabilityId": 5,
  "startsAt": "2026-10-24T23:00:00+09:00",
  "endsAt": "2026-10-25T00:00:00+09:00",
  "quantity": 3,
  "timezone": "Asia/Seoul",
  "cancellable": true,
  "cancellationDeadline": "2026-10-24T22:00:00+09:00",
  "conditionsVersion": 1,
  "expiresAt": "2026-10-14T13:05:00Z"
}
```

예시는 운영 기본값이 아니다. 앱은 ISO 날짜 문자열을 사용자에게 읽기 쉬운 날짜·시간으로 표시한다.

토큰 생략 요청은 기존 터치 예약 호환 경로로 유지하며 응답의 `confirmation`은 null이다. 견적 확인 조건 보장은 적용되지 않는다. 예약 생성 성공(201, PENDING)은 상점주 승인 대기이며 예약 확정을 의미하지 않는다.

## 상점주 심사와 관리자 기록 조회

예약 신청은 기존과 같이 `PENDING`으로 저장하며 해당 장소의 현재 상점주 목록에 나타난다. 별도 관리자 배정이나 신규 신청 푸시는 제공하지 않는다.

| 대상 | API | 동작 |
| --- | --- | --- |
| 상점주 | `GET /merchant-owner/reservations` | 현재 소유 장소의 예약 목록 |
| 상점주 | `GET /merchant-owner/reservations/{reservationId}` | 예약 상세와 처리 이력 |
| 상점주 | `POST /merchant-owner/reservations/{reservationId}/confirm` | 대기 예약 승인 |
| 상점주 | `POST /merchant-owner/reservations/{reservationId}/reject` | 대기 예약 반려 |
| 상점주 | `POST /merchant-owner/reservations/{reservationId}/cancel` | 기존 정책에 따른 확정 예약 취소 |
| 관리자 | `GET /admin/reservations` | 전체 예약 기록 검색 |
| 관리자 | `GET /admin/reservations/{reservationId}` | 예약 상세와 처리 이력 |

상점주 목록은 선택 필터 `status`, `placeId`, `reservationFrom`, `reservationTo`와 기존 `page`, `limit`를 받는다. 기간은 예약 시작 시각을 기준으로 시작 이상·종료 미만이며 양쪽 경계가 있으면 시작이 종료보다 앞서야 한다. 필터를 생략하면 현재 소유 장소의 모든 상태를 반환한다. `placeId`를 전달해도 소유 범위를 넓힐 수 없다.

상점주 상세 응답은 `reservation`(기존 `ReservationResponse`), `placeId`, `placeName`, `statusHistory`로 구성한다. 이력 항목은 `id`, `status`, `changedBy`, `reason`, `changedAt`이며 생성·심사·취소 순서로 조회한다. 목록·승인·반려·취소의 기존 예약 응답 필드는 유지한다.

승인은 본문 없이 요청할 수 있다. 사유를 보내면 `{ "reason": "확인 완료" }` 형식이며 빈 문자열은 허용하지 않는다. 반려는 같은 형식의 본문과 공백이 아닌 사유가 필수다. 사유는 최대 500자이고 저장 시 앞뒤 공백을 제거한다.

조회·심사·취소는 현재 장소 소유자이면서 활성 프로필·승인된 본인/사업자 인증을 갖추고 탈퇴·정지되지 않은 상점주에게만 허용한다. 직원·매니저의 예약 심사 권한은 이 API에 연결하지 않는다. 소유권 이전 후에는 현재 점주가 기존 대기 예약을 심사하며 이전 점주는 처리할 수 없다. 관리자 검색의 `merchantOwnerUserId`와 응답의 점주 정보도 현재 소유자 기준이며 소유 매핑이 없으면 점주 정보는 null이다. 과거 실제 심사자는 `reviewedBy` 및 상태 이력의 `changedBy`로 별도 보존한다.

신청 시 재고를 이미 선점하므로 승인은 재고를 추가 차감하지 않는다. 반려는 선점 수량을 한 번만 반환한다. 예약·현재 소유권 매핑을 잠금 확인하고 상태 변경·재고 반환·이력 저장은 하나의 트랜잭션으로 처리한다. 반복 심사와 승인·반려 경합은 현재 예약 상태 검사로 거절한다. 신규 상점주 처리는 `reservation_status_history`에 기록하고 기존 관리자 감사 로그는 보존한다.

관리자 승인·반려 API는 제거한다. 관리자 화면은 조회 전용으로 전환하고 상점주 화면은 승인·반려와 대기 목록을 연결해야 한다. 상점주 처리 화면을 사용할 수 있는 시점에 서버 전환을 맞춰 심사 공백을 방지한다. 기존 DB 구조와 과거 예약·심사 기록은 유지하며 신규 마이그레이션은 필요하지 않다.

## 오류·재시도·취소

- `QUOTE_NOT_FOUND`: 본인 토큰 부재. 다른 사용자의 견적은 공개하지 않는다.
- `QUOTE_REQUEST_MISMATCH`: 토큰의 예약 대상·인원이 생성 요청과 다르다.
- `QUOTE_CONDITIONS_CHANGED`: 표시 조건 변경 또는 변경 전 계약의 토큰. 새 견적·새 의도 키로 재확인한다.
- `QUOTE_EXPIRED`: 토큰 만료. 슬롯 잠금 대기 중 만료도 예약 생성 전에 거절한다.
- `RESERVATION_SLOT_NOT_FOUND`, `RESERVATION_SLOT_INACTIVE`, `RESERVATION_CAPACITY_EXCEEDED`: 슬롯 부재·자격 상실·종료된 계약·시작됨·정원 부족에 따라 재탐색한다.
- `QUOTE_TERMS_UNAVAILABLE`: 시간대·취소 정책 미설정 또는 시각 해석 불가다.
- `QUOTE_RATE_LIMITED`: 사용자당 시간당 60건 또는 미사용 견적 120건 한도다. 미사용 만료 견적은 하루 뒤 기존 배치로 정리한다.
- `IDEMPOTENCY_KEY_REUSED`: 같은 키/토큰의 다른 본문이다. 한 토큰은 하나의 예약 의도에만 사용한다.

응답 유실·timeout·5xx는 실패 확정이 아니다. 동일 키·토큰·본문으로 재전송하면 저장된 성공 예약 또는 거절을 복구한다. 이미 성공한 예약은 이후 견적 만료나 조건 변경과 관계없이 원래 응답으로 복구한다. 다른 예약의 재고 차감만으로 확인 조건을 무효화하지 않는다.

취소는 확정 예약에만 허용하며 수락한 취소 정책을 사용한다. 처리 중 결제·미환불 결제·환불 처리 중 거래가 남아 있으면 `RESERVATION_REFUND_REQUIRED`로 거절한다. 이 보호는 토큰 없이 만들어진 기존 예약에도 적용한다. 취소 성공 시 재고를 한 번만 복구한다.

## 기존 데이터 및 결제 호환

V139는 기존 슬롯 조건과 예약/견적 JSON 원문을 각각 `legacy_reservation_terms`, `legacy_confirmation`에 복사한다. 새 조건 JSON에서는 상품·금액 필드를 제거하되, 기존 예약의 시간·인원·취소 정책과 재고·상태·멱등 키는 유지한다.

기존 상품 연결 슬롯과 단가/추가 비용이 양수인 슬롯은 `booking_retired=true`로 표시한다. 점주 편집/목록과 공개 탐색·견적·신규 예약에서 제외하며 자동으로 일반 예약으로 전환하지 않는다. 기존 예약의 조회·심사·취소·재고 복구는 유지한다. 필요하면 점주가 상품·금액 없는 독립 슬롯을 새로 등록한다. 종료된 유료 슬롯과 같은 시간의 새 슬롯 등록은 허용하되 기존 예약 참조는 변경하지 않는다.

기존 상품 테이블과 상품 참조 컬럼·외래 키는 이력 보존용으로 유지한다. 새 Java 도메인에는 예약 상품 enum/연결 필드·상품 전용 구현이 없으며 신규 DB 행은 기존 기본값을 사용한다. 보존용 컬럼은 예약 API 응답에 노출하지 않는다.

견적은 `contract_version=2`로 발급한다. 변경 전 버전 1 토큰은 사용자·멱등 검사 후 `QUOTE_CONDITIONS_CHANGED`로 거절한다. 이미 생성된 예약의 재시도는 기존 성공을 반환한다. 폐기한 상품 오류의 과거 거절 기록은 `QUOTE_CONDITIONS_CHANGED`로 이관한다.

신규 결제 승인 거래는 `RESERVATION_NOT_PAYABLE`로 거절한다. 이미 저장된 결제의 동일 멱등 키 결과 복구와 진행 중 승인 복구, 결제 조회·정산·점주 환불은 유지한다. 진행 중 결제의 금액·통화는 보존 원문과 대조하며 불일치/손상된 원문이면 처리 중 상태를 유지한다. 과거에 확인 조건이 없었던 결제의 기존 동작도 유지한다.

## 적용 및 검증 경계

예약 트래픽과 구버전 서버의 쓰기를 중지하고 DB 백업 후 V139와 새 서버를 함께 적용한다. 구버전/신버전 서버가 동시에 견적·슬롯을 쓰는 혼합 배포는 지원하지 않는다. 마이그레이션은 컬럼 추가·원문 복사·JSON 갱신·부분 인덱스 재생성을 하나의 트랜잭션으로 수행한다.

원문 보존만으로 구버전 서버를 바로 재기동할 수 있는 것은 아니다. 새 예약에는 구버전 금액 계약이 없으므로 롤백은 백업·변경 후 신규 예약/결제 이력을 함께 확인하고 결정한다. 과거 Flyway 파일은 수정하지 않는다. 운영 데이터 존재 여부와 실제 적용 결과는 별도 운영 확인 대상이다.

클라이언트는 제거된 요청 필드·검색 필터와 응답 렌더링을 함께 정리해야 한다. 서버 응답 변경만으로 앱의 고정된 가격 표시 문구가 제거되는 것은 아니다.

직접 관련 단위 테스트는 토큰 만료·조건 변경·멱등 재시도·재고·취소·이력 결제 금액 검증·JSON 응답 필드 제거를 확인한다. OpenAPI 통합 및 PostgreSQL 마이그레이션/동시성 테스트 결과는 실제 실행 여부와 함께 별도로 보고한다.
