# 네이버 검색 OpenAPI 계약 검증

관련 이슈: [#1750](https://github.com/Type-Nu11/pingdom-api/issues/1750)

주소·업체명 검색의 중첩 `Item` 이름 충돌과 외부 서비스 오류 응답 문서를 수정한다.
검색 클라이언트, 외부 인증 설정, 좌표 변환, 응답 JSON 필드와 검색 결과 제한은 기존 구현을 유지한다.
주소 query의 `@Size`에는 최소 1자를 명시한다. 기존 `@NotBlank`도 빈 값을 거부하므로
허용되는 입력 범위는 같으며, Springdoc이 `minLength: 0`을 생성하던 문제를 방지한다.

## 생성 문서의 계약

두 경로의 공통 접두사는 `/users/me/merchant-place-applications`이다.

| 경로 | 후보 스키마 | 최대 개수 | 고유 필드 |
| --- | --- | --- | --- |
| `/naver-address-search` | `NaverAddressSearchItem` | 10 | nullable `postalCode` |
| `/naver-place-search` | `NaverPlaceSearchItem` | 5 | `name` |

후보에는 각각 `roadAddress`, `jibunAddress`, WGS84 `latitude`·`longitude`도 포함한다.
query는 필수이며 입력값 길이는 1~100자다. 앞뒤 공백을 `trim`한 검색어로 조회하고,
공백만 있는 입력은 허용하지 않는다. 400 응답의 `ErrorResponse`·`ValidationErrorResponse`
계약과 Bearer 인증 및 401·403 응답을 유지한다.

| 검색 | HTTP 상태 | 실제 오류 코드 |
| --- | --- | --- |
| 주소 | 429 | `NAVER_ADDRESS_SEARCH_RATE_LIMITED` |
| 주소 | 502 | `NAVER_ADDRESS_SEARCH_FAILED` |
| 주소 | 503 | `NAVER_ADDRESS_SEARCH_UNAVAILABLE` |
| 주소 | 504 | `NAVER_ADDRESS_SEARCH_TIMEOUT` |
| 업체명 | 502 | `NAVER_PLACE_SEARCH_FAILED` |
| 업체명 | 503 | `NAVER_PLACE_SEARCH_UNAVAILABLE` |

업체명 클라이언트는 외부 429·timeout도 일반 호출 실패로 변환하므로 별도 429·504 응답을 선언하지 않는다.
외부 오류 응답은 모두 `ErrorResponse`를 참조한다.

## 로컬 검증 결과 — 2026-10-01

Java 21과 임시 build/project-cache 경로에서 검증했다.

- `NaverSearchOpenApiContractTest`: merchant 및 전체 문서의 필드·nullable·배열 제한,
  성공 응답·query 제약·인증, 상태별 오류 스키마·코드의 6개 검증을 확인했다.
  최초 실행에서 query 최소 길이 관련 2개가 실패했고, `@Size(min = 1)` 수정 후 실패한 2개만 재실행해 통과했다.
  400 설명을 공통 계약과 도메인 오류 중심으로 정리한 뒤 오류 응답 관련 2개도 재검증했다.
- `exportOpenApiSpecs`: 생성 성공. 수정 전후 변경된 operation은 두 검색 경로뿐이다.
- `verifyOpenApiContractMerchant`: `compatible`.
- `verifyOpenApiContractOpenapi`: `incompatible`. 두 검색 operation은 각각 `compatible`이며,
  비호환 operation은 `/community/posts` POST, `/community/posts/{postId}/comments` POST,
  `/community/categories/{categoryId}/posts` GET이다.
- 수정 전 생성 문서와 원래 전체 기준 문서 비교도 `incompatible`이다. 기존 커뮤니티 계약 차이는 별도 정리가 필요하다.
  현재 Gradle task는 `--state` 결과가 `incompatible`이어도 정상 종료하므로 `BUILD SUCCESSFUL`만으로
  전체 계약 통과를 판단하지 않는다.
- 기준 문서 갱신은 두 검색 경로와 검색 응답·후보 스키마, 그에 종속된 공용 `Item` 처리만 포함한다.
  merchant의 공용 `Item`은 제거되고, 전체 문서의 `Item`은 남아 있는 트렌드 DTO로 선택된다.
  이전 예약·운영시간·커뮤니티 작업 등의 기준 문서 차이는 함께 갱신하지 않았다.

재현 명령은 다음과 같다. 전체 테스트와 실제 네이버 검색 통합 QA는 이 검증에 포함하지 않는다.

```bash
./gradlew integrationTest --tests com.typenull.pingdom.integration.swagger.NaverSearchOpenApiContractTest
./gradlew verifyOpenApiContractMerchant verifyOpenApiContractOpenapi
```

## 운영 반영 상태

검증 URL: [운영 merchant OpenAPI](https://www.typenull.xyz/v3/api-docs/merchant)

2026-10-01 11:06:39 KST 조회 결과 HTTP 200이지만, 두 후보는 여전히
`#/components/schemas/Item`을 참조하고 두 operation에는 200·400·401·403만 존재했다.
**운영 반영은 미완료이며 배포 후 검증이 필요하다.**

배포 후 같은 URL에서 후보의 서로 다른 참조와 필드·nullable·최대 개수,
query 제약, 위 표의 오류 응답·코드를 확인하고 배포 커밋과 조회 시각을 기록한다.
실검색 통합 QA는 [pingdom-admin #220](https://github.com/Type-Nu11/pingdom-admin/issues/220)에서 별도 추적한다.
