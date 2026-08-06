# #550 공백 포함 중복 패키지 디렉터리 정리 분석

## 1. 문서 목적

이 문서는 공백이 포함된 중복 패키지 디렉터리를 정리하기 전에 현재 호출 관계,
데이터·공개 계약 영향, 호환 계층과 롤백 지점을 고정한다. 이 작업의 완료는 패키지
경로를 무조건 이동하는 것이 아니라, 실제 기준선에 존재하는 중복을 증명하고 안전한
이관 순서를 남기는 것이다.

기준선은 `develop`의 `cb126e98`이다. 분석 결과 현재 Git 기준선에는 경로명 또는 Java
package 선언에 공백이 포함된 디렉터리가 발견되지 않았다. 따라서 이번 분석 커밋에서는
Java 소스나 migration을 임의로 이동·삭제하지 않는다. 공백 디렉터리가 다른 작업 트리,
IDE 생성물 또는 별도 커밋에만 존재한다면 해당 기준선을 확보한 뒤 별도 이관 작업으로
진행해야 한다.

## 2. 확인 결과

| 확인 대상 | 결과 | 판단 |
| --- | --- | --- |
| `src/main/java`·`src/test/java` 경로의 공백 | 없음 | 현재 작업 트리에서 삭제·이동할 디렉터리 없음 |
| Java package 선언과 물리 경로 | 불일치 없음 | 컴파일·스캔 기준의 즉시 위험 없음 |
| Git 전체 이력의 공백 경로 | 확인되지 않음 | 과거 경로를 복구하거나 이름으로 추정하지 않음 |
| Flyway migration 파일 | 패키지 경로와 무관 | 이번 분석 변경으로 DB 변경 없음 |
| OpenAPI baseline | Controller package 경로와 별도 계약 | package 이동만으로 API 계약을 바꾸지 않음 |

확인 명령은 다음과 같다.

```bash
git ls-tree -r --name-only HEAD | rg '[[:space:]]'
find src/main/java -type f -name '*.java' -print0 | xargs -0 awk '/^package / { ... }'
```

두 검사는 각각 Git이 추적하는 공백 경로와 Java 선언·물리 경로의 불일치를 확인한다.
분석 시점에 두 검사에서 정리 대상이 나오지 않았다는 사실을 변경 범위의 근거로 삼는다.

## 3. 호출 관계와 소유 경계

현재 모듈 구조는 `com.typenull.pingdom.<module>`을 최상위 소유 경계로 사용한다.
패키지를 이동할 때는 기술 계층만 맞추지 말고 다음 호출 방향을 보존한다.

```text
HTTP Controller
  -> Application Service / Query Service
    -> Domain policy / Entity
      -> Repository interface
        -> Infrastructure implementation
```

장소 영역의 대표 흐름은 다음과 같다.

| 흐름 | 현재 진입점 | 내부 호출 | 패키지 정리 시 확인할 것 |
| --- | --- | --- | --- |
| 장소 목록·상세·추천 | `place.api` 및 `LegacyPlaceCompatController` | `PlaceQueryService`, `MapPlaceService`, 추천 Query/Click Service | 구 경로 adapter가 동일한 유스케이스를 계속 호출하는지 확인 |
| 장소 북마크 | `place.api` 및 `LegacyBookmarkCompatController` | `PlaceQueryService`, `MapBookmarkService` | 구 경로의 인증 실패와 응답 형식을 보존 |
| 게시글 생성·수정 | `post.api.PostCommandController` | 게시글 Command 흐름, 이미지 저장 | `/post/*` deprecated 경로를 신규 `/posts/*`와 분리 유지 |
| 관리자 장소 운영 | `moderation.api` | 관리자 Service와 `place` 조회·도메인 모델 | 관리자 권한과 감사 이력 호출 경계를 유지 |

`LegacyPlaceCompatController`, `LegacyBookmarkCompatController`와 게시글의 deprecated
메서드는 중복 코드가 아니라 공개 v1 경로를 유지하는 호환 계층이다. 새 패키지로 이동할
때 이 클래스를 삭제하거나 `@Hidden`을 제거하는 것은 별도 API 변경으로 취급한다.

## 4. 데이터 및 계약 영향

### 4.1 데이터베이스

- Java package rename 또는 디렉터리 이동 자체는 테이블·컬럼·인덱스·Flyway 이력을
  변경하지 않는다.
- `MapPlace`, 북마크, 게시글, 관리자 중복 장소 도메인의 `@Table`·`@Column`·enum 매핑을
  함께 변경하지 않는다.
- Entity 이름 또는 JPA persistence 설정을 변경해야 한다면 package 정리와 분리하고,
  운영에 적용된 migration은 수정하지 않은 채 새 version migration 필요성을 별도로
  판단한다.
- Hibernate가 새 package를 스캔하는지 확인하기 전에는 이전 Entity 클래스를 삭제하지
  않는다. 이전 클래스와 새 클래스를 동시에 Entity로 남겨 중복 매핑을 만들지도 않는다.

