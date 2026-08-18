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
                2. 검색 도구가 연결된 경우에만 외부 검색을 사용하고, 검색 결과의 출처와 기준일을 기록한다.
                3. MCP·DB·검색에서 확인되지 않은 수치·시설·비율·순위는 만들지 않는다.
                4. 데이터가 없으면 반드시 "데이터 없음"을 반환한다.
                5. 실제 조회값, 계산값, 해석을 구분한다. 계산값은 사용한 공식과 원본을 명시한다.
                6. 사용자 입력에 없는 조건을 임의로 추가하지 않는다.

                [지역 범위 규칙]
                1. 요청 지역을 행정구역 기준으로 정규화하고 analysisScope에 기록한다.
                2. 시·도·광역시는 해당 행정구역 전체를 기본 범위로 사용한다.
                3. 구·군은 해당 구·군 전체를 기본 범위로 사용한다.
                4. 읍·면·동은 해당 지역 경계와 인접 생활권을 우선 사용한다.
                5. 도로명·주소·장소처럼 행정구역보다 구체적인 입력은 위치 기준 반경을 사용한다.
                   기본 반경은 주소·장소 500m, 동 단위 1,500m이며 실제 데이터 범위가 있으면 그 범위를 우선한다.
                6. 지역명이 모호하면 임의로 선택하지 말고 후보 또는 확인이 필요한 지역을 limitations에 기록한다.
                7. 모든 수치와 시설은 analysisScope 범위 안의 데이터만 사용한다.

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
                  "analysisScope": {
                    "requestedRegion": "",
                    "normalizedRegion": "",
                    "scopeLevel": "CITY|DISTRICT|NEIGHBORHOOD|ADDRESS",
                    "scopeDescription": "",
                    "radiusMeters": null
                  },
                  "dataSources": [],
                  "limitations": []
                }

                reportId, publishedDate, analysisBasisDate는 서버가 생성하므로 JSON에 포함하지 않는다.
                모든 배열은 데이터가 없으면 빈 배열로 반환하고, 수치가 없으면 null을 반환한다.
                JSON 외의 문자는 절대 출력하지 않는다.
                """.formatted(analysisBasisDate, criteria);
    }
}
