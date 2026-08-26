package com.typenull.pingdom.analysis.infrastructure.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.typenull.pingdom.analysis.application.ai.AiAnalysisClient;
import com.typenull.pingdom.analysis.application.ai.AiAnalysisPrompt;
import com.typenull.pingdom.analysis.application.ai.AiAnalysisResponse;
import com.typenull.pingdom.analysis.application.ai.LocationAnalysisContent;
import com.typenull.pingdom.analysis.application.ai.McpAnalysisClient;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportErrorCode;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Gemini tool call과 Pingdom MCP tool 실행을 중계하는 클라이언트다. */
@Slf4j
public class GeminiAiAnalysisClient implements AiAnalysisClient {

    /** recommend_location이 내부에서 반경 확장을 처리하므로 요청당 MCP 실행은 한 번으로 제한한다. */
    private static final int MAX_TOOL_CALLS = 1;

    private final RestClient restClient;
    private final AiAnalysisProperties properties;
    private final ObjectMapper objectMapper;
    private final McpAnalysisClient mcpAnalysisClient;

    public GeminiAiAnalysisClient(
            RestClient restClient,
            AiAnalysisProperties properties,
            ObjectMapper objectMapper
    ) {
        this(restClient, properties, objectMapper, new DisabledMcpAnalysisClient());
    }

    public GeminiAiAnalysisClient(
            RestClient restClient,
            AiAnalysisProperties properties,
            ObjectMapper objectMapper,
            McpAnalysisClient mcpAnalysisClient
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.mcpAnalysisClient = mcpAnalysisClient;
    }

    @Override
    public AiAnalysisResponse analyze(AiAnalysisPrompt prompt) {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new AnalysisReportException(AnalysisReportErrorCode.AI_SERVICE_UNAVAILABLE, null);
        }

        List<McpAnalysisClient.McpTool> mcpTools = mcpAnalysisClient.listTools();
        log.info("입지 분석 AI 시작. mcpToolCount={}, requestedRegionPresent={}",
                mcpTools.size(), StringUtils.hasText(prompt.requestedRegion()));
        ArrayNode contents = objectMapper.createArrayNode();
        contents.addObject()
                .put("role", "user")
                .putArray("parts")
                .addObject()
                .put("text", prompt.content());
        McpAnalysisClient.McpToolResult latestSuccessfulToolResult = null;

