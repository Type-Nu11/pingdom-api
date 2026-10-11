# 커뮤니티 화면 계약 (#1768)

## 호환성과 표시 정책

기존 게시글·댓글 요청의 새 필드는 선택적이다. `imageIds` 생략/null/빈 배열은 사진 없음,
`parentCommentId` 생략/null은 최상위 댓글이다. 기존 응답 필드와 1부터 시작하는 `page`,
`limit`(기본 20, 최대 100), 최신 시각·ID 내림차순을 유지한다. 카테고리는 기존
`PLACE`, `TRAVEL`, `MONEY` 중 하나이며 복수 태그를 지원하지 않는다.
모든 커뮤니티 경로는 기존 JWT 인증 정책을 따른다.

목록에 `preview`(공백 정리 후 Unicode 코드 포인트 기준 최대 120자), `thumbnailUrl`,
`imageCount`, `region`, `createdAt`, `viewCount`, `likeCount`, `commentCount`, `category`,
`country`가 추가된다. 대표 사진은 작성 요청의 첫 이미지이며 사진 없음은 URL=null, 수=0이다.
상세의 `images`는 `{imageId,imageUrl,displayOrder}` 배열이며 순서는 0부터 시작한다.

게시글의 `region`은 **첫 연결 장소**의 지역이다. 장소의 행정구역명이 있으면 사용하고,
없으면 저장된 주소를 반환한다. 연결 장소 없음 또는 첫 장소 삭제 시 null이며 다음 장소로
임의 대체하지 않는다. 연결 장소 응답에는 `imageUrl`, `category`, `region`이 추가된다.
삭제된 장소는 기존 ID·삭제 안내·`deleted=true`를 유지하고 새 표시 값은 null이다.
이 지역은 작성자 거주 지역이나 국적을 의미하지 않는다.

## 전체 목록과 국가 필터

- `GET /community/posts?page=1&limit=20&country=KR`: 카테고리 구분 없이 전체 탐색.
- `GET /community/categories/{categoryId}/posts`: 동일한 `country` 필터 추가.
- `GET /community/countries`: `{country,countryName}` 허용 값 목록.

필터는 **현재 작성자 프로필의 국가**를 기준으로 하며 허용 코드는 `KR`, `US`, `JP`,
`VN`, `CN`, `TH`이다. 파라미터에는 대문자 코드만 받는다. 생략 시 국가 미상·탈퇴·조회 불가
작성자의 게시글도 포함하고, 잘못된/빈 코드는 `400 INVALID_COUNTRY`이다.
기존 회원의 코드·영문/한글 국가명은 커뮤니티 조회에서만 정규화하며 회원 데이터를 변경하지 않는다.
미지원 국가·미상·탈퇴 작성자는 응답 `country=null`이다. 프로필 국가가 바뀌면 필터 결과도 바뀐다.

## 게시글 조회수

`POST /community/posts/{postId}/view`는 `{postId,viewCount}`를 반환한다.
사용자·게시글·KST 날짜별 최초 호출만 증가하며 중복 호출은 증가하지 않는다.
앱은 상세 진입 시 이 경로를 호출한다. 상세 GET과 목록 GET은 조회수를 변경하지 않는다.
기존 데이터는 0부터 시작하고 숨김/없는 게시글은 404이다. 기존 연결 장소 유입 조회수와 별도이다.

## 사진 업로드·작성·취소

1. `POST /community/images`에 multipart `file` 한 장을 전송한다.
2. 결과 `{imageId,imageUrl,contentType,fileSize,expiresAt}`로 미리보기를 표시한다.
3. 작성 요청의 `imageIds`에 최대 10개의 ID를 표시 순서대로 전달한다.
4. 작성 전 사진을 제거하면 `DELETE /community/images/{imageId}`를 호출한다(204).

허용 형식은 실제 디코딩 가능한 JPEG/PNG, 파일당 **10MiB(10,485,760바이트)** 이하이다.
가로·세로 각각 8,000px 이하, 총 36,000,000픽셀 이하이며 재인코딩으로 메타데이터를 제거한다.
`fileSize`는 재인코딩 후 저장 크기이다. 서버 multipart 파일 제한은 10MB, 전체 요청은
헤더 여유를 포함해 11MB이다. 여러 장은 각 파일을 별도로 업로드한다. 썸네일 URL에는 첫 사진
URL을 사용하며 서버가 별도 썸네일 파일을 생성·보관하지 않는다.

