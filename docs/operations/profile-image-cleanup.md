# 프로필 이미지 보존과 정리

프로필 변경은 서버가 만든 `users/profile-images/tracked/{UUID}.jpg|png`와 canonical URL을 별도 트랜잭션으로 선등록한다. 기존 사용자 갱신 트랜잭션은 `User.version` 검사와 새 원장 연결·이전 원장 orphan 전이를 함께 커밋한다. 업로드 예외·낙관적 충돌·실제 commit 실패가 나면 선등록 원장을 유지한다. 요청 스레드에서 즉시 S3 삭제를 하지 않는다.

기존 URL만 있는 이미지는 서버 소유권을 입증할 수 없어 자동 정리하지 않는다. 이전 이미지도 같은 사용자의 프로필 전용 원장에 있는 경우만 교체 시 orphan으로 표시한다. 사용자 FK cascade는 두지 않으며, 참조가 사라진 원장은 사용자 삭제 후에도 재검사한다.

새 tracked namespace는 가입 입력에서 예약한다. 해당 path의 URL은 host와 무관하게 CDN·S3 virtual-host/path-style alias, 인코딩된 구분자·dot segment를 검사하여 `PROFILE_IMAGE_URL_RESERVED`(400)로 거절한다. 정상 서버 업로드의 신규 intent만 이 namespace를 사용자에 연결한다. 따라서 삭제 마지막 참조 확인 뒤 같은 key를 다른 가입 요청이 다시 채택하지 못한다. 기존 외부/legacy URL 계약은 유지한다. 정리 활성 배포 전 모든 가입 처리 인스턴스가 이 방어를 포함하는지 확인한다.

orphan은 관측 후 14일 보존한다. 업로드 중 intent는 제한 시간 2분과 확정 여유 1분 뒤부터 검사한다. 늦은 S3 PUT은 `lastModified`부터 14일을 추가 보장한다. 모든 사용자의 canonical URL을 후보 선정과 실제 삭제 직전에 다시 확인하고, 현재·공유 참조가 있으면 삭제하지 않는다.

삭제는 프로필 전용 Outbox type과 매 시도 UUID를 사용한다. 기존 key 영구 dedup 삭제 publisher는 사용하거나 변경하지 않는다. HEAD로 관측한 immutable `versionId`와 ETag에 대한 조건부 삭제만 허용한다. 버전이 없거나 `null`이면 무조건 삭제로 대체하지 않고 경고 및 `profile.image.cleanup.deferred{reason="missing_immutable_version"}`를 기록한 뒤 24시간 후 재검사한다. 저장소 오류는 Outbox 재시도를 유지한다. 삭제 후 원장도 남겨 늦은 PUT과 객체 재발생을 재확인한다. 임의의 비현재 버전을 열거하거나 삭제하지 않는다.

설정은 `identity.profile-image-storage`에 있다. `cleanup-enabled`는 기본 true, `upload-timeout`은 `PT2M`(최대 5분), `batch-size`는 50(최대 100), `recheck-delay`는 `PT24H`, `cleanup-delay`와 `cleanup-initial-delay`는 `PT1H`다. worker 중단은 `cleanup-enabled=false`로 새 정리 발행을 멈춘다. 이미 발행된 Outbox 이벤트는 별도 상태이므로 중단/구버전 롤백 전 반드시 drain 여부를 확인한다. 새 타입을 처리할 수 있는 버전의 handler는 유지하고 신규 요청 유입을 먼저 멈춘다. 원장과 migration은 삭제하지 않는다.

버킷 versioning·IAM·lifecycle 설정과 운영 객체는 이 변경에서 조회하거나 변경하지 않는다. immutable versionId를 확보하지 못한 환경에서는 정리가 보류된다. 14일 보존을 우회하는 외부 lifecycle은 별도 운영 확인 대상이다. 서버가 추적하지 않은 기존 key/비현재 버전 정리는 범위에 포함하지 않는다.

검증은 순수 단위 타깃과 `ProfileImagePostgreSqlIntegrationTest`를 분리한다. PostgreSQL 타깃은 Flyway V172, 실제 외부 commit 실패, `REQUIRES_NEW` 원장 보존, `User.version` 충돌, 성공 교체 및 14일 경계를 검증한다. 운영 AWS가 필요하지 않다.