        for (int toolCallCount = 0; toolCallCount <= MAX_TOOL_CALLS; toolCallCount++) {
            JsonNode response = generateContent(contents, mcpTools);
            JsonNode modelContent = response.path("candidates").path(0).path("content");
            JsonNode functionCall = findFunctionCall(modelContent);
            if (functionCall == null) {
                return parseFinalResponseWithRepair(contents, response, prompt, latestSuccessfulToolResult);
            }
            if (toolCallCount == MAX_TOOL_CALLS) {
                contents.addObject()
                        .put("role", "user")
                        .putArray("parts")
                        .addObject()
                        .put("text", "도구 호출을 중단하고 지금까지 조회된 결과만 사용해 최종 JSON 보고서를 반환하라. 추가 도구를 호출하지 마라.");
                JsonNode finalResponse = generateContent(contents, List.of());
                return parseFinalResponseWithRepair(contents, finalResponse, prompt, latestSuccessfulToolResult);
            }

            String name = functionCall.path("name").asText(null);
            Map<String, Object> convertedArguments = objectMapper.convertValue(
                    functionCall.path("args"), new TypeReference<>() {
                    }
            );
            Map<String, Object> arguments = convertedArguments == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(convertedArguments);
            if (!StringUtils.hasText(name)) {
                throw new AnalysisReportException(AnalysisReportErrorCode.AI_RESPONSE_INVALID, null);
            }
            if ("recommend_location".equals(name) && StringUtils.hasText(prompt.requestedRegion())) {
                arguments.put("region", prompt.requestedRegion());
            }

            log.info("입지 분석 MCP 호출. tool={}, argumentKeys={}", name, arguments.keySet());
            contents.add(modelContent.deepCopy());
            McpAnalysisClient.McpToolResult toolResult = mcpAnalysisClient.callTool(name, arguments);
            log.info("입지 분석 MCP 응답 수신. tool={}, isError={}, contentLength={}",
                    name, toolResult.isError(), toolResult.content() == null ? 0 : toolResult.content().length());
            if (!toolResult.isError()) {
                latestSuccessfulToolResult = toolResult;
            }
            appendFunctionResponse(contents, toolResult);
        }
        throw new AnalysisReportException(AnalysisReportErrorCode.AI_RESPONSE_INVALID, null);
    }

    private JsonNode generateContent(ArrayNode contents, List<McpAnalysisClient.McpTool> mcpTools) {
        ObjectNode body = objectMapper.createObjectNode();
        body.set("contents", contents);
        if (!mcpTools.isEmpty()) {
            ArrayNode declarations = body.putArray("tools").addObject().putArray("functionDeclarations");
            for (McpAnalysisClient.McpTool tool : mcpTools) {
                ObjectNode declaration = declarations.addObject();
                declaration.put("name", tool.name());
                declaration.put("description", tool.description());
                declaration.set("parameters", objectMapper.valueToTree(tool.inputSchema()));
            }
        }
        ObjectNode generationConfig = body.putObject("generationConfig");
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("temperature", 0.2);

        try {
            JsonNode response = restClient.post()
                    .uri("models/{model}:generateContent", properties.model())
                    .header("x-goog-api-key", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) {
                throw new AnalysisReportException(AnalysisReportErrorCode.AI_RESPONSE_INVALID, null);
            }
            return response;
        } catch (RestClientException exception) {
            throw new AnalysisReportException(AnalysisReportErrorCode.AI_SERVICE_UNAVAILABLE, exception);
        }
    }

    private JsonNode findFunctionCall(JsonNode modelContent) {
        for (JsonNode part : modelContent.path("parts")) {
            if (part.path("functionCall").isObject()) {
                return part.path("functionCall");
            }
        }
        return null;
    }

    private void appendFunctionResponse(
            ArrayNode contents,
            McpAnalysisClient.McpToolResult toolResult
    ) {
        ObjectNode content = contents.addObject().put("role", "user");
        ObjectNode part = content.putArray("parts").addObject();
        ObjectNode functionResponse = part.putObject("functionResponse");
        functionResponse.put("name", toolResult.name());
        ObjectNode response = functionResponse.putObject("response");
        try {
            response.set("result", objectMapper.readTree(toolResult.content()));
        } catch (JsonProcessingException exception) {
            response.put("result", toolResult.content());
        }
        response.put("isError", toolResult.isError());
    }

    private AiAnalysisResponse parseFinalResponse(String content, AiAnalysisPrompt prompt) {
        if (!StringUtils.hasText(content)) {
            throw new AnalysisReportException(AnalysisReportErrorCode.AI_RESPONSE_INVALID, null);
        }
        try {
            String normalizedJson = normalizeJson(content);
            JsonNode payload = objectMapper.readTree(normalizedJson);
            if (payload.has("html")) {
                // AI가 임의 HTML을 반환하면 보고서마다 디자인과 한글 폰트가 달라질 수 있으므로
                // 고정 XHTML 템플릿이 사용할 구조화 JSON만 허용한다.
                throw new AnalysisReportException(AnalysisReportErrorCode.AI_RESPONSE_INVALID, null);
            }
            LocationAnalysisContent structured = objectMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .forType(LocationAnalysisContent.class)
                    .readValue(normalizedJson);
            return new AiAnalysisResponse(structured, prompt.analysisBasisDate());
        } catch (JsonProcessingException exception) {
            throw new AnalysisReportException(AnalysisReportErrorCode.AI_RESPONSE_INVALID, exception);
        }
    }

    /** 계약 오류는 한 번만 수정 요청한다. 두 번째 실패는 원본 오류로 클라이언트에 502를 반환한다. */
    private AiAnalysisResponse parseFinalResponseWithRepair(
            ArrayNode previousContents,
            JsonNode response,
            AiAnalysisPrompt prompt,
            McpAnalysisClient.McpToolResult latestSuccessfulToolResult
    ) {
        String content = extractText(response);
        try {
            return parseFinalResponse(content, prompt);
        } catch (AnalysisReportException exception) {
            log.warn("입지 분석 AI JSON 계약 오류. retry=true, cause={}, causeMessage={}, responsePreview={}",
                    exception.getCause() == null ? "validation" : exception.getCause().getClass().getSimpleName(),
                    causeMessage(exception),
                    responsePreview(content));
            ArrayNode repairContents = previousContents.deepCopy();
            JsonNode modelContent = response.path("candidates").path(0).path("content");
            if (modelContent.isObject()) {
                repairContents.add(modelContent.deepCopy());
            }
            repairContents.addObject()
                    .put("role", "user")
                    .putArray("parts")
                    .addObject()
                    .put("text", "직전 JSON은 서버 계약을 지키지 못했다. 필드명·enum·중첩 구조를 유지하고, "
                            + "evidences에는 문자열이 아닌 Evidence 객체만 넣어 유효한 JSON 객체만 다시 반환하라. "
                            + "MCP에서 받은 실제 수치는 삭제하거나 0으로 바꾸지 마라.");

            JsonNode repairedResponse = generateContent(repairContents, List.of());
            String repairedContent = extractText(repairedResponse);
            try {
                AiAnalysisResponse repaired = parseFinalResponse(repairedContent, prompt);
                log.info("입지 분석 AI JSON 계약 수정 재시도 성공. responseLength={}", repairedContent.length());
                return repaired;
            } catch (AnalysisReportException retryException) {
                log.warn("입지 분석 AI JSON 계약 수정 재시도 실패. cause={}, causeMessage={}, responsePreview={}",
                        retryException.getCause() == null ? "validation" : retryException.getCause().getClass().getSimpleName(),
                        causeMessage(retryException),
                        responsePreview(repairedContent));
                AiAnalysisResponse fallback = createMcpDataFallback(prompt, latestSuccessfulToolResult);
                if (fallback != null) {
                    log.info("입지 분석 AI 계약 오류를 MCP 원천 데이터 보고서로 대체. recommendationCount={}",
                            fallback.content().recommendedPlaces().size());
                    return fallback;
                }
                throw retryException;
            }
        }
    }

    private String responsePreview(String content) {
        if (!StringUtils.hasText(content)) {
            return "<empty>";
        }
        String normalized = content.replaceAll("[\\r\\n]+", " ");
        return normalized.length() <= 700 ? normalized : normalized.substring(0, 700) + "...";
    }

    private String causeMessage(AnalysisReportException exception) {
        Throwable cause = exception.getCause();
        if (cause == null || !StringUtils.hasText(cause.getMessage())) {
            return "<none>";
        }
        String normalized = cause.getMessage().replaceAll("[\\r\\n]+", " ");
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500) + "...";
    }

    /**
     * Gemini의 설명 JSON이 계약을 두 번 연속 위반해도, 이미 조회한 MCP 수치를 버리지 않기 위한 최후 경로다.
     * 수치가 없는 항목을 추정하지 않고 MCP가 제공한 후보·유동인구·타깃 매칭 수치만 사용한다.
     */
    private AiAnalysisResponse createMcpDataFallback(
            AiAnalysisPrompt prompt,
            McpAnalysisClient.McpToolResult toolResult
    ) {
        if (toolResult == null || toolResult.isError() || !StringUtils.hasText(toolResult.content())) {
            return null;
        }
        try {
            JsonNode payload = objectMapper.readTree(toolResult.content());
            JsonNode recommendations = payload.path("recommendations");
            if (!recommendations.isArray() || recommendations.isEmpty()) {
                return null;
            }

            LocalDate basisDate = prompt.analysisBasisDate();
            double totalFoot = 0;
            double ageMatch = 0;
            double genderMatch = 0;
            double topScore = 0;
            Double averageHour = null;
            List<LocationAnalysisContent.RecommendedPlace> places = new ArrayList<>();
            for (JsonNode recommendation : recommendations) {
                JsonNode metrics = recommendation.path("metrics");
                double foot = number(metrics.path("total_foot"));
                double age = number(metrics.path("age_match"));
                double gender = number(metrics.path("gender_match"));
                totalFoot += foot;
                ageMatch += age;
                genderMatch += gender;
                if (averageHour == null && metrics.path("avg_hour").isNumber()) {
                    averageHour = metrics.path("avg_hour").asDouble();
                }
                int rank = recommendation.path("rank").asInt(places.size() + 1);
                double score = number(recommendation.path("score"));
                double scorePercent = score <= 1 ? score * 100 : score;
                topScore = Math.max(topScore, scorePercent);
                String address = textOr(recommendation.path("address"), "주소 미상");
                String name = textOr(recommendation.path("name"), "추천 입지 " + rank);
                places.add(new LocationAnalysisContent.RecommendedPlace(
                        rank, name, address, Math.min(100, Math.max(0, scorePercent)),
                        "유동인구 " + Math.round(foot) + "명, 타깃 연령 매칭 " + Math.round(age) + "명", List.of("mcp-1")
                ));
            }

            double ageShare = totalFoot == 0 ? 0 : ageMatch * 100 / totalFoot;
            double genderShare = totalFoot == 0 ? 0 : genderMatch * 100 / totalFoot;
            Double radiusMeters = payload.path("searched_radius_m").isNumber()
                    ? payload.path("searched_radius_m").asDouble() : 1500D;
            List<LocationAnalysisContent.SourceValue> sourceValues = List.of(
                    new LocationAnalysisContent.SourceValue("top_candidates_total_foot", String.valueOf(Math.round(totalFoot)), "명"),
                    new LocationAnalysisContent.SourceValue("target_age_match", String.valueOf(Math.round(ageMatch)), "명"),
                    new LocationAnalysisContent.SourceValue("target_gender_match", String.valueOf(Math.round(genderMatch)), "명")
            );
            LocationAnalysisContent.Evidence evidence = new LocationAnalysisContent.Evidence(
                    "mcp-1", LocationAnalysisContent.EvidenceType.MCP, "Pingdom MCP", "recommend_location",
                    basisDate, "MCP가 반환한 상위 후보 입지의 유동인구 및 타깃 매칭 집계", null, sourceValues
            );
            List<LocationAnalysisContent.Evidence> evidences = List.of(evidence);
            List<LocationAnalysisContent.Metric> timeMetrics = averageHour == null ? List.of() : List.of(
                    new LocationAnalysisContent.Metric("후보지 평균 활동 시각", averageHour, "시", null)
            );
            String region = prompt.requestedRegion();
            LocationAnalysisContent content = new LocationAnalysisContent(
                    "MCP 원천 데이터 기반 입지 분석 보고서",
                    new LocationAnalysisContent.OverallLocationEvaluation(
                            LocationAnalysisContent.Grade.CONDITIONAL,
                            "상위 " + places.size() + "개 후보지에서 유동인구 데이터를 확인했습니다. 경쟁·시설 데이터는 별도 검증이 필요합니다.",
                            List.of("상위 후보 유동인구 합계 " + Math.round(totalFoot) + "명 확인"),
                            List.of("경쟁점과 주변 시설은 MCP 응답에 포함되지 않음"), evidences),
                    new LocationAnalysisContent.CommercialAreaAnalysis(region, "유동인구 기반 후보 입지",
                            "MCP 추천 후보의 유동인구를 기준으로 비교한 상권입니다.",
                            List.of(new LocationAnalysisContent.Metric("상위 후보 유동인구 합계", totalFoot, "명", null)), evidences),
                    new LocationAnalysisContent.TargetPopulationAnalysis(
                            "MCP가 요청 타깃 조건에 맞는 유동인구를 집계했습니다.", places.getFirst().name(),
                            List.of(new LocationAnalysisContent.Metric("타깃 연령 매칭", ageMatch, "명", ageShare)),
                            List.of(new LocationAnalysisContent.Metric("타깃 성별 매칭", genderMatch, "명", genderShare)),
                            List.of(), evidences),
                    new LocationAnalysisContent.FootTrafficAnalysis(
                            "상위 추천 후보의 유동인구를 합산한 값입니다.", totalFoot, timeMetrics, List.of(), List.of(),
                            "MCP 평균 활동 시각을 참고해 영업시간을 추가 검토하세요.", null, evidences),
                    new LocationAnalysisContent.NearbyFacilities("MCP 응답에 주변 시설 데이터가 없습니다.",
                            List.of(), List.of(), List.of(), List.of(), List.of()),
                    new LocationAnalysisContent.CompetitionAnalysis("MCP 응답에 경쟁점 데이터가 없습니다.",
                            null, null, null, null, List.of(), List.of()),
                    new LocationAnalysisContent.BusinessPerformanceAnalysis(
                            "유동인구 지표를 기반으로 한 조건부 평가입니다.",
                            List.of(new LocationAnalysisContent.Metric("상위 후보 점수", topScore, "점", topScore)),
                            List.of("타깃 유동인구가 확인된 후보지 우선 검토"),
                            List.of("경쟁·시설 정보 추가 검증 필요"), evidences),
                    new LocationAnalysisContent.DataQualityAnalysis(null, places.size(), "MCP 단건 조회", region,
                            radiusMeters > 1500, List.of("경쟁점 데이터", "주변 시설 데이터"), evidences),
                    places,
                    new LocationAnalysisContent.AnalysisScope(region, region,
                            LocationAnalysisContent.ScopeLevel.NEIGHBORHOOD,
                            "MCP 추천 후보 조회 범위", radiusMeters),
                    List.of(new LocationAnalysisContent.DataSource("mcp-1", LocationAnalysisContent.EvidenceType.MCP,
                            "Pingdom MCP", "recommend_location", basisDate, region)),
                    List.of("Gemini 서술 응답의 계약 오류로 MCP 원천 데이터 기반 보고서로 생성됨.")
            );
            return new AiAnalysisResponse(content, basisDate);
        } catch (JsonProcessingException exception) {
            log.warn("입지 분석 MCP 원천 데이터 대체 생성 실패. cause={}", exception.getClass().getSimpleName());
            return null;
        }
    }

    private double number(JsonNode value) {
        if (value.isNumber()) {
            return value.asDouble();
        }
        if (value.isTextual()) {
            try {
                return Double.parseDouble(value.asText());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private String textOr(JsonNode value, String fallback) {
        return StringUtils.hasText(value.asText()) ? value.asText() : fallback;
    }

    private String extractText(JsonNode response) {
        if (response == null || !response.has("candidates")) {
            return null;
        }
        StringBuilder content = new StringBuilder();
        for (JsonNode part : response.path("candidates").path(0).path("content").path("parts")) {
            if (part.path("text").isTextual()) {
                content.append(part.path("text").asText());
            }
        }
        return content.toString();
    }

    private String normalizeJson(String content) {
        String normalized = content.trim();
        if (normalized.startsWith("```") && normalized.endsWith("```")) {
            int firstLineEnd = normalized.indexOf('\n');
            normalized = firstLineEnd < 0
                    ? normalized.substring(3, normalized.length() - 3).trim()
                    : normalized.substring(firstLineEnd + 1, normalized.length() - 3).trim();
        }
        int objectStart = normalized.indexOf('{');
        int objectEnd = normalized.lastIndexOf('}');
        if (objectStart > 0 || (objectEnd >= 0 && objectEnd < normalized.length() - 1)) {
            if (objectStart < 0 || objectEnd < objectStart) {
                return normalized;
            }
            normalized = normalized.substring(objectStart, objectEnd + 1);
        }
        return normalized;
    }

    private static final class DisabledMcpAnalysisClient implements McpAnalysisClient {

        @Override
        public List<McpTool> listTools() {
            return List.of();
        }

        @Override
        public McpToolResult callTool(String name, Map<String, Object> arguments) {
            throw new AnalysisReportException(AnalysisReportErrorCode.MCP_SERVICE_UNAVAILABLE, null);
        }
    }
}
