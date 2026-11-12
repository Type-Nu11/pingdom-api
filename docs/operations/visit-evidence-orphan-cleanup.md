# 방문 증빙 orphan 정리

신규 업로드는 `visit-evidence/tracked/<intent UUID>.jpg|png` key를 S3 호출 전에 DB에 기록합니다. 활성 `visit_evidence` 행이 있는 객체의 기존 보존·다운로드 계약은 변경하지 않습니다. 14일은 미참조 orphan에만 적용합니다.

## 활성화와 중지

- V162 migration과 애플리케이션을 함께 적용하고, 구버전 업로드 요청 종료를 확인합니다.
- `verification.visit-evidence-orphan.cleanup-enabled` 기본값은 true이며 신규 추적 orphan 정리가 자동 실행됩니다. 버전 식별자가 없어 안전한 삭제를 할 수 없는 객체만 보류합니다.
- 정리는 intent deadline 경과 또는 실패 확인 후 orphan으로 판정한 시점부터 14일을 기다립니다. S3 객체가 더 최근에 생성됐다면 수정 시각에서 다시 14일을 기다립니다.
- 실행 중인 유효 업로드와 DB 참조를 재검증하고, 이미 정리 상태로 전환한 intent의 늦은 DB 연결은 거절합니다. SDK 업로드 상한은 기본 2분, 완료 허용 deadline은 그보다 1분 뒤입니다.
- S3 검사·삭제는 DB 트랜잭션 밖에서 실행하며 삭제 직전 참조를 다시 확인합니다. 실패는 Outbox 재시도에 맡기며 S3 미설정을 성공으로 숨기지 않습니다.
- 중지 시 cleanup-enabled를 false로 바꾸고 이미 발행된 전용 Outbox 작업도 중지/정리해야 합니다. 코드 롤백만으로 삭제된 객체를 복원할 수 없습니다. intent 테이블을 롤백 시 삭제하지 않습니다.

## 늦은 put과 버전

삭제 완료나 객체 부재 뒤에도 intent를 보존하고 기본 하루 뒤 다시 확인합니다. 시도별 별도 Outbox 중복 방지 키를 사용하여 이전 삭제 성공이 후속 정리를 영구 차단하지 않습니다. S3에서 확인한 실제 immutable versionId와 ETag를 사용하며 412일 때 무조건 삭제로 대체하지 않습니다. versionId가 비어 있거나 문자열 `null`인 비버전/버전 중지 객체는 삭제하지 않고 하루 뒤 재검사합니다. 보류 건은 경고 로그와 `visit.evidence.orphan.deferred{reason="missing_immutable_version"}` counter로 관측합니다. ETag만으로 동일 바이트 재생성을 구분할 수 없으므로 이 제한을 둡니다.

운영 bucket lifecycle, versioning, delete marker, 복제·Object Lock 및 조건부 삭제 지원은 이 변경에서 실조회하지 않았고 설정도 변경하지 않았습니다. versionId가 있는 추적 orphan의 현재 관측 버전은 두 14일 조건을 충족한 뒤 삭제됩니다. 비버전/버전 중지 객체의 자동 삭제와 HEAD에 보이지 않는 비현재 버전/삭제 마커의 실제 보관 한계는 구현 공백·운영 조건입니다. 이전 버전 전체를 열거하거나 일괄 삭제하지 않습니다.

[AWS DeleteObject 공식 문서](https://docs.aws.amazon.com/AmazonS3/latest/API/API_DeleteObject.html)에 따르면 versionId 지정은 해당 버전만 영구 삭제합니다. 현재 버전을 삭제하면 이전 버전이 다시 현재로 보일 수 있으므로 tombstone으로 다시 관측하지만, 삭제 마커 뒤의 비현재 버전은 HEAD로 관측되지 않을 수 있습니다. [버전 삭제와 lifecycle 문서](https://docs.aws.amazon.com/AmazonS3/latest/userguide/DeletingObjectVersions.html)의 `NoncurrentVersionExpiration` 설정을 운영자가 확인해야 합니다. 일반 버킷의 If-Match는 지원되지만 LastModified/Size 삭제 조건은 directory bucket 전용입니다. 로컬 AWS SDK 2.29.52 Builder의 ifMatch/versionId는 javap로 확인했습니다. 지원하지 않는 조건부 삭제는 오류로 남고 무조건 삭제로 우회하지 않습니다. 유실 없는 분산 원자성이나 외부 put 강제 취소는 보장하지 않습니다.

## 과거 미추적 객체

기존 `visit-evidence/` 객체는 자동 삭제하지 않습니다. cutover 이전 업로드의 종료와 prefix 소유권을 확정하지 못한 객체는 inventory와 DB 참조 대조를 위한 관측 대상으로만 취급합니다. 이 변경에는 운영 bucket 조회·목록 삭제 명령이 없습니다.

## 검증과 원장 보존

단위 테스트는 업로드 전 등록, 활성 증빙 보호, 14일 경계, 만료/완료 경쟁의 상태 보호, 늦은 객체의 유예, 오래된 삭제 시도 거절과 조건부 삭제 요청을 다룹니다. PostgreSQL의 실제 별도 트랜잭션·SKIP LOCKED·migration 검증과 운영 fixture 검증은 별도로 수행해야 합니다. 이력을 영구 보존하므로 정리 완료 원장의 장기 압축 정책은 별도 검토 대상입니다.
