package com.typenull.pingdom.analysis.infrastructure.ai;

import com.typenull.pingdom.analysis.application.ai.AiAnalysisClient;
import com.typenull.pingdom.analysis.application.ai.AiAnalysisPrompt;
import com.typenull.pingdom.analysis.application.ai.AiAnalysisResponse;
import com.typenull.pingdom.analysis.application.ai.LocationAnalysisContent;
import java.util.List;

/** 실제 AI 연결 전에도 고정 JSON 계약과 PDF 디자인을 검증할 수 있는 임시 어댑터다. */
public class PlaceholderAiAnalysisClient implements AiAnalysisClient {

    @Override
    public AiAnalysisResponse analyze(AiAnalysisPrompt prompt) {
        LocationAnalysisContent content = new LocationAnalysisContent(
                "입지 분석 보고서",
                new LocationAnalysisContent.OverallLocationEvaluation(
                        LocationAnalysisContent.Grade.INSUFFICIENT_DATA,
                        "AI/MCP 연결 전 단계의 임시 응답입니다.",
                        List.of(), List.of(), List.of()
                ),
                new LocationAnalysisContent.CommercialAreaAnalysis(
                        "데이터 없음", "데이터 없음", "상권 데이터 없음", List.of(), List.of()
                ),
                new LocationAnalysisContent.TargetPopulationAnalysis(
                        "데이터 없음", List.of(), List.of(), List.of()
                ),
                new LocationAnalysisContent.FootTrafficAnalysis(
                        "데이터 없음", null, List.of(), List.of(), List.of()
                ),
                new LocationAnalysisContent.NearbyFacilities(
                        List.of(), List.of(), List.of(), List.of()
                ),
                new LocationAnalysisContent.CompetitionAnalysis(
                        "경쟁 데이터 없음", null, null, null, null, List.of(), List.of()
                ),
                new LocationAnalysisContent.BusinessPerformanceAnalysis(
                        "사업성 데이터 없음", List.of(), List.of(), List.of(), List.of()
                ),
                new LocationAnalysisContent.DataQualityAnalysis(
                        null, null, "데이터 없음", "데이터 없음", null, List.of(), List.of()
                ),
                List.of(),
                new LocationAnalysisContent.AnalysisScope(
                        "요청 지역 미지정", "정규화 전", LocationAnalysisContent.ScopeLevel.CITY,
                        "임시 응답", null
                ),
                List.of(),
                List.of("AI/MCP 연결 후 실제 분석 데이터로 대체됩니다.")
        );
        return new AiAnalysisResponse(content, prompt.analysisBasisDate());
    }
}
