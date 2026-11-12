# 장소 병합 스냅샷 복구 계약

병합 이력은 전체 장소 상태가 아니라 기존 무버전 JSON에 기록한 필드와 이동 항목만 복구한다. `PlaceMergeSnapshot`의 프로퍼티 이름과 null·enum 의미를 유지하며 스키마나 JSON envelope는 변경하지 않는다.

| 구분 | 필드 및 처리 |
| --- | --- |
| 스냅샷에서 보존 | id, name, address, roadAddress, jibunAddress, postalCode, geocodingSource, operatingStatus, operatingStatusCheckedAt, category, imageUrl, kakaoPlaceId, latitude, longitude, userId, registrant, englishName, touristSummary, touristCategories, regularOperatingHours, operatingExceptions |
| legacy 누락 기본값 | geocodingSource는 LEGACY, operatingStatus는 OPERATING, photoCount는 INSERT 시 0, 영업시간·예외 목록 null은 빈 일정 |
| 복원 기본값 | discoveryStatus=VISIBLE, primaryInformationSource=LEGACY, informationVerificationStatus=UNVERIFIED, createdAt=복원 시각 |
| 파생 및 재계산 | 좌표 geometry, 설정된 행정구역 regionCode, 관광정보 guard, 사진 ACTIVE 수 |
| 과거 값 복구 제외 | description, 정규 휴게시간, communityViewCount, 과거 노출·정보 검증 상태/검증자/검증 시각/증거 갱신 시각, 생성 시각 |

`PlaceMergeSnapshotCodec`는 장소 필드 추출과 snapshot JSON만 담당한다. 주입된 ObjectMapper의 미지 필드·enum·날짜 설정을 변경하지 않는다. 일반 이동 ID 및 중복 제거된 북마크·전환 JSON은 기존 처리 경로를 유지한다.

`PlaceMergeRestoreRepository`는 기존 23컬럼 명시 ID INSERT와 기본값 매핑만 담당한다. 호출자의 복구 트랜잭션에서 실행하고, 이후 JPA 재조회·좌표/행정구역/관광정보/일정 보정은 기존 application 순서를 유지한다. 별도 트랜잭션이나 이벤트로 분리하지 않는다.

병합은 source/target 장소 ID 오름차순 잠금, 복원은 이력 및 대상 장소 잠금을 유지한다. 미지원 FK 사전 거절, Kakao ID flush, 연관 이동, 사진 수 재계산, 단건 추천 재집계와 감사도 같은 트랜잭션에 참여한다. 전체 resync의 청크별 반영은 이 로컬 원자적 경로에 적용하지 않는다.

이동 후 삭제된 행은 복구하지 않으며 대상 장소의 병합 후 변경 전체를 되돌리지 않는다. 이전 JSON에 없는 필드를 추정해서 새롭게 복원하지 않는다. 형식 변경이 필요하면 이전 reader와 신규 이력의 양방향 호환 및 롤백 정책을 먼저 확정한다.

고정 fixture `moderation/place-merge-snapshot-v1.json`은 필드 추출·JSON 왕복·INSERT 매핑의 공통 계약이다. 순수 단위 검증은 null legacy 필드·잘못된 JSON/enum·주입된 mapper 설정과 JdbcTemplate mock의 컬럼-인자 매핑·실패 전파를 확인한다. H2 INSERT/호출자 트랜잭션 롤백은 integration 태그의 별도 통합 검증이며 기본 단위 게이트에서 제외한다. 실제 PostgreSQL geometry·FK 및 전체 병합/복원 롤백도 별도의 관련 통합 검증이 필요하다.
