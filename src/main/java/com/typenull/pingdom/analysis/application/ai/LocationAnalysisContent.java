package com.typenull.pingdom.analysis.application.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.List;

/** AI가 반환하는 분석 데이터 계약이다. HTML은 이 데이터로 서버가 생성한다. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record LocationAnalysisContent(
        String reportName,
        OverallLocationEvaluation overallLocationEvaluation,
        TargetPopulationAnalysis targetPopulationAnalysis,
        FootTrafficAnalysis footTrafficAnalysis,
        NearbyFacilities nearbyFacilities,
        AnalysisScope analysisScope,
        List<DataSource> dataSources,
        List<String> limitations
) {

    public LocationAnalysisContent {
        dataSources = dataSources == null ? List.of() : List.copyOf(dataSources);
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record OverallLocationEvaluation(
            Grade grade,
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
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record TargetPopulationAnalysis(
            String summary,
            List<Metric> age,
            List<Metric> gender,
            List<Evidence> evidences
    ) {
        public TargetPopulationAnalysis {
            age = age == null ? List.of() : List.copyOf(age);
            gender = gender == null ? List.of() : List.copyOf(gender);
            evidences = evidences == null ? List.of() : List.copyOf(evidences);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record FootTrafficAnalysis(
            String summary,
            Double total,
            List<Metric> byTime,
            List<Metric> byDay,
            List<Evidence> evidences
    ) {
        public FootTrafficAnalysis {
            byTime = byTime == null ? List.of() : List.copyOf(byTime);
            byDay = byDay == null ? List.of() : List.copyOf(byDay);
            evidences = evidences == null ? List.of() : List.copyOf(evidences);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record NearbyFacilities(
            List<Facility> competitors,
            List<Facility> convenienceFacilities,
            List<Facility> transportFacilities,
            List<Evidence> evidences
    ) {
        public NearbyFacilities {
            competitors = competitors == null ? List.of() : List.copyOf(competitors);
            convenienceFacilities = convenienceFacilities == null ? List.of() : List.copyOf(convenienceFacilities);
            transportFacilities = transportFacilities == null ? List.of() : List.copyOf(transportFacilities);
            evidences = evidences == null ? List.of() : List.copyOf(evidences);
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
