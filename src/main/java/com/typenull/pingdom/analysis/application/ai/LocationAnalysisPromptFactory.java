package com.typenull.pingdom.analysis.application.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.analysis.api.dto.LocationAnalysisRequest;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportErrorCode;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportException;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LocationAnalysisPromptFactory {

    private final ObjectMapper objectMapper;

    public AiAnalysisPrompt create(LocationAnalysisRequest request, LocalDate analysisBasisDate) {
        try {
            Map<String, Object> criteriaMap = request.toCriteriaMap();
            String criteria = objectMapper.writeValueAsString(criteriaMap);
            return new AiAnalysisPrompt(buildPrompt(criteria, analysisBasisDate), analysisBasisDate);
        } catch (JsonProcessingException exception) {
            throw new AnalysisReportException(AnalysisReportErrorCode.AI_RESPONSE_INVALID, exception);
        }
    }

    private String buildPrompt(String criteria, LocalDate analysisBasisDate) {
        return """
                너는 Pingdom의 상권·입지 분석 AI다.

                [입력]
                분석 기준일: %s
                프론트 요청 원본 JSON: %s

                [데이터 규칙]
                1. 필요한 경우 MCP의 읽기 전용 도구로 분석 데이터를 조회한다.
                2. MCP 또는 DB에서 확인되지 않은 수치·시설·비율·순위는 만들지 않는다.
                3. 데이터가 없으면 반드시 "데이터 없음"을 반환한다.
                4. 실제 조회값과 해석을 구분하고, 데이터 기준일·범위·한계를 명시한다.
                5. 사용자 입력에 없는 조건을 임의로 추가하지 않는다.

                [분석 항목]
                - 종합 입지 평가: grade, summary, strengths, risks, evidences
                - 타깃 인구 분석: summary, age, gender, evidences
                - 유동 인구 분석: summary, total, byTime, byDay, evidences
                - 주변 시설: competitors, convenienceFacilities, transportFacilities, evidences

                [반환 계약]
                반드시 아래 JSON 객체만 반환한다. Markdown, 설명 문장, 코드 블록, HTML은 반환하지 않는다.
                {
                  "reportName": "보고서명",
                  "overallLocationEvaluation": {
                    "grade": "SUITABLE|CONDITIONAL|UNSUITABLE|INSUFFICIENT_DATA",
                    "summary": "",
                    "strengths": [],
                    "risks": [],
                    "evidences": []
                  },
                  "targetPopulationAnalysis": {
                    "summary": "",
                    "age": {},
                    "gender": {},
                    "evidences": []
                  },
                  "footTrafficAnalysis": {
                    "summary": "",
                    "total": null,
                    "byTime": [],
                    "byDay": [],
                    "evidences": []
                  },
                  "nearbyFacilities": {
                    "competitors": [],
                    "convenienceFacilities": [],
                    "transportFacilities": [],
                    "evidences": []
                  },
                  "limitations": []
                }

                reportId, publishedDate, analysisBasisDate는 서버가 생성하므로 JSON에 포함하지 않는다.
                모든 배열은 데이터가 없으면 빈 배열로 반환하고, 수치가 없으면 null을 반환한다.
                JSON 외의 문자는 절대 출력하지 않는다.
                """.formatted(analysisBasisDate, criteria);
    }
}
