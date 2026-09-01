package com.typenull.pingdom.analysis.application.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.List;

/** AI가 반환하는 분석 데이터 계약이다. HTML은 이 데이터로 서버가 생성한다. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record LocationAnalysisContent(
        String reportName,
        OverallLocationEvaluation overallLocationEvaluation,
        CommercialAreaAnalysis commercialAreaAnalysis,
        TargetPopulationAnalysis targetPopulationAnalysis,
        FootTrafficAnalysis footTrafficAnalysis,
        NearbyFacilities nearbyFacilities,
        CompetitionAnalysis competitionAnalysis,
        BusinessPerformanceAnalysis businessPerformanceAnalysis,
        DataQualityAnalysis dataQualityAnalysis,
        List<RecommendedPlace> recommendedPlaces,
        AnalysisScope analysisScope,
        List<DataSource> dataSources,
        List<String> limitations
) {

    public LocationAnalysisContent {
        dataSources = dataSources == null ? List.of() : List.copyOf(dataSources);
        recommendedPlaces = recommendedPlaces == null ? List.of() : List.copyOf(recommendedPlaces);
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
    }

    /** Backend가 map_place에서 확인한 동일 업종 경쟁업체를 AI 결과에 반영한다. */
    public LocationAnalysisContent withNearbyCompetitors(List<Facility> competitors) {
        return withNearbyPlaces(competitors, List.of(), List.of());
    }

    /** Backend가 map_place에서 확인한 주변 장소를 시설 유형별로 반영한다. */
    public LocationAnalysisContent withNearbyPlaces(
            List<Facility> competitors,
            List<Facility> convenienceFacilities,
            List<Facility> transportFacilities
    ) {
        NearbyFacilities currentFacilities = nearbyFacilities == null
                ? new NearbyFacilities("데이터 없음", List.of(), List.of(), List.of(), List.of(), List.of())
                : nearbyFacilities;
        CompetitionAnalysis updatedCompetition = new CompetitionAnalysis(
                competitionSummary(competitors.size()), competitors.size(), null, null, null, competitors,
                competitionAnalysis == null ? List.of() : competitionAnalysis.evidences()
        );
        List<Facility> effectiveConvenience = convenienceFacilities.isEmpty()
                ? currentFacilities.convenienceFacilities() : convenienceFacilities;
        List<Facility> effectiveTransport = transportFacilities.isEmpty()
                ? currentFacilities.transportFacilities() : transportFacilities;
        NearbyFacilities updatedFacilities = new NearbyFacilities(
                facilitySummary(currentFacilities.summary(), competitors, effectiveConvenience, effectiveTransport),
                currentFacilities.demandDrivers(), competitors, effectiveConvenience, effectiveTransport,
                currentFacilities.evidences()
        );
        return new LocationAnalysisContent(
                reportName, overallLocationEvaluation, commercialAreaAnalysis, targetPopulationAnalysis,
                footTrafficAnalysis, updatedFacilities, updatedCompetition, businessPerformanceAnalysis,
                dataQualityAnalysis, recommendedPlaces, analysisScope, dataSources, limitations
        );
    }

    /** 관측된 유동 데이터가 있지만 AI 사업성 섹션이 비어 있을 때만 서버가 수치를 재사용해 보강한다. */
    public LocationAnalysisContent withDerivedBusinessPerformance() {
        if (footTrafficAnalysis == null || footTrafficAnalysis.total() == null || footTrafficAnalysis.total() <= 0d) {
            return this;
        }

        BusinessPerformanceAnalysis current = businessPerformanceAnalysis;
        List<Metric> indicators = current == null || current.performanceIndicators().isEmpty()
                ? derivedPerformanceIndicators() : current.performanceIndicators();
        String summary = current == null || isEmptyData(current.summary())
                ? "관측 유동 인구 " + formatWholeNumber(footTrafficAnalysis.total())
                        + "명을 기준으로 입지 수요를 검토했습니다."
                : current.summary();
        List<String> opportunities = current == null || current.opportunities().isEmpty()
                ? List.of("관측 유동 인구를 바탕으로 시간대별 운영·홍보 전략을 검증할 수 있습니다.")
                : current.opportunities();
        List<String> risks = current == null || current.risks().isEmpty()
                ? List.of("유동 지표는 관측값이며 매출·임대료·전환율은 별도 검증이 필요합니다.")
                : current.risks();
        BusinessPerformanceAnalysis derived = new BusinessPerformanceAnalysis(
                summary, indicators, opportunities, risks, current == null ? List.of() : current.evidences()
        );
        return new LocationAnalysisContent(
                reportName, overallLocationEvaluation, commercialAreaAnalysis, targetPopulationAnalysis,
                footTrafficAnalysis, nearbyFacilities, competitionAnalysis, derived, dataQualityAnalysis,
                recommendedPlaces, analysisScope, dataSources, limitations
        );
    }

    private static String facilitySummary(
            String currentSummary,
            List<Facility> competitors,
            List<Facility> convenienceFacilities,
            List<Facility> transportFacilities
    ) {
        int total = competitors.size() + convenienceFacilities.size() + transportFacilities.size();
        return total == 0
                ? "추천 좌표 1.5km 내 공개 map_place 시설이 확인되지 않았습니다."
                : "추천 좌표 1.5km 내 공개 map_place 장소 " + total + "건을 분류했습니다."
                        + " 경쟁 " + competitors.size() + "건, 편의 " + convenienceFacilities.size()
                        + "건, 교통 " + transportFacilities.size() + "건";
    }

    private List<Metric> derivedPerformanceIndicators() {
        java.util.ArrayList<Metric> metrics = new java.util.ArrayList<>();
        metrics.add(new Metric("관측 유동 인구", footTrafficAnalysis.total(), "명", null));
        if (footTrafficAnalysis.operatingHoursFitScore() != null) {
            metrics.add(new Metric(
                    "영업시간 적합도", footTrafficAnalysis.operatingHoursFitScore(), "점",
                    footTrafficAnalysis.operatingHoursFitScore()
            ));
        }
        footTrafficAnalysis.byTime().stream()
                .filter(metric -> metric != null && metric.value() != null)
                .max(java.util.Comparator.comparing(Metric::value))
                .ifPresent(metric -> metrics.add(new Metric(
                        "피크 유동 시간대(" + metric.label() + ")", metric.value(), metric.unit(), metric.sharePercent()
                )));
        if (targetPopulationAnalysis != null) {
            targetPopulationAnalysis.behaviorIndicators().stream()
                    .filter(metric -> metric != null && metric.value() != null)
                    .findFirst()
                    .ifPresent(metric -> metrics.add(new Metric(
                            "평균 활동 시간", metric.value(), metric.unit(), metric.sharePercent()
                    )));
        }
        return List.copyOf(metrics);
    }

    private static boolean isEmptyData(String value) {
        return value == null || value.isBlank() || value.contains("데이터 없음");
    }

    private static String formatWholeNumber(Double value) {
        return String.format(java.util.Locale.ROOT, "%,.0f", value);
    }

    private static String competitionSummary(int totalCompetitors) {
        return totalCompetitors == 0
                ? "분석 기준 좌표 100m 내 동일 업종 공개 장소가 확인되지 않았습니다."
                : "분석 기준 좌표 100m 내 동일 업종 경쟁점 " + totalCompetitors + "건을 확인했습니다.";
    }

    public LocationAnalysisContent(
            String reportName,
            OverallLocationEvaluation overallLocationEvaluation,
            TargetPopulationAnalysis targetPopulationAnalysis,
            FootTrafficAnalysis footTrafficAnalysis,
            NearbyFacilities nearbyFacilities,
            AnalysisScope analysisScope,
            List<DataSource> dataSources,
            List<String> limitations
    ) {
        this(reportName, overallLocationEvaluation, null, targetPopulationAnalysis, footTrafficAnalysis,
                nearbyFacilities, null, null, null, List.of(), analysisScope, dataSources, limitations);
    }

    public LocationAnalysisContent(
            String reportName,
            OverallLocationEvaluation overallLocationEvaluation,
            TargetPopulationAnalysis targetPopulationAnalysis,
            FootTrafficAnalysis footTrafficAnalysis,
            NearbyFacilities nearbyFacilities,
            List<RecommendedPlace> recommendedPlaces,
            AnalysisScope analysisScope,
            List<DataSource> dataSources,
            List<String> limitations
    ) {
        this(reportName, overallLocationEvaluation, null, targetPopulationAnalysis, footTrafficAnalysis,
                nearbyFacilities, null, null, null, recommendedPlaces, analysisScope, dataSources, limitations);
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record CommercialAreaAnalysis(
            String name,
            String type,
            String summary,
            List<Metric> demandIndicators,
            List<Evidence> evidences
    ) {
        public CommercialAreaAnalysis {
            demandIndicators = demandIndicators == null ? List.of() : List.copyOf(demandIndicators);
            evidences = evidences == null ? List.of() : List.copyOf(evidences);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record OverallLocationEvaluation(
            Grade grade,
            Double overallScore,
            String summary,
            List<String> strengths,
            List<String> risks,
            List<Evidence> evidences
    ) {
        public OverallLocationEvaluation {
            strengths = strengths == null ? List.of() : List.copyOf(strengths);
            risks = risks == null ? List.of() : List.copyOf(risks);
            evidences = evidences == null ? List.of() : List.copyOf(evidences);
        }

        public OverallLocationEvaluation(
                Grade grade,
                String summary,
                List<String> strengths,
                List<String> risks,
                List<Evidence> evidences
        ) {
            this(grade, null, summary, strengths, risks, evidences);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record TargetPopulationAnalysis(
            String summary,
            String derivedFromPlace,
            List<Metric> age,
            List<Metric> gender,
            List<Metric> behaviorIndicators,
            List<Evidence> evidences
    ) {
        public TargetPopulationAnalysis {
            age = age == null ? List.of() : List.copyOf(age);
            gender = gender == null ? List.of() : List.copyOf(gender);
            behaviorIndicators = behaviorIndicators == null ? List.of() : List.copyOf(behaviorIndicators);
            evidences = evidences == null ? List.of() : List.copyOf(evidences);
        }

        public TargetPopulationAnalysis(
                String summary,
                List<Metric> age,
                List<Metric> gender,
                List<Evidence> evidences
        ) {
            this(summary, "데이터 없음", age, gender, List.of(), evidences);
        }

        public TargetPopulationAnalysis(
                String summary,
                String derivedFromPlace,
                List<Metric> age,
                List<Metric> gender,
                List<Evidence> evidences
        ) {
            this(summary, derivedFromPlace, age, gender, List.of(), evidences);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record FootTrafficAnalysis(
            String summary,
            Double total,
            List<Metric> byTime,
            List<Metric> byDay,
            List<Metric> byMonth,
            String operatingHoursAssessment,
            Double operatingHoursFitScore,
            List<Evidence> evidences
    ) {
        public FootTrafficAnalysis {
            byTime = byTime == null ? List.of() : List.copyOf(byTime);
            byDay = byDay == null ? List.of() : List.copyOf(byDay);
            byMonth = byMonth == null ? List.of() : List.copyOf(byMonth);
            evidences = evidences == null ? List.of() : List.copyOf(evidences);
        }

        public FootTrafficAnalysis(
                String summary,
                Double total,
                List<Metric> byTime,
                List<Metric> byDay,
                List<Evidence> evidences
        ) {
            this(summary, total, byTime, byDay, List.of(), "데이터 없음", null, evidences);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record NearbyFacilities(
            String summary,
            List<Metric> demandDrivers,
            List<Facility> competitors,
            List<Facility> convenienceFacilities,
            List<Facility> transportFacilities,
            List<Evidence> evidences
    ) {
        public NearbyFacilities {
            demandDrivers = demandDrivers == null ? List.of() : List.copyOf(demandDrivers);
            competitors = competitors == null ? List.of() : List.copyOf(competitors);
            convenienceFacilities = convenienceFacilities == null ? List.of() : List.copyOf(convenienceFacilities);
            transportFacilities = transportFacilities == null ? List.of() : List.copyOf(transportFacilities);
            evidences = evidences == null ? List.of() : List.copyOf(evidences);
        }

        public NearbyFacilities(
                List<Facility> competitors,
                List<Facility> convenienceFacilities,
                List<Facility> transportFacilities,
                List<Evidence> evidences
        ) {
            this("데이터 없음", List.of(), competitors, convenienceFacilities, transportFacilities, evidences);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record CompetitionAnalysis(
            String summary,
            Integer totalCompetitors,
            Integer franchiseCompetitors,
            Integer independentCompetitors,
            Double competitionDensity,
            List<Facility> keyCompetitors,
            List<Evidence> evidences
    ) {
        public CompetitionAnalysis {
            keyCompetitors = keyCompetitors == null ? List.of() : List.copyOf(keyCompetitors);
            evidences = evidences == null ? List.of() : List.copyOf(evidences);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record BusinessPerformanceAnalysis(
            String summary,
            List<Metric> performanceIndicators,
            List<String> opportunities,
            List<String> risks,
            List<Evidence> evidences
    ) {
        public BusinessPerformanceAnalysis {
            performanceIndicators = performanceIndicators == null ? List.of() : List.copyOf(performanceIndicators);
            opportunities = opportunities == null ? List.of() : List.copyOf(opportunities);
            risks = risks == null ? List.of() : List.copyOf(risks);
            evidences = evidences == null ? List.of() : List.copyOf(evidences);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record DataQualityAnalysis(
            Double reliabilityScore,
            Integer observationCount,
            String observationPeriod,
            String coverage,
            Boolean radiusExpanded,
            List<String> missingData,
            List<Evidence> evidences
    ) {
        public DataQualityAnalysis {
            missingData = missingData == null ? List.of() : List.copyOf(missingData);
            evidences = evidences == null ? List.of() : List.copyOf(evidences);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record RecommendedPlace(
            Integer rank,
            String name,
            String address,
            Double score,
            String reason,
            List<String> evidenceIds,
            Double latitude,
            Double longitude
    ) {
        public RecommendedPlace {
            evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        }

        public RecommendedPlace(
                Integer rank,
                String name,
                String address,
                Double score,
                String reason,
                List<String> evidenceIds
        ) {
            this(rank, name, address, score, reason, evidenceIds, null, null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record AnalysisScope(
            String requestedRegion,
            String normalizedRegion,
            ScopeLevel scopeLevel,
            String scopeDescription,
            Double radiusMeters
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record Metric(
            String label,
            Double value,
            String unit,
            Double sharePercent
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record Facility(
            String name,
            String category,
            Double distanceMeters,
            String address,
            String description
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record Evidence(
            String id,
            EvidenceType type,
            String source,
            String reference,
            LocalDate basisDate,
            String description,
            String formula,
            List<SourceValue> sourceValues
    ) {
        public Evidence {
            sourceValues = sourceValues == null ? List.of() : List.copyOf(sourceValues);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record SourceValue(
            String name,
            String value,
            String unit
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record DataSource(
            String id,
            EvidenceType type,
            String source,
            String reference,
            LocalDate basisDate,
            String scope
    ) {
    }

    public enum Grade {
        SUITABLE,
        CONDITIONAL,
        UNSUITABLE,
        INSUFFICIENT_DATA
    }

    public enum ScopeLevel {
        CITY,
        DISTRICT,
        NEIGHBORHOOD,
        ADDRESS
    }

    public enum EvidenceType {
        DB,
        MCP,
        SEARCH,
        CALCULATION
    }
}
