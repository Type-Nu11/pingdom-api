# 로컬 AI 입지 분석 실행

입지 분석 API는 `AiAnalysisClient` 포트를 통해 AI 공급자를 선택합니다. `local` 프로필에서는 기본값으로 Ollama를 사용하고, 그 외 프로필에서는 기존 임시 응답을 사용합니다.

## 1. Ollama와 모델 준비

```bash
ollama serve
ollama pull qwen2.5:7b
```

Ollama가 이미 실행 중이면 `ollama serve`는 다시 실행하지 않아도 됩니다.

## 2. 서버 실행

```bash
SPRING_PROFILES_ACTIVE=local \
AI_PROVIDER=ollama \
AI_BASE_URL=http://localhost:11434 \
AI_MODEL=qwen2.5:7b \
./gradlew bootRun
```

`POST /analysis/reports/location` 요청이 들어오면 서버가 Ollama `/api/chat`으로 시스템 지침과 분석 조건을 전달하고, AI가 반환한 HTML을 PDF로 변환합니다.

`pingdom-mcp` 연동 도구를 사용할 경우 `MCP_ENABLED=true`를 설정합니다. 현재 MCP 저장소의 `/recommend`는 내부에서 `GROQ_API_KEY`를 사용하므로 MCP 실행에는 해당 키가 필요합니다.

## 3. 설정값

| 환경변수 | 기본값 | 설명 |
| --- | --- | --- |
| `AI_PROVIDER` | `placeholder` (`local` 프로필은 `ollama`) | `ollama` 또는 `placeholder` |
| `AI_BASE_URL` | `http://localhost:11434` | Ollama 주소 |
| `AI_MODEL` | `qwen2.5:7b` | 설치한 Ollama 모델명 |
| `AI_CONNECT_TIMEOUT` | `2s` | 연결 제한 시간 |
| `AI_READ_TIMEOUT` | `2m` | 모델 응답 제한 시간 |
| `MCP_ENABLED` | `false` | MCP `/recommend` 조회 사용 여부 |
| `MCP_BASE_URL` | `http://localhost:8080` | pingdom-mcp 주소 |

현재 MCP 서버는 별도 구현 대상입니다. MCP 조회 결과를 프롬프트에 포함하는 어댑터를 추가하면 동일한 `AiAnalysisClient` 인터페이스를 유지한 채 DB 기반 분석으로 확장할 수 있습니다.
