# 입지 분석 보고서 목록 페이지 전환

이슈 #1848은 `GET /analysis/reports`를 전체 JSON 배열에서 크기가 제한된 요약 페이지 객체로 변경합니다. 클라이언트는 배열 대신 `response.reports`를 읽고 `hasNext`가 참일 때 다음 페이지를 요청해야 합니다.

## 요청과 응답

- 필수 `email`은 기존 계정 이메일 검증 입력입니다. 조회 소유권은 #1833의 인증 사용자 ID로 제한하며, 소유자 ID가 없는 기존 보고서는 제외합니다.
- `page`는 1부터 시작하며 기본 1, `limit`은 기본 20·최소 1·최대 100입니다. 지원 범위를 넘는 페이지 또는 잘못된 크기는 400 `ANALYSIS_REPORT_INVALID_PAGINATION`으로 거절합니다.
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

2026-11-12 로컬 `pingdom-consulting/src/features/analysis/locationAnalysisReportApi.ts`는 보고서 생성 POST를 호출하고 `locationAnalysisReportStorage.ts`는 IndexedDB에 보관합니다. 해당 저장소와 `pingdom-admin`에서 GET 목록 소비자는 발견되지 않았습니다. 배포된 별도 앱·외부 클라이언트의 사용 여부는 이 확인 범위에 포함되지 않습니다.

기존 GET 배열 응답을 사용하는 클라이언트가 있으면 서버 적용 전에 페이지 객체를 읽도록 전환해야 합니다. 배포·롤백은 응답형에 맞는 클라이언트와 함께 수행합니다. 무제한 레거시 조회 경로를 추가하거나 기존 배열을 설명 없이 첫 페이지로 잘라 반환하지 않습니다.

## 정렬과 동시 변경

보조 ID 정렬은 고정 데이터에서 같은 생성 시각의 페이지 순서를 안정화합니다. offset 페이지 탐색 중 다른 요청이 보고서를 생성·삭제하면 이미 읽은 위치가 달라질 수 있으므로 전체 탐색의 스냅샷 일관성까지 보장하지 않습니다. 해당 보장이 필요한 경우 cursor 기반 별도 계약을 검토합니다.
