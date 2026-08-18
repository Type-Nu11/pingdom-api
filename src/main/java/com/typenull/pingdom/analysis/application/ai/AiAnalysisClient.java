package com.typenull.pingdom.analysis.application.ai;

/** AI 호출 방식(OpenAI, 사내 gateway, MCP 연동)을 서버 보고서 흐름에서 분리하는 포트다. */
public interface AiAnalysisClient {

    AiAnalysisResponse analyze(AiAnalysisPrompt prompt);
}