### 4.2 API와 OpenAPI

- Java package 이동은 정상적으로 bean이 등록되면 HTTP 경로·요청·응답·오류 코드를
  바꾸지 않아야 한다.
- `Legacy*CompatController`의 경로는 `@Hidden`, `@Deprecated`, 기존 인증 처리를
  유지한다.
- Controller 이름이나 package 이동을 이유로 OpenAPI group에서 endpoint가 사라지거나
  노출되는지 확인한다.
- API 관련 파일을 변경한 경우에만 `exportOpenApiSpecs`와
  `verifyOpenApiContract`를 실행하고, 의도하지 않은 baseline diff를 허용하지 않는다.

## 5. 단계적 이관 및 하위 호환 전략

실제 공백 디렉터리가 확인될 때의 이관 순서는 다음과 같다.

1. **Inventory**: 공백 경로, Java package 선언, import, Spring component scan, 테스트와
   빌드 설정의 참조를 고정한다.
2. **Target 결정**: 최상위 모듈과 `api/application/domain/infrastructure` 책임에 맞는
   정상 패키지를 정하고, 클래스별 이전·유지·삭제 사유를 기록한다.
3. **Move**: IDE 또는 `git mv`로 소스와 테스트를 함께 이동하고 package/import를 갱신한다.
   이 단계에서는 API 경로, Bean 이름, Entity table mapping, migration을 변경하지 않는다.
4. **Compatibility 확인**: legacy controller와 deprecated endpoint가 새 Service를
   호출하는지, 인증·오류 응답·OpenAPI group이 이전과 같은지 검증한다.
5. **Contract 검증**: 단위·통합 테스트와 OpenAPI 계약을 통과시킨 뒤 변경된 패키지의
   직접 참조가 남아 있지 않은지 재검색한다.
6. **Contract 정리**: 사용하지 않는 호환 adapter를 제거하려면 클라이언트 전환 증거와
   별도 승인 후 별도 커밋·배포로 처리한다. 패키지 이동 커밋에 섞지 않는다.

하위 호환의 기준은 “이전 package를 계속 제공한다”가 아니라 “기존 공개 API와 저장
계약을 계속 제공한다”이다. 내부 package를 외부에서 직접 참조하는 소비자가 확인되면
adapter 또는 deprecation 기간을 먼저 마련한다.

## 6. 롤백 지점과 판단

| 단계 | 롤백 지점 | 확인 신호 | 조치 |
| --- | --- | --- | --- |
| 분석 | 문서 커밋 이전 | 기준선·대상 목록이 불명확함 | 코드 이동을 시작하지 않고 조사 보류 |
| package 이동 | 이동 커밋 직후 | 컴파일 실패, Spring bean 미등록, component scan 누락 | 이동 커밋을 되돌리고 이전 package 기준으로 복구 |
| API 검증 | 테스트·OpenAPI 전 | 기존 경로 404, 인증·오류 응답 변화, baseline 비호환 | 호환 controller와 이전 호출 흐름 복구 |
| 배포 | 새 애플리케이션 적용 후 | readiness 실패, 요청 오류 증가 | DB를 되돌리지 않고 이전 검증 이미지로 복귀 |
| DB 영향 발견 | migration 또는 데이터 변경이 포함된 경우 | Flyway 실패, schema 불일치, 데이터 정합성 저하 | 적용 이력과 실제 schema를 확인한 뒤 DB 백업·복구 절차 판단 |

이번 분석 커밋에는 DB 변경이 없으므로 정상적인 롤백 대상은 문서 파일과 커밋이다.
실제 package 이동에서 DB 영향이 발견되면 패키지 정리 커밋을 중단하고 migration·백업
검토를 별도 이슈로 분리한다. 이미 적용된 Flyway 파일을 수정하거나 `flyway repair`로
실패를 숨기지 않는다.

## 7. 검증 계획과 완료 기준

실제 이동 커밋에서 다음을 실행한다.

```bash
./gradlew test
./gradlew verifyOpenApiContract
git diff --check
```

추가로 다음 검색 결과가 0건이어야 한다.

- 공백이 포함된 Java source/package 경로
- 이전 package를 참조하는 import 또는 fully-qualified name
- 중복 Bean 이름·Entity 매핑
- 의도하지 않은 OpenAPI baseline 변경

이번 분석 하위 이슈의 완료 기준은 다음과 같다.

- 현재 기준선에 공백 경로가 없다는 사실과 조사 범위가 문서화됨
- 실제 이동 시 호출 관계와 호환 controller의 역할이 명시됨
- DB·Flyway·OpenAPI 영향 판단 기준과 별도 검토 조건이 명시됨
- 컴파일·계약·운영 실패별 롤백 지점이 명시됨
- 실제 코드 이동을 추정으로 수행하지 않음

