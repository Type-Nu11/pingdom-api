package com.typenull.pingdom.analysis.application.ai;

import java.util.Map;

/** MCP 조회 결과를 AI 프롬프트에 공급하는 포트다. */
@FunctionalInterface
public interface McpAnalysisDataProvider {

    String fetch(Map<String, Object> criteria);
}
