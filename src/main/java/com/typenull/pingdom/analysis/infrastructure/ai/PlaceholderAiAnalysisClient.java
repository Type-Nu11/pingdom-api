package com.typenull.pingdom.analysis.infrastructure.ai;

import com.typenull.pingdom.analysis.application.ai.AiAnalysisClient;
import com.typenull.pingdom.analysis.application.ai.AiAnalysisPrompt;
import com.typenull.pingdom.analysis.application.ai.AiAnalysisResponse;
import org.springframework.web.util.HtmlUtils;

/** 실제 AI/MCP 어댑터가 연결되기 전에도 PDF 파이프라인을 검증할 수 있는 기본 어댑터다. */
public class PlaceholderAiAnalysisClient implements AiAnalysisClient {

    @Override
    public AiAnalysisResponse analyze(AiAnalysisPrompt prompt) {
        String criteria = HtmlUtils.htmlEscape(prompt.userPrompt());
        String reportName = "입지 분석 보고서";
        String html = """
                <p class="notice">현재는 AI/MCP 연결 전 단계입니다. 아래 문서는 PDF 변환 및 API 계약 검증용 임시 응답입니다.</p>
                <h2>종합 입지 평가</h2>
                <p>AI/MCP 연동 후 조회된 데이터에 기반한 종합 입지 평가가 이 영역에 표시됩니다.</p>
                <h2>타깃 인구 분석</h2>
                <p>AI/MCP 연동 후 요청 조건에 맞는 타깃 인구 분석이 이 영역에 표시됩니다.</p>
                <h2>유동 인구 분석</h2>
                <p>AI/MCP 연동 후 시간대·요일별 유동 인구 분석이 이 영역에 표시됩니다.</p>
                <h2>주변 시설</h2>
                <p>AI/MCP 연동 후 주변 경쟁·편의·교통 시설 분석이 이 영역에 표시됩니다.</p>
                <h2>분석 요청 조건</h2>
                <pre>%s</pre>
                """.formatted(criteria);
        return new AiAnalysisResponse(reportName, prompt.analysisBasisDate(), html);
    }
}
