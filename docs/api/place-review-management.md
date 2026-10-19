# 본인 장소 리뷰 수정·삭제 (#1809)

Common OpenAPI의 기존 장소 리뷰 태그에 아래 API를 추가한다. Bearer 인증이 필요하며 사용자 ID를 요청 본문으로 받지 않는다. 성공 후 내 리뷰·공개 목록과 리뷰 summary를 다시 조회한다.

## 수정

`PATCH /places/{placeId}/reviews/{reviewId}` → `200 PlaceReviewResponse`

```json
{
  "content": "안내가 친절하고 깨끗합니다.",
  "recommendReasons": ["FRIENDLY", "CLEAN"],
  "reviewMediaIds": [12, 9]
}
```

- 모든 필드의 누락과 `null`은 기존 값을 유지한다. `{}`도 현재 리뷰를 그대로 반환한다.
- `content`: 제공하면 공백 불가, 최대 2,000자. 저장 시 앞뒤 공백을 제거한다.
- `recommendReasons`: 제공하면 1~5개의 기존 enum 코드, 중복 불가, 요청 순서를 유지한다. 첫 코드를 기존 `recommendReason`에도 저장한다.
- 기존 `recommendReason`: 제공하면 공백 불가, 최대 100자. 복수 필드와 동시 입력 불가. 단일 필드로 수정하면 기존 복수 태그를 비운다. 기존의 임의 문자열 호환은 유지하며 집계는 유효 enum 코드만 포함한다.
- `reviewMediaIds`: 최대 3개, 양수·중복 불가. 응답의 `reviewMedia[].reviewMediaId`와 사진 업로드 응답 ID를 사용한다. 배열은 유지할 기존 사진과 신규 사진의 **최종 표시 순서**다. 누락한 기존 사진은 제거하며 `[]`는 전체 제거다. 본인·동일 장소·동일 리뷰에 연결된 사진 또는 본인·동일 장소의 미연결·미만료 업로드만 허용한다. 기존 연결 사진은 업로드 만료 시각이 지나도 유지할 수 있다.
- 임의 `imageUrls` 입력은 금지하며 생략·null·빈 배열만 허용한다. 구형 URL만 있는 사진은 `reviewMediaIds` 생략/null이면 유지한다. ID 목록을 명시하면 구형 URL도 포함해 전체 사진을 교체한다. 키가 없는 구형 URL의 외부 객체 삭제는 이 API가 수행하지 않는다.
- 사진과 추천 태그 순서를 기존 DB UNIQUE 제약과 충돌하지 않게 단계별 flush로 교체한다. 본문·태그·사진 연결/제거·삭제 outbox는 같은 트랜잭션이다. 검증 실패나 후속 저장 실패 시 DB 변경과 outbox를 모두 롤백한다. 제거된 관리 사진의 메타데이터는 삭제하고 S3 객체는 기존 outbox 처리기로 비동기 정리한다. 직접 S3 삭제는 수행하지 않는다.

## 삭제와 기존 심사

`DELETE /places/{placeId}/reviews/{reviewId}` → 본문 없는 `204`

- 본인 리뷰의 `VISIBLE`, `HIDDEN`, `DELETED` 모두 허용한다. 최초 `DELETED` 전이 시각은 유지하며 반복 요청도 `204`다.
- 리뷰 행·본문·추천 태그·연결 사진·사업자의 삭제 신청 및 관리자 심사 기록은 보존한다. 본인 삭제는 사진 정리 outbox를 발행하지 않는다. 기존 관리자·사업자의 증거 조회가 유지된다.
- 상점주에 의해 숨겨진 리뷰를 수정해도 `HIDDEN`은 유지한다. `PENDING` 삭제 심사 중 수정은 증거 보존을 위해 `409`로 거절하고 본인 삭제는 허용한다.
- 본인 삭제 이후에도 관리자는 기존 PENDING 신청을 승인·반려할 수 있다. 이미 `DELETED`인 리뷰에 대한 승인으로 최초 삭제 시각을 변경하지 않는다. 반려도 본인 삭제를 되돌리지 않는다.
- 방문 인증 세션·체크인 자격·인증 완료 기록은 변경하지 않는다. 리뷰 생성과 방문 인증은 현재 별도 흐름이며, 재작성은 기존 `POST /places/{placeId}/reviews` 정책을 유지한다. 새 리뷰 작성은 별도 새 행이다.

## 목록과 집계

- 공개 리뷰 목록: `VISIBLE`만 포함한다.
- `GET /users/me/reviews.totalElements`: 본인의 `VISIBLE`·`HIDDEN` 리뷰 건수다. `DELETED`는 제외한다. 앱이 이 목록으로 구성하는 내 검증 장소 목록에서도 삭제된 리뷰를 제거해야 한다. 동일 장소에 다른 본인 리뷰가 남으면 그 장소는 남을 수 있다.
- #1810의 `GET /places/{placeId}/reviews/summary`가 리뷰 집계의 기준이다. 커밋 뒤 다시 조회하면 `totalReviewCount`는 VISIBLE 리뷰 건수, `participantCount`는 유효 태그를 최소 하나 가진 고유 작성자 수, `reasonCounts[].count`는 태그별 고유 작성자 수를 반환한다. 동일 작성자의 여러 리뷰는 태그 합집합으로 집계한다. 복수 태그가 있으면 legacy는 무시하고, 없을 때만 유효 legacy 코드를 사용한다.
- 기존 장소 카드·방문 결정의 `verifiedEvidenceCount`는 **장소 정보 검증 근거 수**이며 리뷰 작성자 수가 아니다. 그 필드의 의미를 변경하지 않는다. 해당 화면의 리뷰 수·검증 참여 인원은 review summary를 조회한다.
- summary에는 HIDDEN·DELETED가 포함되지 않는다. 숨김 리뷰의 본인 수정은 공개 집계를 늘리지 않는다. 본인 리뷰 삭제는 방문 인증 자체를 취소하지 않는다.

## 오류 계약

| HTTP | code | 조건 |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED` | 본문/추천 이유/사진 개수·중복·양수·null 원소 등 값 검증 실패 |
| 400 | `INVALID_REQUEST_BODY` | 잘못된 JSON 또는 enum 코드 |
| 401 | `INVALID_TOKEN` | 인증 없음·유효하지 않은 인증 |
| 403 | `PLACE_REVIEW_MEDIA_FORBIDDEN` | 타인 또는 다른 장소의 사진 ID |
| 404 | `PLACE_REVIEW_NOT_FOUND` | 없는 리뷰·장소 불일치·타인 리뷰, PATCH의 삭제 리뷰 |
| 404 | `PLACE_REVIEW_MEDIA_NOT_FOUND` | 요청 사진이 없음 |
| 409 | `PLACE_REVIEW_UPDATE_PENDING_DELETION` | 삭제 심사 중 PATCH |
| 409 | `PLACE_REVIEW_MEDIA_ALREADY_CONNECTED` | 다른 리뷰에 연결된 신규 사진 |
| 409 | `PLACE_REVIEW_MEDIA_EXPIRED` | 신규 임시 사진의 연결 가능 시간 만료 |

리뷰 존재 여부는 타인·경로 불일치에도 동일한 404로 은닉한다. 사진은 기존 사진 업로드·취소 API의 403/404/409 계약을 유지한다.

```json
{"code":"PLACE_REVIEW_NOT_FOUND","message":"장소 리뷰를 찾을 수 없습니다."}
```

이 문서는 로컬 구현 계약이다. 실제 배포·공개 Swagger 반영은 별도 배포 이후 확인한다.
