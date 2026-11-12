# 입지 분석 보고서 목록 페이지 전환

이슈 #1848은 `GET /analysis/reports`를 전체 JSON 배열에서 크기가 제한된 요약 페이지 객체로 변경합니다. 클라이언트는 배열 대신 `response.reports`를 읽고 `hasNext`가 참일 때 다음 페이지를 요청해야 합니다.

## 요청과 응답

- 필수 `email`은 기존 계정 이메일 검증 입력입니다. 조회 소유권은 #1833의 인증 사용자 ID로 제한하며, 소유자 ID가 없는 기존 보고서는 제외합니다.
- `page`는 1부터 시작하며 기본 1, `limit`은 기본 20·최소 1·최대 100입니다. 1 미만의 page, 범위 밖 limit, DB offset의 int 한계를 넘는 조합은 400 `ANALYSIS_REPORT_INVALID_PAGINATION`으로 거절합니다. 전체 페이지 수를 넘는 유효한 page는 빈 목록으로 응답합니다.
- 정렬은 `createdAt DESC, reportId DESC`이며 외부 정렬 입력은 받지 않습니다.
- 응답은 `reports/page/limit/totalCount/totalPages/hasNext`입니다. 빈 결과 또는 범위 밖 페이지도 빈 reports와 전체 개수를 반환합니다.
- 요약 항목의 기존 필드와 다운로드·HTML 경로는 유지하고 PDF·HTML 본문은 조회하지 않습니다.

```json
{
  "reports": [],
  "page": 1,
  "limit": 20,
  "totalCount": 0,
  "totalPages": 0,
  "hasNext": false
}
```

## 클라이언트 확인과 전환

2026-11-12 공식 원격 저장소의 다음 소스 스냅샷에서 `analysis/reports`와 보고서 관련 타입을 검색했습니다.

| 저장소 | 브랜치·commit | GET 목록 소비자 |
| --- | --- | --- |
| pingdom-consulting | main · `93b1c2a93f63cf8f963e34714a9eb33f8036fbd1` | 미발견. `locationAnalysisReportApi.ts`는 생성 POST, `locationAnalysisReportStorage.ts`는 IndexedDB 사용 |
| pingdom-admin | release · `b6ad7d914b2b9c8674812fc9d615ebadacc6f631` | 미발견. 관련 경로는 OpenAPI 문서에만 존재 |
| pingdom-app | dev · `93d52c2f7675c7d3414baf32473b9038b6ff1edb` | 미발견 |

이는 현재 공식 소스의 정적 조사 결과입니다. 배포된 과거 번들·별도 외부 클라이언트의 실제 사용 여부까지 입증하지는 않습니다.

기존 GET 배열 응답을 사용하는 클라이언트가 있으면 서버 적용 전에 페이지 객체를 읽도록 전환해야 합니다. 배포·롤백은 응답형에 맞는 클라이언트와 함께 수행합니다. 무제한 레거시 조회 경로를 추가하거나 기존 배열을 설명 없이 첫 페이지로 잘라 반환하지 않습니다.

## 정렬과 동시 변경

보조 ID 정렬은 고정 데이터에서 같은 생성 시각의 페이지 순서를 안정화합니다. offset 페이지 탐색 중 다른 요청이 보고서를 생성·삭제하면 이미 읽은 위치가 달라질 수 있으므로 전체 탐색의 스냅샷 일관성까지 보장하지 않습니다. 해당 보장이 필요한 경우 cursor 기반 별도 계약을 검토합니다.

## 2026-11-12 완료 검증

검증 기준은 `92afc490c04849583874ca097ad03fd43b250fe7`이며 JDK 21.0.13에서 다음 직접 관련 클래스만 실행했습니다.

```sh
./gradlew --no-daemon --max-workers=2 --console=plain \
  test --tests com.typenull.pingdom.analysis.api.LocationAnalysisControllerTest \
       --tests com.typenull.pingdom.analysis.application.LocationAnalysisReportArchiveServiceTest \
  integrationTest --tests com.typenull.pingdom.analysis.infrastructure.LocationAnalysisReportPaginationIntegrationTest
```

Controller 11건·ArchiveService 19건·H2 pagination 통합 2건, 총 32건이 통과했습니다. 실제 저장소 fixture는 0/1/101개, 같은 생성 시각의 100개+1개 탐색, 범위 밖 빈 페이지, 타인 및 소유자 없는 행 제외를 확인했습니다. SQL `StatementInspector`로 목록 SELECT에 PDF·HTML 컬럼이 없는 것도 확인했습니다.

실제 이메일 접근 정책을 연결한 목록 403 회귀를 추가한 뒤 새 메서드와 직접 정책 테스트 3건만 추가 실행했습니다.

```sh
./gradlew --no-daemon --max-workers=2 --console=plain test \
  --tests com.typenull.pingdom.analysis.api.LocationAnalysisControllerTest.rejectsAnotherEmailBeforeListingReports \
  --tests com.typenull.pingdom.analysis.application.LocationAnalysisReportAccessPolicyTest
```

추가 4건도 통과하여 고유 검증 케이스는 총 36건이며 실패·오류·건너뜀은 0건입니다. 타인 이메일 요청은 실제 접근 정책과 공통 오류 변환을 거쳐 403 `ANALYSIS_REPORT_FORBIDDEN`을 반환하고 보관 조회 서비스에는 도달하지 않습니다. 고정 인증 사용자 resolver를 쓰는 Controller 테스트로, JWT 필터 전체를 새로 검증한 것은 아닙니다.

일반 CI가 제외하는 `integration` 태그의 pagination 2건은 위 명령으로 직접 실행했습니다. 실제 exporter와 여섯 OpenAPI 계약 검증은 동일 운영 tree의 최종 PR #1924 [CI 로그](https://github.com/Type-Nu11/pingdom-api/actions/runs/43180426270/job/131054762275)에서 성공했습니다. 현재 후속 변경은 문서·회귀 테스트뿐이며 API 구현과 OpenAPI 스키마를 다시 바꾸지 않았습니다.

이 결과는 고정 H2 fixture의 제한·정렬·projection 및 직접 API 계약 검증입니다. 운영 PostgreSQL 지연이나 동시 생성·삭제 중 offset 탐색의 스냅샷 일관성을 보장하지 않습니다. 전체 테스트 스위트는 실행하지 않았습니다.
