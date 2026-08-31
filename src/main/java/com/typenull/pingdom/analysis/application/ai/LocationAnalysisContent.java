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
        CompetitionAnalysis updatedCompetition = competitionAnalysis == null
                ? null
                : new CompetitionAnalysis(
                        competitionAnalysis.summary(), competitors.size(),
                        Math.min(nonNegative(competitionAnalysis.franchiseCompetitors()), competitors.size()),
                        Math.max(0, competitors.size()
                                - Math.min(nonNegative(competitionAnalysis.franchiseCompetitors()), competitors.size())),
                        competitionAnalysis.competitionDensity(), competitors, competitionAnalysis.evidences()
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

    private static int nonNegative(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private static String facilitySummary(
            String currentSummary,
            List<Facility> competitors,
            List<Facility> convenienceFacilities,
            List<Facility> transportFacilities
    ) {
        if (currentSummary != null && !currentSummary.isBlank()
                && !"데이터 없음".equals(currentSummary) && !"주변 시설 없음".equals(currentSummary)) {
            return currentSummary;
        }
        int total = competitors.size() + convenienceFacilities.size() + transportFacilities.size();
        return total == 0 ? "확인된 시설 없음" : "추천 좌표 1.5km 내 공개 장소 " + total + "건 확인";
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
