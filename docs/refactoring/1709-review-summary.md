# #1709 전체 소스 검토 결과

[이슈 #1709](https://github.com/Type-Nu11/pingdom-api/issues/1709)의 테스트 이름·한국어 설명 정리를 수행했다. 기준 커밋은 `0388d61b90344c880f4e720d3163b36ce71e3474`, 작업 브랜치는 `refactor/1709-test-names-and-code-comments`이다.

운영 Java 1,267개와 테스트 Java 402개, 총 **1,669개 파일을 검토**했다. 992개 파일을 수정했고 기존 설명이 충분한 677개는 유지했다. 미검토 파일은 없다. 테스트명은 335개 파일에서 1,297개를 정리했다.

파일별 검토 근거·현재 메서드 선언·이름 변경 전후는 [전체 검토 목록](1709-review-inventory.json), 실행 클래스와 점검 수치는 [검증 기록](1709-validation.json)에 있다. 기존 동작의 의문점과 테스트 검증 한계는 [후속 검토 항목](1709-follow-up-findings.md)으로 분리했다.

## 변경 기준과 도메인별 범위

테스트는 실제 조건과 assertion을 읽고 짧은 영어 이름과 annotation 위 한국어 Javadoc으로 역할을 분리했다. setup/teardown, fixture, 인자 공급자, 중첩·익명 클래스의 helper 및 명시적 생성자도 포함한다. 테스트 메서드와 helper를 합친 2,369개 선언 모두에 한국어 Javadoc이 연결되어 있다.

운영 코드는 책임과 업무 흐름, 입력 조건, 저장 순서, 실패 처리, 실제 보호 장치와 한계를 설명했다. 단순 accessor·위임·매핑에는 불필요한 장문을 추가하지 않았다. 독립 검토에서 발견한 잘못된 보장 표현을 수정하고, annotation 아래에 있던 Javadoc 188개를 실제 선언에 연결되도록 옮겼다.

- `verification`: 장소별 정책 우선순위, 증빙 검증·저장·S3 보상, 방문·Scout 상태 전이와 실제 검사 범위를 설명했다.
- `place`·`identity`: 추천 후보·개인화·노출·전환·snapshot, 장소 신청·승인·리뷰, 인증·계정·권한·상점 관리의 조건과 부작용을 보강했다.
- `moderation`·`engagement`·`post`: 관리자 권한, 신고·숨김·복구, 병합·감사·조회 범위와 집계 변경을 구분했다.
- `shared`·`notification`·`privacy`: JWT·rate limit, Outbox 선점·재시도, S3·알림의 외부 부작용과 DB 롤백 한계, 보관 기간·배치 경계를 설명했다.
- `analysis`·`consultation`과 상품·예약·결제 관련 도메인: 외부 응답 검증, 보고서 생성·보관, 업무 상태·권한·멱등 처리의 실제 책임을 정리했다.
- `architecture`·`integration`·`fixture`: reflection·DB·HTTP 검증과 정적 계약 fixture의 차이를 명시하고 현재 assertion이 입증하는 범위로 설명을 제한했다.

표의 변경 파일 수에는 주석만 수정한 파일도 포함한다. `integration`은 디렉터리 구분이며, 다른 도메인에도 통합 테스트가 있으므로 실행 분류를 뜻하지 않는다.

| 도메인/소스 묶음 | 운영 파일 | 테스트 파일 | 변경 파일 | 변경한 테스트명 |
| --- | ---: | ---: | ---: | ---: |
| analysis | 27 | 13 | 29 | 32 |
| application-root | 1 | 1 | 2 | 0 |
| architecture | 0 | 7 | 7 | 6 |
| availability | 12 | 3 | 7 | 17 |
| boost | 30 | 7 | 16 | 11 |
| campaign | 20 | 4 | 12 | 9 |
| community | 44 | 14 | 25 | 45 |
| consultation | 21 | 7 | 12 | 19 |
| engagement | 32 | 3 | 12 | 7 |
| fixture | 0 | 29 | 29 | 18 |
| identity | 172 | 36 | 122 | 90 |
| integration | 0 | 43 | 43 | 303 |
| menu | 23 | 7 | 15 | 19 |
| merchant | 3 | 2 | 4 | 1 |
| moderation | 244 | 28 | 86 | 52 |
| notification | 43 | 14 | 32 | 32 |
| offer | 24 | 7 | 16 | 30 |
| payment | 26 | 5 | 12 | 14 |
| place | 308 | 97 | 251 | 347 |
| post | 17 | 4 | 11 | 15 |
| privacy | 16 | 3 | 10 | 8 |
| product | 8 | 3 | 6 | 3 |
| reservation | 18 | 3 | 7 | 11 |
| shared | 88 | 33 | 113 | 60 |
| verification | 90 | 29 | 113 | 148 |
| 합계 | 1267 | 402 | 992 | 1297 |

## 검증 결과

기준 커밋과 현재 Java 소스를 문자열·문자 리터럴·text block을 보존한 토큰으로 비교했다. 운영 소스는 주석·공백 외 차이가 없고 테스트는 기록된 메서드 식별자만 달라졌다. assertion, fixture, 문자열, annotation, tag, disabled 상태, 운영 코드 이름·쿼리·트랜잭션은 유지했다.

JDK AST로 테스트 선언을 전후 대조한 결과 일반 `@Test` 1,461개와 `@ParameterizedTest` 27개, 전체 메서드·생성자 2,369개가 각각 동일하다. 한국어 Javadoc 누락·중복 signature·구문 오류는 0개다. `MethodSource` 9개는 모두 명시적 공급자에 연결되며 이름 생략 연결은 없다. 테스트명 기반 실행 순서 설정은 발견되지 않았다.

직접 호출, reflection, 문자열, Gradle/CI 필터, 추적 중인 스크립트·문서의 이름 참조를 검색했다. 남은 이전 이름 후보 1개는 다른 클래스에 그대로 유지한 정상 메서드 선언이며 끊어진 참조가 아니다. 검토 목록에 기록한 변경 전 이름은 추적 목적의 데이터다.

`compileTestJava`와 선택한 단위 테스트를 묶음별로 실행했다. 총 **56개 클래스, 334개 테스트가 통과**했다. 파라미터별 실행 건수를 포함하며 선언 수와 다르다. 모든 주석 수정 후 `compileTestJava`도 다시 통과했고, 운영 선언의 Javadoc 배치 오류 0개와 `git diff --check` 통과를 확인했다.

| 실행 범위 | 클래스 | 실행 건수 | 결과 |
| --- | ---: | ---: | --- |
| verification 정책·증빙 대표 단위 테스트 | 2 | 6 | 통과 |
| verification 저장·파일·상태 전이 단위 테스트 | 10 | 47 | 통과 |
| verification 서비스·DTO·도메인 단위 테스트 | 13 | 81 | 통과 |
| shared 보안·rate limit·Outbox 및 place API·도메인 단위 테스트 | 19 | 74 | 통과 |
| 아키텍처·MethodSource·응답 검증·Kakao/Naver 지역 해석기 단위 테스트 | 12 | 126 | 통과 |

마지막 선택 실행 명령은 다음과 같다. 임시 init script는 `allprojects { layout.buildDirectory.set(file('/private/tmp/pingdom-1709/build')) }`만 설정하며 저장소 빌드 설정은 수정하지 않았다.

```sh
./gradlew --no-daemon \
  -I /private/tmp/pingdom-1709/isolated-build.gradle \
  --project-cache-dir /private/tmp/pingdom-1709/gradle-cache \
  compileTestJava test \
  --tests 'com.typenull.pingdom.architecture.*' \
  --tests '*ReportModerationPermissionTest' \
  --tests '*ProviderEnvelopeValidatorTest' \
  --tests '*KakaoPlaceAdministrativeRegionResolverTest' \
  --tests '*NaverPlaceAdministrativeRegionResolverTest' \
  --tests '*NaverPlaceAdministrativeRegionResolverCacheTest'
```

기존 `build/classes`의 잔여 class 파일을 읽는 Gradle 해시 계산이 정체되어 한 번 중단했다. 기존 산출물을 보존한 채 위 임시 빌드·캐시 경로로 전환해 성공했다. 테스트 assertion 실패는 아니었다. 기존 deprecated/unchecked 경고는 이번 작업에서 수정하지 않았다.

통합·Docker/Testcontainers·전체 suite·장시간 검증은 이슈의 승인 범위에 따라 실행하지 않았다. 통합 테스트도 소스 컴파일과 이름·본문 보존은 확인했지만 DB·HTTP 동작을 실행 검증했다고 주장하지 않는다. 후속 결함 후보도 정적 관찰이며 별도 재현이 필요하다.

## 커밋 구성

후속 사용자 요청에 따라 운영 코드와 테스트를 분리하고, 같은 도메인의 관련 파일을 묶어 소스 변경 660개와 검토 문서 1개, 총 661개의 커밋으로 구성한다. 각 소스 커밋은 1~2개 파일의 실제 변경을 포함하며 빈 커밋이나 파일 내부의 인위적인 분할은 사용하지 않는다.

운영 주석과 검토 문서는 `docs :`, 테스트 이름·주석은 `refactor :` 형식의 한국어 메시지를 사용한다. 원격 push와 PR 생성은 이번 요청에 포함하지 않는다.
