# 예약 가능 점주 자격 계약

예약 관련 Java 정책, JPQL, 주변 장소 native SQL은 아래 **현재 장소 소유자** 자격을 공유한다. 팀원 capability나 MANAGER 역할로 이 자격을 대체하지 않는다.

| 조건 | 허용 상태 | 거절 상태·경계 |
| --- | --- | --- |
| 사용자 역할 | `MERCHANT_OWNER` | 다른 역할 또는 사용자 부재 |
| 사용자 상태 | `ACTIVE` | `WITHDRAWN` |
| 정지 | `banned=false`, 또는 만료된 임시정지 | 영구정지, 미래 임시정지, 임시정지 만료 시각 부재 |
| 임시정지 만료 | `banType=TEMPORARY`이고 `banExpiresAt <= now` | `banExpiresAt > now` 또는 `null` |
| 점주 프로필 | `ACTIVE` | 부재, `PENDING`, `REJECTED`, `REVOKED` |
| 본인·사업자 인증 | 두 상태 모두 `APPROVED` | 인증 부재 또는 어느 한쪽 미승인 |
| 현재 장소 소유 | `merchant_owner_place`의 점주가 슬롯의 점주와 동일 | 매핑 부재 또는 소유권 이전 후 과거 점주 슬롯 |

현재 `UserStatus`는 `ACTIVE`와 `WITHDRAWN` 두 값이다. Java의 `!isWithdrawn()`과 SQL의 `status=ACTIVE`는 이 상태 집합에서 일치한다. 사용자 상태나 정지·인증·소유 정책이 추가되면 아래 구현과 계약 회귀를 함께 검토해야 한다. 비교에는 같은 `now`를 전달하며 회귀 fixture는 PostgreSQL `timestamp(6)`에 손실 없이 저장되는 시각을 사용한다.

## 구현 책임

| 경로 | 공통 자격 구현 | 유지하는 추가 조건 |
| --- | --- | --- |
| 점주 명령의 소유권 정책 | `AvailabilityAccessPolicy.requireOwnedPlace()` | 요청 점주와 현재 장소 소유 매핑 비교 |
| 공개 슬롯 | `PlaceAvailabilityRepository.findPublicByPlaceId()` | 전달된 슬롯 상태, 종료 전, 잔여량 양수, `bookingRetired=false`, 시작 시각·ID 정렬 |
| 추천 예약 신호 | `findPlaceIdsWithReservableAvailability()` | `ACTIVE`, 종료 전, 잔여량 양수, `bookingRetired=false`, 장소 ID 중복 제거 |
| 예약·견적 잠금 조회 | `findReservableByIdForUpdate()` | `bookingRetired=false`, `PESSIMISTIC_WRITE` 유지 |
| 실제 재고 예약 | `PlaceAvailabilityService.reserve()` | 자격을 포함한 잠금 조회 후 도메인이 미래 시작·활성 상태·양수 수량·잔여량을 검사 |
| 주변 장소 목록·count | `NearbyReservablePlaceQueryRepository` | 공개·운영 장소, 좌표·반경, 미래 시작, 기간 중첩·요청 정원·분류, `bookingRetired=false` |

공개 슬롯과 추천 신호는 이미 시작했지만 종료 전인 슬롯도 포함할 수 있다. 잠금 조회는 슬롯 상태·시각·재고를 대신 검사하지 않는다. 주변 장소만 장소 노출·운영·공간 조건을 추가 적용한다. 따라서 모든 경로의 전체 결과가 같다는 계약은 아니다.

JPQL 세 곳은 해당 저장소의 `RESERVABLE_MERCHANT_PREDICATE`만 재사용한다. native 목록과 count도 별도의 저장소 내부 상수를 재사용한다. 두 상수는 각각 `availability`/`a` alias와 `:now`에 의존한다. Java 정책은 독립적인 명시적 검사로 유지하며 SQL 생성기·문자열 치환·DB view·조회 후 N건 검사를 도입하지 않는다.

주변 목록은 장소별 가장 이른 시작 시각·ID의 슬롯 하나를 반환하고 count는 `COUNT(DISTINCT place_id)` 의미를 유지한다. 공통 자격 거절 시 Java 정책은 `PLACE_NOT_OWNED`, 실제 reserve는 잠금 조회 결과 부재에 따라 `AVAILABILITY_NOT_FOUND`를 반환한다. 자격은 유효하지만 슬롯 도메인 조건에 걸리면 reserve의 기존 `AVAILABILITY_CAPACITY_EXCEEDED` 매핑을 유지한다.

## 실제 DB 계약 회귀

`ReservableMerchantEligibilityPostgreSqlIntegrationTest`는 실제 Flyway 스키마를 적용한 PostGIS와 실제 정책·저장소·reserve 서비스를 사용한다. query mock이나 spy 없이 동일 fixture에 아래 상태만 변형한다.

- 정상, 탈퇴, 역할 변경, 프로필 대기·회수·부재, 본인/사업자 미승인·인증 부재
- 영구정지, 미래·과거·정확히 현재 시각에 만료되는 임시정지, 만료 시각 부재
- 소유권 이전, 소유 매핑 부재
- 공통 자격이 유효한 상태에서 시작·종료·비활성·재고 소진·계약 종료 슬롯 및 숨김·폐업·좌표 부재·반경 밖 장소

프로필 부재는 실제 FK의 `ON DELETE CASCADE`를 적용하여 슬롯·인증·매핑도 제거한다. 제약을 끄거나 고아 슬롯을 삽입하지 않는다. 소유권 이전은 새로 자격을 갖춘 점주에게 매핑만 이전하여 과거 슬롯 점주를 통한 자격 우회를 검사한다. SQL로 상태를 변경한 뒤 영속성 컨텍스트를 비워 Java 정책도 실제 DB 상태를 읽는다.

정상 대조 장소를 먼저 만들고 페이지 크기를 1로 설정하여 첫 페이지를 가득 채운다. 이로써 Spring Data의 짧은 페이지 count 생략을 피하고 실제 native `countQuery` 결과도 검사한다. 대상 장소에 슬롯을 두 개 두어 장소별 대표 슬롯과 장소 기준 집계도 확인한다. 각 시나리오는 트랜잭션 롤백으로 격리하며 reserve는 조회 검증 뒤 마지막에 호출한다.

이 회귀는 현재 자격의 동등성과 경로별 조건을 확인한다. 사용자·점주 자격 변경과 예약 사이의 동시성 전체나 실행 계획의 성능을 증명하지는 않는다. 변경은 기존 predicate 추출이므로 Java 정책, 트랜잭션 경계, 잠금, 스키마를 바꾸지 않는다.