사진은 업로드 후 24시간 동안 연결 가능하다. 소유권·미연결·만료·중복·최대 장수를
검증하고 게시글과 사진 연결을 한 DB 트랜잭션에 저장한다. 등록 실패 시 모든 연결 변경이
롤백되고 미연결 사진은 만료 전 동일 ID로 재시도할 수 있다. 기존 사진을 다른 글에 재사용하거나
게시 후 삭제하는 기능은 제공하지 않는다.

작성 중 선택 취소는 미연결 사진 취소 API를 사용한다. 취소/만료 정리는 DB 메타데이터 삭제와
S3 삭제 Outbox 등록을 함께 커밋하고 실제 파일 삭제는 기존 worker가 재시도한다.
이미 연결된 사진은 만료 정리 대상에서 제외한다. 정리는 기본 1시간 주기, 시작 후 5분부터이며
`community.media.cleanup-enabled`, `cleanup-delay`, `cleanup-initial-delay`로 조정한다.
연결·취소·만료 정리가 같은 행을 잠가 연결된 파일을 정리하는 경쟁을 방지한다.

S3 업로드 실패는 503이며 업로드를 다시 시도한다. 업로드 성공 후 DB 저장/커밋 실패는 S3
보상 삭제를 시도하고, 삭제 실패는 별도 트랜잭션의 Outbox로 재시도한다. **프로세스 강제 종료 또는
S3 삭제와 DB/Outbox 장애가 동시에 발생하면 고아 객체가 남을 수 있다.** 이 경우 서버 오류 로그의
key를 기준으로 DB 연결 유무를 확인한 후 운영 정리한다. S3 성공·DB 기록 전 구간을 완전히 원자화하지는 않는다.
응답 유실 시 완료 여부를 확정할 수 없으므로 임의로 같은 이미지를 다른 글에 연결하지 않는다.
사용하지 않은 성공 업로드는 만료 정리된다.

| 상태 | 대표 오류 |
| --- | --- |
| 400 | 파일 누락/잘못된 이미지/해상도·파일 제한, 중복 또는 10장 초과 ID |
| 401 | 인증 필요 |
| 403 | `MEDIA_FORBIDDEN`: 다른 사용자의 사진 |
| 404 | `MEDIA_NOT_FOUND`: 없는/취소된/정리된 사진 |
| 409 | `MEDIA_ALREADY_CONNECTED`: 이미 연결된 사진 |
| 410 | `MEDIA_EXPIRED`: 아직 정리되지 않은 만료 사진 |
| 413 | `UPLOAD_TOO_LARGE`: multipart 파일·요청 제한 초과 |
| 503 | `MEDIA_STORAGE_UNAVAILABLE`: S3 사용 불가 |

## 대댓글과 댓글 좋아요

- 댓글 작성 요청에 `parentCommentId` 추가. 같은 게시글의 공개 최상위 댓글만 부모로 허용한다.
- 깊이는 1단계이다. 대댓글을 부모로 지정하면 `400 INVALID_COMMENT_PARENT`, 다른 게시글·숨김·없는
  부모는 `404 COMMENT_NOT_FOUND`이다.
- 기존 `GET /community/posts/{postId}/comments`는 공개 댓글과 대댓글의 평면 페이지를 유지한다.
  각 항목에 `parentCommentId`, `likeCount`, `liked`가 추가된다.
- `rootOnly=true`는 최상위 댓글만 조회하며 `totalCount`도 최상위 공개 댓글 수이다.
- `GET /community/posts/{postId}/comments/{commentId}/replies`는 특정 최상위 댓글의 공개 대댓글을
  별도 페이지로 조회한다. 부모가 다른 페이지에 있어도 이 경로로 답글을 조회할 수 있다.
- 기본 댓글 목록 및 게시글 목록의 `commentCount`는 공개 댓글·대댓글의 합이다. 부모가 숨김 처리되면
  해당 부모의 대댓글도 공개 목록·집계·좋아요 대상에서 제외한다. 기존 신고/관리자 숨김 이력은 보존한다.
- `GET/POST/DELETE /community/posts/{postId}/comments/{commentId}/likes`는
  `{commentId,likeCount,liked}`를 반환한다. 동일 사용자의 반복 등록·취소는 멱등적이다.
- 댓글 작성자가 탈퇴했으면 기존 탈퇴 표시 이름을 사용한다. 게시글 작성자 배지는 기존 authorId 비교를 유지한다.

## 검증 범위

관련 단위 테스트와 PostgreSQL API·동시성·마이그레이션·생성 OpenAPI 계약 테스트를 사용한다.
S3는 테스트 대역이며 실제 버킷 공개 접근, 배포 프록시 업로드 제한, 운영 worker 실행 및 앱의
Android/iOS 연동은 별도 배포 검증이 필요하다. API 추가만으로 앱 #421의 구현·배포 완료를 의미하지 않는다.
