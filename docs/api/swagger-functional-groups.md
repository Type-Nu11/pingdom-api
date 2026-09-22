# Swagger 기능별 분류

기존 `app`, `merchant`, `admin`, `common`, `consulting`과 `/v3/api-docs/{group}` 주소를 유지한다.
그룹 소속은 `@ApiAudience`, 화면의 기능별 분류는 `@Tag`로 지정한다. 두 메타데이터 모두 문서에만 사용한다.

## 분류 규칙

- Controller에 기본 `@ApiAudience`와 `@Tag`를 선언한다. 메서드 지정은 클래스 지정보다 우선한다.
- `@Tag.name`에는 `SwaggerTagCatalog`의 상수를 사용한다. 분류 설명과 업무 흐름 순서는 해당 catalog에서 관리한다.
- 하나의 operation에는 최종 태그 하나만 남긴다. Springdoc가 합친 클래스 태그와 메서드 태그를 그대로 노출하지 않는다.
- Controller 단위로 새 분류를 만들기보다 같은 사용자 작업에 필요한 API를 함께 묶는다.
- Swagger UI는 분류를 접은 상태로 시작한다. `Filter by tag`에서 기능명으로 찾은 뒤 분류를 펼친다.
- `VoiceAiSessionController`는 기존처럼 전체 문서의 `Voice AI`에만 표시한다. 다섯 그룹으로의 편입은 별도 결정이 필요하다.

## #1708 적용 기준

| 그룹 | operation 수 | 기능 분류 수 |
| --- | ---: | ---: |
| app | 103 | 18 |
| merchant | 83 | 13 |
| admin | 145 | 19 |
| common | 14 | 6 |
| consulting | 1 | 1 |

전체 문서는 352개 operation이다. 그룹 미지정 operation도 포함하므로 위 다섯 그룹의 합과 다르다.
각 operation의 확정된 `HTTP method + path → 기능명` 매핑은 `src/test/resources/openapi-baseline/{group}.json`의 `paths` 아래 `tags`에 기록된다.

복합 Controller의 메서드별 분류는 다음과 같다.

| Controller | 기본 분류 | 메서드별 분류 |
| --- | --- | --- |
| `UsersController` | 내 정보·계정 연동 | `/users/me/travel-purposes`의 GET·PUT은 여행 |
| `PlaceController` | 장소 탐색 | 개별 장소의 카드·방문 판단·운영 공지·상세·미디어는 장소 상세·메뉴 |
| `AdminPostController` | 신고·이의신청 | `/admin/posts/s3/**`는 Outbox·스토리지 운영 |

## 검증 및 baseline 갱신

1. 변경 전 현재 소스에서 `./gradlew exportOpenApiSpecs`를 실행하고 `build/openapi/*.json`을 별도 보관한다. 저장된 baseline은 현재 소스보다 오래됐을 수 있다.
2. 변경 후 같은 명령으로 생성한다. 그룹별 `HTTP method + path` 집합을 비교하고, 문서 최상위 `tags`와 각 operation의 `tags`만 제외한 JSON이 동일한지 확인한다. 이 비교에는 요청·응답, `operationId`, 보안 명세가 포함된다.
3. 검증된 생성 문서 6개만 `src/test/resources/openapi-baseline/`에 반영한다. 기존 baseline과 현재 소스 간 차이를 이번 변경의 API 동작 변경으로 해석하지 않는다.
4. 다음 범위의 테스트를 실행한다. 통합 테스트 실행은 프로젝트 검증 정책에 따른다.

```sh
./gradlew test --tests '*SpringdocProfileConfigurationTest' --tests '*SwaggerFunctionalGroupingTest'
./gradlew integrationTest --tests '*OpenApiDocumentationValidationTest'
```

`SwaggerFunctionalGroupingTest`는 소속 판별, 메서드 우선순위, 단일 태그, 설명·순서와 미지정 그룹 보존을 검증한다.
`OpenApiDocumentationValidationTest`는 실제 생성 문서의 그룹별 operation 집합과 개별 분류를 baseline에 대조하고, 허용된 분류·중복·누락·표시 순서를 검증한다.

#1708에서는 단위 테스트 9개와 문서 통합 테스트 35개를 통과했다. 변경 전후 생성 문서는 태그를 제외하고 완전히 동일했다.
로컬 H2 환경의 실제 Swagger UI에서 다섯 그룹 선택, 초기 접힘, 기능명 검색, 하위 API 펼침 및 화면 렌더링을 확인했다.
`openapi-export` 프로필은 기본적으로 UI가 비활성화되어 있으므로 UI 확인 시 로컬 실행에 한해 `springdoc.swagger-ui.enabled=true`로 재정의한다.
