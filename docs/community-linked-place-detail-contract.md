# 커뮤니티 연결 장소 상세 조회 계약

게시글 상세 응답의 `places[].placeId`는 기존 장소 상세 API의 요청 식별자와 동일하다.
일반 장소 상세는 기존 API를 재사용하며, 게시글에서 장소를 선택한 경우에만 커뮤니티 유입 조회를 기록하는 API를 사용한다.

## 요청

```http
GET /places/{placeId}
```

일반 장소 탐색·직접 진입은 `GET /places/{placeId}`를 요청하며 조회수는 증가하지 않는다.

게시글 상세에서 장소를 선택한 경우에는 인증 정보와 함께 아래 API를 요청한다.

```http
POST /community/posts/{postId}/places/{placeId}/view
```

이 요청은 실제로 해당 게시글에 연결된 공개 장소만 조회하며, 같은 사용자의 같은 장소 조회는 KST 기준 하루에 한 번만 `communityViewCount`를 증가시킨다.

## 응답 기준

- 정상 장소는 `communityViewCount`를 포함한 기존 `PlaceDetailResponse`를 반환한다.
- 없는 장소 또는 삭제된 장소는 기존 `404 PLACE_NOT_FOUND` 응답을 그대로 사용한다.
- 게시글 상세에서 `deleted: true`인 연결 장소는 상세 요청 버튼을 제공하지 않는다.
- 게시글에 연결되지 않은 장소를 커뮤니티 유입 경로로 요청하면 `404 PLACE_NOT_LINKED`를 반환한다.

`communityViewCount`는 장소 언급 게시글 수와 다른 지표이며, 언급 게시글 수는 외부 응답에 노출하지 않는다.
