# 장소 추천 행동 전환 도메인 기준

## 1. 목적과 적용 범위

이 문서는 장소 추천 결과가 사용자 행동으로 이어지는 흐름의 용어, 책임, 전환 조건과
운영 확인 기준을 정의한다. 현재 구현과 맞지 않는 용어가 추천 품질 판단이나 운영 지표를
왜곡하지 않도록 하는 것이 목적이다.

이 문서는 현재 구현을 설명한다. 전환 조건, 기간, API, DB schema 또는 집계 방식을 바꾸지
않는다. 동작 변경은 API·Flyway·OpenAPI 영향과 함께 별도 이슈에서 검토한다.

관련 문서:

- [장소 추천 알고리즘 설계](../algorithm/place-recommendation.md)
- [Pingdom 2.0 리팩터링 범위와 성공 지표](pingdom-2.0-refactoring.md)
- [리팩터링 적용·복구 Runbook](../refactoring-rollout-runbook.md)

## 2. 용어

| 용어 | 현재 구현에서의 의미 | 비고 |
| --- | --- | --- |
| 추천 응답 | `GET /places/recommendations`가 반환한 장소 목록 | 응답마다 `recommendationRequestId`, `recommendationVersion`을 가진다. |
| 추천 노출 | 추천 응답에 포함된 장소 하나를 `place_recommendation_exposure`에 기록한 사실 | 응답 트랜잭션 커밋 후 비동기로 저장한다. 응답 반환과 저장 성공은 같은 보장이 아니다. |
| 추천 클릭 | 인증 사용자가 `POST /places/recommendations/click`으로 장소 선택을 기록한 사실 | `placeId`, `recommendationVersion`, `requestId`가 필요하다. |
| 행동 전환 | 최근 추천 클릭 이후 사용자가 한 북마크 또는 장소 게시글 좋아요 | 현재 전환 유형은 `BOOKMARK`, `LIKE` 두 가지다. |
| 전환 귀속 | 행동 전환을 가장 최근의 같은 사용자·장소 클릭과 추천 버전에 연결하는 처리 | 클릭 후 7일 이내일 때만 수행한다. |
| 실제 방문 | 사용자가 장소에 물리적으로 방문했다는 사실 | 현재 수집·검증·저장하지 않는다. 행동 전환과 동의어로 사용하지 않는다. |
| 추천 snapshot | 원천 로그와 장소 집계를 장소별·추천 버전별로 조회용 집계에 반영한 값 | 원천 로그가 기준이며, 필요 시 관리자 재동기화로 다시 계산한다. |

문서와 운영 화면에서는 `장소 방문 전환` 대신 `추천 행동 전환`을 사용한다. 과거 코드와
테이블의 `conversion` 명칭은 호환성을 위해 유지하되, 실제 방문 측정으로 해석하지 않는다.

## 3. 책임 경계와 데이터 소유

| 책임 | 소유 모듈 | 현재 처리 |
| --- | --- | --- |
| 추천 응답·노출·클릭·전환·snapshot | `place` | 추천 결과를 만들고 노출·클릭·전환 원천 로그와 집계를 관리한다. |
| 북마크 행동 | `place` | 북마크 생성 후 전환 자격을 확인한다. |
| 게시글 좋아요 행동 | `engagement` | 좋아요 생성 후 연결된 장소의 전환 자격을 확인한다. |
| 추천 성과 조회·snapshot 재동기화 | `moderation` | 관리자용 노출·클릭·전환 지표를 조회하고 원천 로그 기준으로 snapshot을 재생성한다. |

`engagement`가 좋아요 처리 중 `place`의 전환 기록 서비스를 직접 호출하는 것은 현재 구현의
전환 경로다. 이를 이벤트로 바꾸거나 책임을 이동하는 작업은 이 문서 범위에 포함하지 않는다.

## 4. 전환 상태와 조건

아래 상태는 사용자·장소·행동의 추적 모델이며, 별도의 상태 enum이나 하나의 상태 컬럼으로
저장하지 않는다. `미전환`, `전환 가능`, `전환 기간 만료`는 행동 시점에 계산되는 논리 상태다.

| 상태 | 진입 조건 | 다음 상태·처리 | 저장 기준 |
| --- | --- | --- | --- |
| 추천 응답됨 | 추천 목록이 반환됨 | 비동기 노출 저장을 요청한다. | 응답의 `recommendationRequestId` |
| 노출 기록됨 | 비동기 listener가 성공 | 클릭 여부와 관계없이 노출 원천 로그와 snapshot을 증가시킨다. | `place_recommendation_exposure` |
| 클릭 기록됨 | 인증 사용자의 클릭 요청과 존재하는 장소 | 같은 사용자·장소의 가장 최근 클릭이 이후 행동 전환의 후보가 된다. | `place_recommendation_click` |
| 전환 가능 | 행동 시점에 최근 클릭이 7일 이내 | 북마크 또는 좋아요 유형별 전환 기록을 시도한다. | 별도 상태 없음 |
| 북마크 전환 기록됨 | 전환 가능 상태에서 북마크 생성 | 해당 장소·추천 버전 snapshot의 북마크 전환 수를 증가시킨다. | `place_recommendation_conversion` (`BOOKMARK`) |
| 좋아요 전환 기록됨 | 전환 가능 상태에서 장소 게시글 좋아요 생성 | 해당 장소·추천 버전 snapshot의 좋아요 전환 수를 증가시킨다. | `place_recommendation_conversion` (`LIKE`) |
| 미전환 또는 기간 만료 | 최근 클릭이 없거나 7일을 넘김 | 행동은 정상 처리하지만 전환 원천 로그를 만들지 않는다. | 별도 상태 없음 |

