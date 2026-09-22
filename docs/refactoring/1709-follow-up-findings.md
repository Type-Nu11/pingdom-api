# #1709 검토 중 확인한 후속 검토 항목

기준 커밋은 `0388d61b90344c880f4e720d3163b36ce71e3474`이다. 아래 항목은 주석 작성 중 확인한 기존 구현과 테스트의 정적 관찰이다. 이번 작업에서 동작을 수정하거나 해당 장애를 재현한 것은 아니다. 각 항목의 후속 검증을 수행한 뒤 별도 수정 범위를 결정해야 한다.

## 운영 동작 검토 후보

| 위치 | 코드에서 확인한 조건 | 필요한 후속 검증 |
| --- | --- | --- |
| [ChangeInfoService.changeUsername](../../src/main/java/com/typenull/pingdom/identity/application/command/ChangeInfoService.java) | 현재 이름과 요청 이름이 같을 때만 `existsByUsername`을 호출한다. | 다른 사용자의 이름으로 변경하는 요청과 본인 이름 유지 요청을 비교하고 DB 제약·예외 변환까지 확인한다. |
| [PlaceRegistrationService.toRegularHours](../../src/main/java/com/typenull/pingdom/place/application/service/registration/PlaceRegistrationService.java) | 등록 영업 상태에 `OPEN_24_HOURS`가 있으나 승인 장소의 일반 영업시간으로 변환할 때는 `OPEN`만 포함한다. | 24시간 영업으로 제출·승인한 신청의 실제 장소 일정 응답을 확인한다. |
| [PlaceRecommendationSnapshotService](../../src/main/java/com/typenull/pingdom/place/application/service/recommendation/snapshot/PlaceRecommendationSnapshotService.java), [PlaceRecommendationVersionSnapshotService](../../src/main/java/com/typenull/pingdom/place/application/service/recommendation/snapshot/PlaceRecommendationVersionSnapshotService.java) | 최초 생성은 장소 잠금 후 재조회하지만 기존 snapshot의 조회 후 증분 갱신에는 쓰기 잠금이나 `@Version`이 없다. | 서로 다른 트랜잭션이 같은 snapshot을 동시에 갱신할 때 노출·클릭·전환 증가분이 보존되는지 확인한다. |
| [PlaceRecommendationPolicyService](../../src/main/java/com/typenull/pingdom/place/application/service/recommendation/policy/PlaceRecommendationPolicyService.java) | 여러 `volatile` 맵을 순차 교체하고 읽기 경로는 각 맵을 별도로 참조한다. 트래픽 버킷 배정은 맵 순회 순서에도 영향을 받는다. | 정책 갱신과 요청이 겹칠 때 일관된 정책 묶음을 읽는지, `Map.copyOf`를 거친 순회 순서가 배정 안정성에 영향을 주는지 확인한다. |
| [LocationAnalysisHtmlComposer.compose/text](../../src/main/java/com/typenull/pingdom/analysis/application/LocationAnalysisHtmlComposer.java) | 일부 AI 요약을 `text()`로 변환해 panel 본문에 넣지만 `text()`는 XML/HTML escape를 하지 않는다. | 특수문자와 태그가 포함된 요약으로 XHTML 변환 및 저장 HTML 표시 결과를 확인한다. |
| [AdminPlaceDuplicateResolver.match](../../src/main/java/com/typenull/pingdom/moderation/application/support/AdminPlaceDuplicateResolver.java) | Kakao ID 일치 판정보다 먼저 nullable 좌표를 `double` 인자로 전달한다. 후보 조회는 null 좌표를 일괄 제외하지 않는다. | 좌표가 없는 중복 후보가 포함된 관리자 목록·상세에서 예외가 발생하는지 확인한다. |
| [AdminPostServiceImpl.hidePost/deletePost](../../src/main/java/com/typenull/pingdom/moderation/application/service/post/AdminPostServiceImpl.java) | 숨김 전이 때 장소 사진 수를 감소시키고, 삭제 시에는 기존 숨김 여부와 관계없이 다시 감소시킨다. | 동일 게시글을 숨긴 뒤 삭제할 때 장소 사진 수가 실제 공개 사진 수와 일치하는지 확인한다. |
| [AdminReportServiceImpl](../../src/main/java/com/typenull/pingdom/moderation/application/service/report/AdminReportServiceImpl.java) | 사진 신고 처리 경로는 일반 조회로 `PENDING`을 검사하며 커뮤니티 신고 경로와 잠금 방식이 다르다. | 단건·일괄 승인 요청이 같은 신고에 겹칠 때 신고자 통계·제재·감사 기록이 중복 반영되는지 확인한다. |
| [AdminPlaceMergeService.restoreMerge](../../src/main/java/com/typenull/pingdom/moderation/application/service/place/merge/AdminPlaceMergeService.java), [PlaceRecommendationSnapshotResyncService.resyncMergedPlace](../../src/main/java/com/typenull/pingdom/place/application/service/recommendation/snapshot/PlaceRecommendationSnapshotResyncService.java) | 복구 경로도 대상 snapshot만 재계산하고 원본 snapshot은 삭제하는 병합용 메서드를 호출한다. 해당 호출에는 버전·유사도 snapshot 재동기화도 없다. | 병합 후 복구한 원본·대상 장소의 일반·버전·유사도 snapshot이 실제 이벤트와 일치하는지 확인한다. |
| [AdminPlaceOperatingScheduleService.updateOperatingSchedule](../../src/main/java/com/typenull/pingdom/moderation/application/service/place/operating/AdminPlaceOperatingScheduleService.java), [MapPlace.replaceOperatingSchedule](../../src/main/java/com/typenull/pingdom/place/domain/place/core/MapPlace.java) | 관리자 일정 교체는 휴게시간을 빈 집합으로 전달하는 2인자 overload를 사용해 기존 정규 휴게시간을 제거한다. | 휴게시간이 있는 장소의 관리자 일정 변경 후 데이터와 응답을 확인하고 의도한 교체 계약인지 판단한다. |

