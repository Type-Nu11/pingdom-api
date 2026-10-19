# 장소 리뷰 전체 추천 태그 집계

## 확정 계약

- `GET /places/{placeId}/reviews/summary`
- 요청 body·query·페이지·limit 없음. 기존 리뷰 목록과 동일하게 Bearer JWT 인증 필요.
- Swagger: `common` 그룹의 `장소 리뷰` 태그. App에서도 이 Common 계약을 소비합니다. 기존 그룹 원칙에 따라 `app` 문서에 중복 노출하지 않습니다.
- 기존 공개 리뷰 목록과 같이 장소 존재 여부만 확인합니다. 장소의 공개·영업 상태에 추가 제한을 두지 않습니다.

| 필드 | 형식 | 정의 |
| --- | --- | --- |
| `placeId` | int64, non-null | 조회 장소 ID |
| `totalReviewCount` | int64, non-null, 0 이상 | 전체 `VISIBLE` 리뷰 건수. 태그 미선택·유효하지 않은 태그·작성자 없는 과거 값도 포함 |
| `participantCount` | int64, non-null, 0 이상 | 유효 태그를 하나 이상 선택한 고유 `user_id` 수 |
| `reasonCounts` | 배열, non-null | 지원하는 7개 코드를 0건까지 모두 포함 |
| `reasonCounts[].reason` | enum, non-null | `FRIENDLY`, `EASY_TO_FIND`, `GOOD_FOOD`, `MULTILINGUAL_SUPPORT`, `PARKING`, `PHOTO_SPOT`, `CLEAN` |
| `reasonCounts[].count` | int64, non-null, 0 이상 | 해당 태그를 선택한 고유 `user_id` 수 |

배열 순서는 위 enum 선언 순서로 고정합니다. 화면의 내림차순 정렬·막대 비율·번역은 앱 책임입니다. 여러 태그 선택이 가능하므로 태그별 수 합계는 참여 인원보다 클 수 있습니다.

## 집계 대상과 과거 데이터

1. 장소의 전체 `VISIBLE` 리뷰를 대상으로 하며 `HIDDEN`·`DELETED`는 제외합니다. 페이지 크기와 무관합니다.
2. 동일 작성자의 여러 공개 리뷰는 태그 합집합으로 계산합니다. 같은 리뷰의 반복 태그도 같은 태그에 중복 가산하지 않습니다. 최신 리뷰만 고르는 정책은 사용하지 않습니다.
3. 복수 `recommendReasons`가 하나라도 존재하면 이전 단일 `recommendReason`은 사용하지 않습니다. 복수 목록이 비어 있을 때만 정확히 일치하는 유효 단일 코드를 사용합니다.
4. 지원하지 않는 코드·빈 문자열·다른 대소문자·임의 공백은 유효 태그가 아닙니다. 복수 값이 모두 unknown이어도 단일 값으로 fallback하지 않습니다.
5. `user_id IS NULL`인 과거 값은 리뷰 건수에는 포함하지만 참여·태그 인원에는 포함하지 않습니다. 사람 수를 리뷰 건수로 대체하지 않습니다. 현재 스키마의 작성자 NOT NULL 제약과 별개로 집계 정책을 명시합니다.

## 갱신·오류

별도 캐시·비동기 집계·허용 지연은 없습니다. 장소 존재·전체 리뷰 건수·태그·참여 인원을 단일 SQL snapshot에서 계산합니다. 작성·태그 수정·본인/관리자 삭제·상점주 숨김/복구 트랜잭션이 커밋된 뒤 다음 요청에 반영됩니다. 동시에 진행 중인 미커밋 변경은 집계하지 않습니다. #1809는 같은 리뷰와 공개 상태를 수정하며 별도 집계 갱신 호출을 필요로 하지 않습니다.

| HTTP | 코드 | 조건 |
| --- | --- | --- |
| 200 | - | 존재하는 빈 장소도 반환하며 모든 집계 값이 0 |
| 400 | `INVALID_REQUEST_PARAMETER` | 숫자 형식이 아니거나 int64 범위를 벗어난 ID |
| 401 | `INVALID_TOKEN` / `EXPIRED_TOKEN` | 인증 없음·유효하지 않은 토큰 / 만료 토큰. 기존 보안 필터 계약 적용 |
| 404 | `PLACE_NOT_FOUND` | 존재하지 않는 장소 ID. 0·음수도 기존 리뷰 목록처럼 없음 처리 |
| 500 | `INTERNAL_SERVER_ERROR` | 집계 쿼리 등 서버 내부 실패. 빈 집계로 대체하지 않음 |

현재는 로컬 소스의 확정 계약이며 배포·운영 응답 확인과 구분합니다.
