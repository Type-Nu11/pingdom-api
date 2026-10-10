# 커뮤니티 앱 계약 (#1768)

기존 필드를 유지하면서 목록·상세·작성 화면의 누락 계약을 추가한다. 운영 배포는 PR 리뷰/병합 이후이며 이 문서는 배포 완료를 의미하지 않는다.

## 게시글 탐색과 표시

- `GET /community/posts?page=1&limit=20&countryCode=KR`: 전체 게시글. 국가 필터는 선택값이다.
- 기존 `GET /community/categories/{categoryId}/posts`에도 같은 국가 필터를 지원한다.
- `GET /community/countries`: ISO 3166-1 alpha-2 허용 코드 목록. 정확한 대문자 코드만 허용하며 `ZZ`, 소문자는 `400 INVALID_COUNTRY`다.
- 국가가 미설정된 기존 글은 전체 조회에 포함되지만 특정 국가 필터에는 포함되지 않는다. 작성자 국적이나 장소 좌표로 국가를 추측하지 않는다.
- 목록 항목: 기존 `postId`, `title`에 `contentPreview`(본문 앞 150자), `imageUrl`(첫 사진), `imageCount`, `countryCode`, `region`, `createdAt`, `viewCount`, `likeCount`, `commentCount`, `category` 추가.
- 상세: 기존 필드에 순서 있는 `images[{imageId,imageUrl,position}]`, `countryCode`, `region`, `viewCount` 추가. 정상 상세 GET마다 조회수를 원자적으로 1 증가한다. 사용자별/일별 고유 방문 집계는 아니다.
- 연결 장소: 기존 `placeId`, `placeName`, `deleted`에 `imageUrl`, `category`, `regionCode`, `region`, `address` 추가. 지역 이름은 기존 지역 사전에서 일괄 조회하며 미해석 지역은 null이다. 표기 주소도 함께 제공한다. 삭제된 장소는 새 필드가 null이다.
- 복수 태그는 지원하지 않는다. 기존 단일 `categoryId`/`category`를 유지한다.

## 이미지 업로드와 작성

1. JWT 인증으로 `POST /community/images`에 multipart `file` 하나를 업로드한다.
2. `201 {imageId,imageUrl,expiresAt}`를 받아 미리보기를 표시한다. expiresAt은 UTC 기준 24시간 후다.
3. 기존 `POST /community/posts` 요청에 `imageIds`를 추가한다. 선택적으로 `countryCode`, `region`(최대 100자)을 지정한다.

```json
{"categoryId":"TRAVEL","title":"여행 후기","content":"본문","placeIds":[],"imageIds":[12,11],"countryCode":"KR","region":"서울"}
```

- JPEG/PNG만 허용하며 선언 MIME·실제 형식 모두 검증한다. 파일·변환 결과 각 10MiB 이하, 각 변 8,000px 이하, 전체 3,600만 픽셀 이하. 디코딩·재인코딩으로 EXIF 등 메타데이터를 제거한다. 서버 multipart 제한 초과는 기존 전역 처리 규칙을 따른다.
- 한 게시글 최대 10장, ID 중복 불가. 본인·미사용·업로드 완료·미만료 파일만 연결한다. `imageIds` 입력 순서가 0부터 시작하는 `position`이다. 연결된 파일은 더 이상 만료되지 않는다.
- 기존 작성 요청은 imageIds/countryCode/region 생략 가능하다. 임의 외부 URL/S3 key를 받지 않는다.
- 업로드 실패는 `400 IMAGE_FILE_INVALID` 또는 `503 IMAGE_UPLOAD_UNAVAILABLE`. 게시글 연결 실패는 `400 INVALID_IMAGE`이며 게시글·장소·이미지 연결 전체가 롤백된다.
- S3 요청 전에 신규 UUID key를 DB에 커밋한다. 요청 실패·프로세스 종료에도 미사용 정리 원장에 남는다. 게시글 등록 실패 후에는 만료 전 동일 이미지 ID로 다시 작성할 수 있다.
- `DELETE /community/images/{imageId}`: 본인 미사용 파일 또는 본인 공개 게시글 사진 삭제. 타인·없는 이미지·숨김 게시글은 `404 IMAGE_NOT_FOUND`, 성공은 204다. 사진 삭제 후 position에 빈 번호가 생길 수 있으며 응답 순서가 갤러리 순서다.
- 미사용 이미지는 24시간 후 1분 주기, 최대 100개씩 행 잠금/SKIP LOCKED로 정리한다. 명시적 삭제·만료 정리는 DB 변경과 S3 삭제 Outbox를 같은 트랜잭션에 기록하며 저장소 삭제 실패는 기존 Outbox 재시도 정책을 따른다. 실제 객체 삭제 시점은 Outbox 처리 시점이다.

