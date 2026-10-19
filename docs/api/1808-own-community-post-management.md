# 내 커뮤니티 게시글 관리 계약 (#1808)

이 문서는 로컬 구현 계약이며 운영 배포 완료를 의미하지 않는다. 모든 경로는 JWT 인증이 필요하다.

## 내 글 목록

`GET /users/me/community/posts?page=1&limit=2`

- 인증 사용자 본인 글만 조회한다. 클라이언트의 사용자 ID를 받지 않는다.
- `page`는 1부터 시작하고 `limit`은 1~100, 기본 20이다. 마이페이지 미리보기는 `limit=2`를 사용한다.
- `createdAt DESC, postId DESC` 정렬이며 수정은 등록 순서를 바꾸지 않는다.
- `posts`, `page`, `limit`, `totalElements`, `totalPages`, `hasNext`를 반환한다.
- 카드는 `postId`, `title`, `contentPreview`(앞 150자), `imageUrl`, `imageCount`, `category`, `countryCode`, `region`, `createdAt`, `updatedAt`, `viewCount`, `likeCount`, `commentCount`, `hidden`을 포함한다.
- 숨김 글은 `hidden=true`로 포함한다. 공개 상세 조회와 수정은 불가하고 삭제는 가능하다. 삭제 글은 제외한다.
- 수정 전 `updatedAt`은 null이며 마지막 본인 수정 시각만 기록한다.

## 부분 수정

`PATCH /community/posts/{postId}`

```json
{"title":"수정한 여행 후기","categoryId":"TRAVEL","placeIds":[1],"imageIds":[12,11,15],"region":null}
```

| 입력 | 의미 |
| --- | --- |
| 필드 누락 | 기존 값 유지 |
| `countryCode: null`, `region: null` | 해당 위치 정보 초기화 |
| `placeIds: []`, `imageIds: []` | 해당 연결 전체 제거. PLACE 카테고리는 장소 1개 이상 필수 |
| 다른 필드의 명시적인 null | `400 INVALID_REQUEST_BODY` |
| 빈 객체 또는 공백 제목·본문·카테고리 | `400 VALIDATION_FAILED` |

- 제목은 최대 50자, 본문은 최대 5,000자이며 앞뒤 공백을 제거한다. 단일 활성 카테고리와 기존 장소 검증을 유지한다.
- 국가 코드는 기존 `GET /community/countries` 허용 값이며 잘못된 값은 `400 INVALID_COUNTRY`다. 표시 지역은 최대 100자이고 공백은 null로 정리한다.
- `imageIds`는 최종 전체 사진 순서다. 상세 응답 `images[].imageId`로 기존 사진을 유지·재정렬하며, 본인 미사용·업로드 완료·미만료 이미지 ID를 추가할 수 있다.
- 최대 10장이며 중복·타인·다른 글·없는 이미지와 만료된 미사용 이미지는 `400 INVALID_IMAGE`다. 현재 글에 연결된 사진은 업로드 후 24시간이 지나도 유지할 수 있다.
- 빠진 기존 사진은 DB에서 제거하고 같은 트랜잭션에 S3 삭제 Outbox를 기록한다. 실제 객체 삭제는 기존 비동기 재시도 정책을 따른다. 새로 업로드했지만 연결하지 않은 사진은 기존 24시간 만료 정리 대상이다.
- 게시글 행을 먼저 잠근 뒤 이미지 행을 ID 오름차순으로 잠근다. 사진 순서의 유일 제약을 지키기 위해 기존 연결을 트랜잭션 내부에서 해제·flush한 뒤 최종 순서로 연결한다. 실패하면 게시글·장소·이미지·Outbox 전체를 롤백한다.
- 성공은 `200 {"postId":1,"updatedAt":"2026-10-19T00:00:00"}`다. 공개 상세와 내 글 카드에도 `updatedAt`을 제공한다.

## 삭제와 노출

`DELETE /community/posts/{postId}`의 성공은 204다.

- 본인 글은 관리자 숨김 상태에서도 삭제할 수 있다. 이미 삭제한 글의 반복 삭제는 404다.
- `deletedAt`을 기록하는 soft delete이며 원본 본문·댓글·좋아요·신고와 관리자 숨김 이력은 보존한다. 관리자 기존 조회와 신고 검토는 유지한다.
- `hidden=true`도 함께 기록해 구버전 바이너리로 롤백해도 삭제 글이 공개 목록·상세에 재노출되지 않게 한다. 기존 `hiddenByAdminUserId`와 `hiddenAt`은 덮어쓰지 않는다.
- 일반/카테고리/내 글 목록에서 제외하고 상세·댓글·좋아요·장소 조회·신고 등 공개 진입을 차단한다. hidden 값이 바뀌어도 deletedAt 조건으로 공개 재노출을 막는다.
- 장소 연결은 제거하므로 해당 글은 내부 장소 언급 집계에서 제외된다. 기존 장소 조회수 이력은 유지한다. 이미지 원장은 삭제하고 S3 삭제 Outbox를 같은 트랜잭션에 기록한다.
- 기존 `DELETE /community/images/{imageId}`는 사진 하나를 지우는 기능이며 글 삭제를 대체하지 않는다.

## 오류와 변경 범위

- JWT 없음·무효: 401, 기존 인증 오류 계약을 따른다.
- 수정 시 타인·숨김·삭제·없는 글, 삭제 시 타인·삭제·없는 글: `404 POST_NOT_FOUND`. 작성자 존재 여부를 403으로 구분하지 않는다.
- 장소 없음: `404 PLACE_NOT_FOUND`. 중복 장소, PLACE의 빈 장소, 카테고리 오류는 기존 400 코드다.
- 숨김 포함 내 글 조회 외 기존 공개 노출 정책은 #1536 및 #1768을 유지한다.
- V140은 nullable `updated_at`, `deleted_at`과 미삭제 글의 작성자·작성시각·ID 인덱스를 추가한다. 기존 글은 두 시각 모두 null로 유지한다.

배포 시 V140을 먼저 적용해야 한다. 실제 S3 객체 삭제·운영 인증 요청은 로컬 DB/테스트 대역 검증과 구분해 확인한다.
