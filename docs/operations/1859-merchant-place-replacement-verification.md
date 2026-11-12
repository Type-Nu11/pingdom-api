# 점주 장소 교체 저장 정합성 검증 (#1859)

2026-11-12에 `92afc490c04849583874ca097ad03fd43b250fe7`의 차집합 교체 구현을 실제 PostgreSQL에서 검증했다. 이번 후속 변경은 테스트와 결과 기록이며 운영 저장 코드·잠금·스키마를 추가로 변경하지 않는다.

## 재현 조건

- JDK `21.0.13+9-LTS`, Docker 29.3.1, `postgis/postgis:16-3.4`의 실제 PostgreSQL 16.4.
- Flyway migration 149개 적용, 최종 버전 v177, Hibernate schema validation 사용.
- 활성 점주 2명, 장소 A/B/C, 기존 소유 매핑 A/B, B의 PUBLISHED 오퍼 1개.
- 요청은 동일 목록 A/B, C 추가 A/B/C, B 제거 A의 3종이다. 각 요청에 기본 UNMEASURED(0/0/0, 평가시각 없음)와 HEALTHY(93/4/2, 평가시각 있음)를 적용해 6조합을 구성한다.
- Clock은 `2026-08-31T03:00:00Z`로 고정하고 기존 매핑은 10일 전 생성했다. 각 fixture는 독립 커밋으로 준비한다. 교체 종료 후 새 트랜잭션에서 최종 매핑·품질·생성시각·오퍼 상태를 재조회한다.
- 컨테이너 전용 DB만 사용하며 각 케이스 전 초기화한다. 테스트 트랜잭션의 자동 rollback으로 커밋 오류를 가리지 않는다.

```sh
./gradlew postgresIntegrationTest \
  --tests com.typenull.pingdom.identity.application.service.merchant.MerchantOwnerPlaceReplacementPostgreSqlIntegrationTest \
  --no-daemon --max-workers=2 --console=plain
```

JDK 21과 Docker daemon이 필요하다. 이 클래스는 `postgres-integration` 태그만 사용하므로 기본 `test`와 기존 CI의 `postgresSmokeTest`에는 포함되지 않는다. 전체 테스트 스위트는 실행하지 않았다.

## 변경 전 6조합의 실제 결과

기준은 `60962db65448bd03b77939b375eb0b452c50f8ca`의 저장 흐름이다. 같은 테스트 DB·현재 runtime에서 원래 공개 메서드의 기존 엔티티 조회 → 제거 오퍼 종료 → 요청 장소 잠금/소유 확인 → 전체 bulk delete → 동일 ID builder의 saveAll/merge → 응답용 재조회 순서를 재현했다. 해당 entity와 오퍼 저장소는 기준 commit과 동일하며, 전체 삭제 저장소 메서드도 원래의 clear 없는 JPQL을 사용한다. fixture에서 무효 처리하는 권한·감사 호출은 legacy helper에서 생략했다.

| 품질 | 요청 | 이전 흐름의 트랜잭션 | 실제 최종 매핑 | B 오퍼 |
| --- | --- | --- | --- | --- |
| UNMEASURED | A/B | 커밋 | 없음 | PUBLISHED |
| UNMEASURED | A/B/C | 커밋 | C만 존재 | PUBLISHED |
| UNMEASURED | A | 커밋 | 없음 | CLOSED |
| HEALTHY | A/B | stale 예외로 rollback | 원래 A/B와 품질 보존 | PUBLISHED |
| HEALTHY | A/B/C | stale 예외로 rollback | 원래 A/B와 품질 보존, C 없음 | PUBLISHED |
| HEALTHY | A | stale 예외로 rollback | 원래 A/B와 품질 보존 | PUBLISHED |

원래 코드는 관리 중인 매핑을 남긴 bulk delete와 동일 ID의 새 builder merge를 혼용한다. 실제 미측정 조합은 유지 매핑이 복구되지 않은 채 커밋됐고, 측정 조합은 `ObjectOptimisticLockingFailureException`으로 트랜잭션 전체가 rollback됐다. SQL별 실행 경로 전체는 별도로 계측하지 않았으므로 ORM 내부 동작을 추가로 단정하지 않는다. **항상 품질이 기본값으로 커밋되는 문제**라고 표현하지 않으며, 이 fixture에서 확인한 두 실패 양상은 매핑 누락 커밋과 교체 요청 실패/rollback이다.

원시 결과는 실행 후 `build/reports/1859-legacy-place-replacement.json`에 생성된다. 예상된 stale 예외는 해당 케이스에서 명시적으로 검증하며 다른 예외를 성공으로 숨기지 않는다.

## 현재 차집합 구현의 결과

현재 구현의 6조합은 모두 커밋 후 요청과 같은 매핑을 반환했다.

| 요청 | 실제 최종 매핑 | 기존 품질·평가시각·생성시각 | C | B 오퍼 |
| --- | --- | --- | --- | --- |
| A/B | A/B | 모두 보존 | 없음 | PUBLISHED |
| A/B/C | A/B/C | A/B 모두 보존 | UNMEASURED, 새 생성시각 | PUBLISHED |
| A | A | A 보존 | 없음 | CLOSED |

위 표의 각 행을 UNMEASURED와 HEALTHY에서 각각 확인했다. 유지 매핑을 재생성하지 않고 추가·제거 대상만 바꾸므로 기존 품질과 생성시각을 보존했다.

추가 실제 DB 검증 3건도 통과했다.

- 감사 기록 실패: 매핑 추가·삭제와 오퍼 종료가 모두 rollback되어 원래 A/B와 품질 및 PUBLISHED 오퍼가 보존됐다.
- 두 점주의 동일 장소 동시 추가: 한 트랜잭션만 성공하고 다른 트랜잭션은 PLACE_ALREADY_ASSIGNED로 거절됐다.
- Claim이 장소 잠금 아래 소유권을 이전하는 동안 교체가 대기: 잠금 획득 뒤 재조회한 현재 소유권을 보존하고 다른 점주의 오퍼를 닫지 않았다.

최종 직접 실행은 기존 회귀 9건과 legacy 재현 6건, **총 15건 통과·실패/오류/건너뜀 0건**이었다. Gradle은 47초, JUnit 클래스는 22.887초였으며 이 값은 검증 소요 시간이고 API 성능 수치가 아니다.

## 검증 한계와 되돌리기

legacy 재현은 원래 교체 저장 순서의 비교이며 과거 서버 전체를 재배포한 결과가 아니다. 현재 schema와 runtime을 양쪽에서 동일하게 사용했다. 고정된 두 소유자와 제어한 잠금 순서의 경합을 검증했으며 모든 운영 동시 실행 조합, HTTP 인증·권한, 외부 장애 전체를 입증하지 않는다.

이번 변경은 검증 클래스와 문서만 되돌리면 된다. 운영 코드·DB migration·개인 데이터 변경이나 별도 전환 단계가 없으며 기존 차집합 구현은 그대로 유지한다.