## 대댓글과 댓글 좋아요

- 기존 `POST /community/posts/{postId}/comments` 본문에 선택적인 `parentCommentId` 추가. 같은 공개 게시글의 공개 최상위 댓글에만 답글을 달 수 있다. 대댓글에 답글 작성·다른 게시글 부모는 `400 INVALID_PARENT_COMMENT`다.
- 작성 응답에도 `parentCommentId`를 반환한다.
- 기존 댓글 목록 항목에 `parentCommentId`, `likeCount`, `liked` 추가. 기존 최신순·페이지 형식을 유지하는 평탄 목록이다.
- `GET /community/posts/{postId}/comments?parentCommentId={id}`는 해당 최상위 댓글의 답글만 페이지 조회한다. 필터 생략은 기존 댓글/답글 전체 목록이다. 숨김 부모의 답글은 목록·댓글 수·좋아요 API에서 노출하지 않는다.
- `GET`, `POST`, `DELETE /community/comments/{commentId}/likes`: JWT 필수, `{commentId,likeCount,liked}`. 등록·취소를 반복해도 같은 사용자 좋아요는 최대 1개다. 숨김 댓글·부모·게시글은 `404 COMMENT_NOT_FOUND`다.

## DB와 검증

V138은 국가/지역/조회수, 댓글 부모 관계, 댓글 좋아요 고유 제약, 이미지 소유권·연결 순서·만료 원장을 추가한다. 기존 데이터는 국가/지역/부모 null, 조회수 0으로 유지한다.

PostgreSQL API 테스트로 순서·국가 필터·조회수·답글·숨김 부모·반복 좋아요·타인 이미지·중복/만료 이미지·가짜 이미지 거절과 등록 롤백을 검증한다. S3는 테스트 대역을 사용하므로 실제 버킷 권한·공개 이미지 접근·Outbox 실제 삭제는 배포 환경에서 추가 확인해야 한다.

최종 로컬 검증 결과:

- `./gradlew test`: 1,233개 통과.
- `./gradlew integrationTest --tests '*Community*'`: 커뮤니티·관리자 회귀 및 OpenAPI 36개 통과.
- `./gradlew postgresIntegrationTest --tests '*Community*'`: V138 적용/스키마 검증과 API·동시성 7개 통과.
- `git diff --check`: 통과.

전체 통합 테스트 실행에서는 작업 범위 밖의 예약 OpenAPI nullable 검증과 GeminiVoiceClient 테스트 빈 누락 실패도 관찰했다. 위 결과는 전체 통합 테스트 통과를 의미하지 않는다. 해당 영역의 코드는 변경하지 않았다.

## 마이그레이션 검증 보완

V138 적용은 성공했지만 `FlywayMigrationIntegrationTest`의 최신 버전 기대값이 V137에 머물러 스모크 테스트가 실패했다. 최신 버전을 V138로 갱신하고 업그레이드 적용 개수를 `최신 버전 - 시작 버전`으로 계산하도록 변경했다. 빈 DB와 기존 DB 업그레이드에서 커뮤니티 필드·이미지·댓글 좋아요 테이블도 확인한다.

Hibernate 스키마 검증 테스트는 기존 CONCURRENTLY 인덱스와 Flyway 트랜잭션 잠금의 충돌로 대기했다. 다른 PostgreSQL 테스트와 동일하게 세션 잠금 설정(`spring.flyway.postgresql.transactional-lock=false`)을 적용했다. V138 SQL 및 운영 DB는 변경하지 않았다.

보완 후 `./gradlew migrationSmokeTest migrationTest --no-daemon`: 스모크 1개, 전체 마이그레이션 25개 모두 통과했다. 테스트 종료 시 DB 컨테이너 정리 이후 스케줄러의 연결 거절 로그가 있었으며, 테스트 실패는 0개다.
