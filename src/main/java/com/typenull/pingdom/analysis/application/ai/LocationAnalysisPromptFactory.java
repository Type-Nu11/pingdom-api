package com.typenull.pingdom.analysis.application.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.analysis.api.dto.LocationAnalysisRequest;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportErrorCode;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportException;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LocationAnalysisPromptFactory {

    private final ObjectMapper objectMapper;

    public AiAnalysisPrompt create(LocationAnalysisRequest request, LocalDate analysisBasisDate) {
        try {
            String criteria = objectMapper.writeValueAsString(request.toCriteriaMap());
            return new AiAnalysisPrompt(
                    buildPrompt(criteria, analysisBasisDate), analysisBasisDate, request.getRegion()
            );
        } catch (JsonProcessingException exception) {
            throw new AnalysisReportException(AnalysisReportErrorCode.AI_RESPONSE_INVALID, exception);
        }
    }

    private String buildPrompt(String criteria, LocalDate analysisBasisDate) {
        return """
                너는 Pingdom 상권·입지 분석 AI다. 아래 입력과 Pingdom MCP의 조회 결과만 근거로 분석한다.
                확인하지 못한 장소·주소·수치·비율을 만들지 않는다. Backend가 고정 XHTML 템플릿으로 PDF를
                만들므로 HTML, Markdown, 설명 문장을 반환하지 말고 JSON 객체 하나만 반환한다.

                [입력]
                분석 기준일: %s
                [FRONTEND_REQUEST_JSON_BEGIN]
                %s
                [FRONTEND_REQUEST_JSON_END]
                고정 입력은 category(업종), region(희망 지역), targetCustomerGroup(선택),
                operatingHours(선택)다. category와 region은 필수다.

                [MCP]
                - 실행환경의 Pingdom MCP에 연결된 읽기 전용 도구만 사용한다.
                - recommend_location을 정확히 한 번 호출하고 region에는 입력 region을 그대로 넣는다.
                - age_min, age_max, gender는 targetCustomerGroup에 명시된 값만 사용한다. 해석할 수 없으면
                  임의의 연령·성별을 만들지 않는다.
                - MCP가 반환한 recommendations, metrics, statistics, nearby_places를 그대로 근거로 사용한다.
                  추천 장소의 rank/name(address의 실제 장소명)/address/score/lat/lng와 metrics.total_foot,
                  metrics.age_match, metrics.gender_match, metrics.avg_hour를 누락하거나 변경하지 않는다.
                - nearby_places는 name/address/category/distance_m을 그대로 사용한다. 요청 업종과 같고
                  반경 100m 이내면 competitors, 역·정류장·터미널·지하철·공항이면 transportFacilities,
                  그 외는 convenienceFacilities로 분류한다. 이 주변 조회 결과는 Backend 후처리 결과와 함께 사용한다.
                - MCP 오류 또는 recommendations가 빈 배열이면 grade는 INSUFFICIENT_DATA로 한다.
                - 원본에 없는 배열은 []로, 원본에 없는 숫자는 null로 둔다. null placeholder와 임의의 0은 만들지 않는다.

                [등급]
                - recommendations가 없으면 INSUFFICIENT_DATA, overallScore는 null.
                - 확인된 위험이 없고 검증된 점수가 70 이상이면 SUITABLE.
                - 점수가 45~69이거나 핵심 외 입력·비교 데이터가 부족하면 CONDITIONAL.
                - 점수가 45 미만이거나 근거가 있는 중대한 위험이면 UNSUITABLE.
                - 점수의 원본·분모·계산식이 없으면 점수를 만들지 말고 CONDITIONAL로 둔다.

                [출력 규칙]
                - 아래 구조와 필드명만 사용한다. 필드를 추가·삭제·이름 변경하지 않는다.
                - 모든 evidences 원소는 문자열이 아닌 객체다. type은 DB, MCP, SEARCH, CALCULATION 중 하나다.
                  실패 사유(예: GEOCODE_FAILED)는 evidences가 아니라 limitations에 기록한다.
                  Evidence는 {id, type, source, reference, basisDate, description, formula, sourceValues} 구조다.
                - Metric은 {label, value, unit, sharePercent}, Facility는
                  {name, category, distanceMeters, address, description} 구조다.
                - dataSources는 {id, type, source, reference, basisDate, scope} 객체 배열이다.
                - recommendedPlaces는 MCP recommendations를 rank 순서로 모두 넣고,
                  {rank, name, address, score, reason, evidenceIds, latitude, longitude}를 사용한다.
                - analysisScope.requestedRegion은 입력 region과 완전히 같아야 하며 scopeLevel은
                  CITY, DISTRICT, NEIGHBORHOOD, ADDRESS 중 하나다.
                - targetPopulationAnalysis.derivedFromPlace는 실제 추천 장소명이며, 없을 때만 "데이터 없음"이다.
                - 통계·시설·사업성 데이터가 MCP에 없으면 해당 배열을 []로 두고 limitations에 누락 원인을 쓴다.

                [JSON]
                {
                  "reportName": "[업종] [지역] 상권·입지 분석 보고서",
                  "overallLocationEvaluation": {"grade":"SUITABLE|CONDITIONAL|UNSUITABLE|INSUFFICIENT_DATA", "overallScore":null, "summary":"", "strengths":[], "risks":[], "evidences":[]},
                  "commercialAreaAnalysis": {"name":"", "type":"", "summary":"", "demandIndicators":[], "evidences":[]},
                  "targetPopulationAnalysis": {"summary":"", "derivedFromPlace":"", "age":[], "gender":[], "behaviorIndicators":[], "evidences":[]},
                  "footTrafficAnalysis": {"summary":"", "total":null, "byTime":[], "byDay":[], "byMonth":[], "operatingHoursAssessment":"", "operatingHoursFitScore":null, "evidences":[]},
                  "nearbyFacilities": {"summary":"", "demandDrivers":[], "competitors":[], "convenienceFacilities":[], "transportFacilities":[], "evidences":[]},
                  "competitionAnalysis": {"summary":"", "totalCompetitors":null, "franchiseCompetitors":null, "independentCompetitors":null, "competitionDensity":null, "keyCompetitors":[], "evidences":[]},
                  "businessPerformanceAnalysis": {"summary":"", "performanceIndicators":[], "opportunities":[], "risks":[], "evidences":[]},
                  "dataQualityAnalysis": {"reliabilityScore":null, "observationCount":null, "observationPeriod":"", "coverage":"", "radiusExpanded":false, "missingData":[], "evidences":[]},
                  "recommendedPlaces": [],
                  "analysisScope": {"requestedRegion":"", "normalizedRegion":"", "scopeLevel":"CITY|DISTRICT|NEIGHBORHOOD|ADDRESS", "scopeDescription":"", "radiusMeters":null},
                  "dataSources": [],
                  "limitations": []
                }
                JSON 외의 모든 문자는 출력하지 않는다.
                """.formatted(analysisBasisDate, criteria);
    }
}