전환은 동일한 `(user_id, place_id, conversion_type)` 조합마다 한 번만 기록한다. 따라서 한
사용자는 같은 장소에 대해 `BOOKMARK`와 `LIKE`를 각각 한 번 기록할 수 있지만, 같은 유형을
다시 수행해도 새 전환 기록은 생기지 않는다. 북마크 삭제나 좋아요 취소는 이미 기록된 행동
전환과 전환 snapshot을 감소시키지 않는다.

## 5. 귀속 규칙과 예외

1. 전환 기록은 사용자와 장소가 같은 가장 최근 클릭을 찾고, 그 클릭이 현재 시각 기준 7일
   이내일 때만 만든다.
2. 전환은 찾은 클릭의 `recommendationVersion`에 귀속한다. 노출 로그의 `requestId`와 클릭
   요청의 `requestId` 관계는 현재 검증하지 않는다.
3. 클릭 API는 요청의 `requestId` 재사용을 사용자 단위로 거부하지만, 해당 `requestId`가 실제
   추천 응답·노출과 연결되는지는 검증하지 않는다.
4. 추천 노출 저장은 커밋 후 별도 트랜잭션에서 실행된다. 저장 실패는 추천 응답을 실패시키지
   않고 오류 로그만 남긴다. 실패한 노출 원천 로그는 snapshot 재동기화로 새로 만들 수 없다.
5. 장소가 없으면 클릭을 기록하지 않는다. 북마크 또는 좋아요 자체의 기존 유효성 검사는 각
   행동 유스케이스가 먼저 수행한다.

이 규칙 때문에 현재 전환 지표는 노출-클릭-행동의 완전한 세션 퍼널이나 실제 방문 지표가
아니다. 추천 후 행동을 운영상 비교하기 위한 귀속 지표로만 사용한다.

## 6. 지표와 조회 해석

원천 데이터는 `place_recommendation_exposure`, `place_recommendation_click`,
`place_recommendation_conversion`이며, 조회 성능을 위해 장소별 및 장소·추천 버전별
snapshot을 사용한다.

관리자 성과 조회의 현재 비율은 다음과 같다.

```text
CTR = clickCount / exposureCount
bookmarkConversionRate = bookmarkConversionCount / exposureCount
likeConversionRate = likeConversionCount / exposureCount
totalConversionRate = (bookmarkConversionCount + likeConversionCount) / exposureCount
```

- 전환율의 분모는 클릭 수가 아니라 노출 수다.
- 한 사용자·장소에서 두 전환 유형을 모두 기록할 수 있으므로 `totalConversionRate`는 순사용자
  기준 퍼널 전환율이 아니며 100%를 넘을 수 있다.
- 추천 랭킹에 쓰는 전환 점수는 관리자 조회의 `totalConversionRate`와 다르다. 좋아요 전환에
  0.60 가중치를 적용하고, 표본 수 기반 smoothing과 신뢰도 보정을 거친다.
- 기간·추천 버전·장소 병합 이후 지표가 의심되면 원천 로그와 snapshot을 대조한다. snapshot은
  집계 결과일 뿐 전환 원천 사실을 대신하지 않는다.

## 7. 공개 계약과 변경 이력

현재 문서화 작업은 다음 공개 계약을 바꾸지 않는다.

| 계약 | 현재 기준 | 이번 문서 작업 |
| --- | --- | --- |
| App API | `GET /places/recommendations`, `POST /places/recommendations/click`와 v1 호환 경로 | 요청·응답·경로 변경 없음 |
| 관리자 API | 추천 성과 조회와 `POST /admin/places/recommendation-snapshots/resync` | 요청·응답 변경 없음 |
| DB schema | 추천 노출·클릭·전환 원천 테이블과 snapshot, 기존 Flyway migration | migration 추가·수정 없음 |
| OpenAPI baseline | `src/test/resources/openapi-baseline` | 기준 스펙 갱신 없음 |

향후 실제 방문 신호를 도입하거나 클릭 기준 전환율로 지표를 변경할 경우에는 API·데이터
보존 기간·중복 기준·재집계·OpenAPI·Flyway 영향과 복구 경로를 별도 이슈에서 함께 결정한다.

## 8. 운영 확인과 복구

1. 추천 지표가 비정상적이면 먼저 해당 요청의 `X-Request-Id`, 추천 응답의
   `recommendationRequestId`, 추천 버전, 장소 ID를 보존한다.
2. 애플리케이션 로그에서 비동기 노출 저장 실패 여부를 확인한다. 노출 원천 로그가 없으면
   추천 응답은 성공했더라도 해당 노출은 지표에 포함되지 않는다.
3. 원천 노출·클릭·전환 로그와 장소별·추천 버전별 snapshot을 비교한다. 원천 로그는 있는데
   snapshot만 다르면 관리자 `POST /admin/places/recommendation-snapshots/resync`로 재집계할 수
   있다.
4. 원천 노출 로그 자체가 누락된 경우 재동기화는 누락을 복원하지 않는다. 원인과 영향 범위를
   기록하고, 동일 요청을 임의로 재생성하지 않는다.
5. 재동기화 후 성공·실패 metric과 로그를 확인하고, 남은 차이는 별도 이슈로 추적한다.
