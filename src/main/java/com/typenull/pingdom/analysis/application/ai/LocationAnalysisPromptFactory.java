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
            return new AiAnalysisPrompt(
                    systemInstruction() + "\n\n" + userPrompt(criteria, analysisBasisDate),
                    analysisBasisDate
            );
        } catch (JsonProcessingException exception) {
            throw new AnalysisReportException(AnalysisReportErrorCode.AI_RESPONSE_INVALID, exception);
        }
    }

    private String systemInstruction() {
        return """
                너는 Pingdom의 상권·입지 분석 보고서 작성 AI다.
                MCP 서버를 통해 조회 가능한 DB 데이터만 근거로 사용하고, 조회되지 않은 값은 추정하지 말고 '데이터 없음'으로 표시한다.
                분석 결과는 반드시 UTF-8 HTML 본문으로 작성한다.
                서버가 보고서명, 보고서 ID, 발행일자, 분석 기준일 메타데이터를 최종 HTML에 주입한다.
                AI HTML 본문에는 종합 입지 평가, 타깃 인구 분석, 유동 인구 분석, 주변 시설 섹션을 포함한다.
                표와 목록은 인쇄 가능한 semantic HTML(table, section, h1~h3)로 작성하며 script와 외부 실행 코드는 포함하지 않는다.
                """;
    }

    private String userPrompt(String criteria, LocalDate analysisBasisDate) {
        return """
                다음 조건으로 입지 분석 보고서를 생성해라.
                분석 기준일: %s
                프론트 요청 조건(JSON): %s

                처리 순서:
                1. 필요한 경우 MCP를 통해 분석 데이터를 조회한다.
                2. 조회 기준과 데이터 한계를 명시한다.
                3. 업종 적합성, 타깃 도달 가능성, 유동인구, 주변 시설을 종합 평가한다.
                4. 종합 입지 평가, 타깃 인구 분석, 유동 인구 분석, 주변 시설 섹션을 포함한 HTML만 반환한다.
                """.formatted(analysisBasisDate, criteria);
    }
}
