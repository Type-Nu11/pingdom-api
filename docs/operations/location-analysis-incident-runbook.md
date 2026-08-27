# 입지 분석 502 대응 Runbook

`POST /analysis/reports/location`는 사용자 조건을 Gemini Interactions API와 Pingdom MCP에 전달해
PDF 보고서를 생성한다. 분석 중 외부 연동 또는 AI 응답 계약이 실패하면 JSON 오류 계약과 HTTP 502를 반환한다.

## 요청 경로

`LocationAnalysisController` → `LocationAnalysisReportService` → `GeminiAiAnalysisClient` →
Gemini Interactions API → Pingdom MCP `recommend_location` → PDF 생성·보고서 보관 순서로 처리한다.

Gemini Remote MCP 요청은 `generation_config.tool_choice=any`와 `recommend_location` 도구 제한을
함께 보내야 한다. `tool_choice`를 요청 최상위에 두면 Gemini 요청 검증 단계에서 실패한다.

## 운영 재현 요청

아래 명령은 URL Markdown 표기와 JSON 문자열의 불필요한 역슬래시를 제거한 운영 재현용 요청이다.

```bash
curl -sS -i -X POST 'http://54.116.166.107:8080/analysis/reports/location' \
  -H 'Content-Type: application/json' \
  --data '{
    "category": "카페",
    "region": "서울특별시 송파구 잠실동",
    "targetCustomerGroup": "20~39세",
    "operatingHours": "평일 09:00~22:00",
    "email": "test@example.com",
    "privacyConsent": true
  }' \
  -D response.headers \
  -o location-analysis.pdf \
  -w 'HTTP %{http_code}\n'
```

## 응답 판별

| HTTP | 응답 형식 | 발생 조건 |
| --- | --- | --- |
| 200 | `application/pdf` | Gemini 분석 응답 검증, PDF 생성, DB 보관까지 모두 성공 |
| 400 | JSON validation 오류 | `category`, `region`, `email`, `privacyConsent`가 누락·형식 오류이거나 동의가 `false` |
| 502 | JSON `ErrorResponse` | Gemini API 통신 실패·실패 상태, MCP 주소 누락, AI 출력 JSON 또는 분석 계약 검증 실패 |
| 500 | JSON `ErrorResponse` | PDF 변환·보고서 DB 보관 등 내부 처리 실패 |

요청 자체가 잘못된 URL이거나 JSON 문법이 깨진 경우에는 API까지 도달하지 않거나 HTTP 메시지 변환 단계에서
실패하므로, 응답 헤더와 본문 파일을 정상 보고서로 취급하지 않는다.

## 502 발생 시 확인 순서

```bash
cd ~/Pingdom_Backend
docker compose ps
curl -fsS http://127.0.0.1:8080/actuator/health
docker compose logs --since 10m app | grep -E 'Gemini Interactions|입지 분석'

cd ~/Pingdom_MCP
docker compose logs --since 10m app
```

`Gemini Interactions API 요청 실패. status=400`이 보이면 요청 형식·지원 모델을 먼저 확인한다.
MCP 로그가 전혀 없으면 Gemini가 MCP 도구를 호출하기 전에 실패한 것이므로 MCP 컨테이너 재시작보다
Gemini 요청과 응답 상태를 우선 확인한다.
