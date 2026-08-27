# AI 입지 분석 실행

입지 분석 API는 `AiAnalysisClient` 포트를 통해 공급자를 선택합니다.

## Gemini 무료 티어 사용

Google AI Studio에서 발급한 키를 EC2 환경변수로 등록합니다.

```bash
SPRING_PROFILES_ACTIVE=local \
AI_PROVIDER=gemini \
GEMINI_API_KEY=your-api-key \
AI_MODEL=gemini-3.1-flash-lite \
# Gemini Remote MCP가 접근할 수 있는 HTTPS Streamable HTTP 주소
MCP_SERVER_URL=https://mcp.example.com/mcp \
MCP_AUTH_TOKEN=your-mcp-token \
./gradlew bootRun
```

`AI_BASE_URL`을 지정하지 않으면 Gemini API 기본 주소를 사용합니다. API 키는 Git에 커밋하지 않습니다.

## Ollama 로컬 사용

```bash
ollama serve
ollama pull qwen2.5:7b

SPRING_PROFILES_ACTIVE=local \
AI_PROVIDER=ollama \
AI_BASE_URL=http://localhost:11434 \
AI_MODEL=qwen2.5:7b \
./gradlew bootRun
```

## 처리 흐름

`POST /analysis/reports/location` 요청이 들어오면 서버는 다음 순서로 처리합니다.

1. 프론트 입력(`category`, `region`, `targetCustomerGroup`, `operatingHours` 및 기타 조건)을 하나의 프롬프트에 삽입합니다.
2. Backend가 Gemini Interactions API에 Remote MCP 서버와 분석 프롬프트를 함께 등록합니다.
3. Gemini가 MCP 세션을 열고 `tools/call`을 실행해 DB 조회 결과를 받습니다.
4. Gemini가 고정 JSON 계약을 생성하고 Backend가 최종 응답을 받습니다.
5. 서버가 HTML 안전성·필수 필드를 검증한 뒤 PDF로 변환합니다.

## 주요 환경변수

| 환경변수 | 설명 |
| --- | --- |
| `AI_PROVIDER` | `gemini`, `ollama`, `placeholder` |
| `GEMINI_API_KEY` 또는 `AI_API_KEY` | Gemini API 키(`gemini` 사용 시 필수) |
| `AI_BASE_URL` | AI API 주소. 미지정 시 공급자별 기본값 사용 |
| `AI_MODEL` | Gemini 또는 Ollama 모델명 |
| `AI_CONNECT_TIMEOUT` | 연결 제한 시간 |
| `AI_READ_TIMEOUT` | 응답 제한 시간 |
| `MCP_SERVER_URL` | Gemini가 접근할 수 있는 Pingdom Remote MCP Streamable HTTP 주소 |
| `MCP_AUTH_TOKEN` | MCP 인증 토큰. 설정 시 Gemini 요청의 Bearer 헤더로 전달 |

Gemini 무료 티어의 모델별 한도와 요금은 Google 공식 가격·제한 문서를 확인해야 합니다.