## 기존 테스트가 확인하는 범위

| 위치 | 현재 검증의 범위와 한계 |
| --- | --- |
| [AdminSecurityTest.adminPostsAccess](../../src/test/java/com/typenull/pingdom/integration/admin/AdminSecurityTest.java), [AdminPostControllerTest.removedPostQueries](../../src/test/java/com/typenull/pingdom/integration/admin/AdminPostControllerTest.java) | 관리자 인증으로 `GET /admin/posts`를 호출할 때 각각 200과 404를 기대한다. 계약 기대값의 불일치 후보이며 이번 작업에서는 두 assertion을 유지했다. |
| [AuthControllerTest.refreshTokenContract](../../src/test/java/com/typenull/pingdom/integration/auth/AuthControllerTest.java) | `refreshTokenOf` helper가 다시 로그인하므로 마지막 토큰 차이 비교만으로 refresh 요청 자체의 토큰 회전을 직접 입증하지 않는다. |
| [OpenApiDocumentationValidationTest.validateAgainstSchema](../../src/test/java/com/typenull/pingdom/integration/swagger/OpenApiDocumentationValidationTest.java) | 예시 검사기는 null·범위·format 전체를 검증하지 않는다. `oneOf`는 최소 한 개 일치를 확인하며 직접 자기 참조 외의 다중 노드 순환까지 탐지하지 않는다. |
| [PlaceRecommendationPortfolioComparisonTest](../../src/test/java/com/typenull/pingdom/place/PlaceRecommendationPortfolioComparisonTest.java) | 고정 fixture의 순위·결과 유사도 비교다. CTR과 북마크 전환율은 관련 후보 순위에 고정 비율을 대입한 모의 수치이며 실제 사용자 지표 개선 결과가 아니다. |

통합·Docker·동시성 재현 테스트는 이번 승인된 검증 범위에 포함하지 않았다. 이 문서의 후보를 확인하기 위한 테스트 실행이나 동작 수정은 별도 작업으로 다룬다.
