package com.typenull.pingdom.analysis.application.ai;

import com.typenull.pingdom.analysis.api.dto.LocationAnalysisRequest;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportErrorCode;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** AI JSON의 스키마와 핵심 의미 규칙을 PDF 생성 전에 검증한다. */
@Component
@RequiredArgsConstructor
public class LocationAnalysisResponseValidator {

    public void validate(LocationAnalysisRequest request, AiAnalysisResponse response) {
        if (response == null || response.content() == null || response.analysisBasisDate() == null) {
            invalid();
        }
        LocationAnalysisContent content = response.content();
        requireText(content.reportName(), "reportName");
        if (content.overallLocationEvaluation() == null
                || content.targetPopulationAnalysis() == null
                || content.footTrafficAnalysis() == null
                || content.nearbyFacilities() == null
                || content.analysisScope() == null) {
            invalid();
        }
        if (content.overallLocationEvaluation().grade() == null) {
            invalid();
        }
        validateScope(request, content.analysisScope());
        validateEvidence(content.overallLocationEvaluation().evidences());
        validateEvidence(content.targetPopulationAnalysis().evidences());
        validateEvidence(content.footTrafficAnalysis().evidences());
        validateEvidence(content.nearbyFacilities().evidences());
        validateDataSources(content.dataSources());
        validateMetrics(content.targetPopulationAnalysis().age());
        validateMetrics(content.targetPopulationAnalysis().gender());
        validateMetrics(content.footTrafficAnalysis().byTime());
        validateMetrics(content.footTrafficAnalysis().byDay());
        validateFacilities(content.nearbyFacilities().competitors());
        validateFacilities(content.nearbyFacilities().convenienceFacilities());
        validateFacilities(content.nearbyFacilities().transportFacilities());
        validateRecommendedPlaces(content.recommendedPlaces());
        requireText(content.targetPopulationAnalysis().derivedFromPlace(),
                "targetPopulationAnalysis.derivedFromPlace");

        boolean noCoreData = content.overallLocationEvaluation().evidences().isEmpty()
                && content.targetPopulationAnalysis().evidences().isEmpty()
                && content.footTrafficAnalysis().evidences().isEmpty()
                && content.nearbyFacilities().evidences().isEmpty()
                && content.targetPopulationAnalysis().age().isEmpty()
                && content.targetPopulationAnalysis().gender().isEmpty()
                && content.footTrafficAnalysis().total() == null
                && content.footTrafficAnalysis().byTime().isEmpty()
                && content.footTrafficAnalysis().byDay().isEmpty()
                && content.nearbyFacilities().competitors().isEmpty()
                && content.nearbyFacilities().convenienceFacilities().isEmpty()
                && content.nearbyFacilities().transportFacilities().isEmpty()
                && content.recommendedPlaces().isEmpty();
        if (noCoreData && content.overallLocationEvaluation().grade()
                != LocationAnalysisContent.Grade.INSUFFICIENT_DATA) {
            invalid();
        }
        if (content.overallLocationEvaluation().grade()
                == LocationAnalysisContent.Grade.INSUFFICIENT_DATA
                && content.limitations().isEmpty()) {
            invalid();
        }
        if (content.overallLocationEvaluation().grade()
                != LocationAnalysisContent.Grade.INSUFFICIENT_DATA
                && (content.recommendedPlaces().isEmpty()
                || content.targetPopulationAnalysis().age().isEmpty()
                || content.targetPopulationAnalysis().gender().isEmpty()
                || (content.footTrafficAnalysis().total() == null
                && content.footTrafficAnalysis().byTime().isEmpty()
                && content.footTrafficAnalysis().byDay().isEmpty())
                || "데이터 없음".equals(content.targetPopulationAnalysis().derivedFromPlace()))) {
            invalid();
        }
    }

    private void validateScope(LocationAnalysisRequest request, LocationAnalysisContent.AnalysisScope scope) {
        requireText(scope.requestedRegion(), "analysisScope.requestedRegion");
        requireText(scope.normalizedRegion(), "analysisScope.normalizedRegion");
        if (!scope.requestedRegion().equals(request.getRegion()) || scope.scopeLevel() == null
                || (scope.radiusMeters() != null && scope.radiusMeters() < 0)) {
            invalid();
        }
    }

    private void validateEvidence(List<LocationAnalysisContent.Evidence> evidences) {
        for (LocationAnalysisContent.Evidence evidence : evidences) {
            requireText(evidence.id(), "evidence.id");
            requireText(evidence.source(), "evidence.source");
            requireText(evidence.reference(), "evidence.reference");
            requireText(evidence.description(), "evidence.description");
            if (evidence.type() == null || (evidence.type() == LocationAnalysisContent.EvidenceType.CALCULATION
                    && (!StringUtils.hasText(evidence.formula()) || evidence.sourceValues().isEmpty()))) {
                invalid();
            }
        }
    }

    private void validateDataSources(List<LocationAnalysisContent.DataSource> sources) {
        for (LocationAnalysisContent.DataSource source : sources) {
            requireText(source.id(), "dataSources.id");
            requireText(source.source(), "dataSources.source");
            requireText(source.reference(), "dataSources.reference");
            if (source.type() == null) {
                invalid();
            }
        }
    }

    private void validateMetrics(List<LocationAnalysisContent.Metric> metrics) {
        for (LocationAnalysisContent.Metric metric : metrics) {
            requireText(metric.label(), "metric.label");
            requireText(metric.unit(), "metric.unit");
            if (metric.value() != null && metric.value() < 0) {
                invalid();
            }
            if (metric.sharePercent() != null && (metric.sharePercent() < 0 || metric.sharePercent() > 100)) {
                invalid();
            }
        }
    }

    private void validateFacilities(List<LocationAnalysisContent.Facility> facilities) {
        for (LocationAnalysisContent.Facility facility : facilities) {
            requireText(facility.name(), "facility.name");
            requireText(facility.category(), "facility.category");
            if (facility.distanceMeters() != null && facility.distanceMeters() < 0) {
                invalid();
            }
        }
    }

    private void validateRecommendedPlaces(List<LocationAnalysisContent.RecommendedPlace> places) {
        for (LocationAnalysisContent.RecommendedPlace place : places) {
            if (place.rank() == null || place.rank() < 1) {
                invalid();
            }
            requireText(place.name(), "recommendedPlaces.name");
            requireText(place.address(), "recommendedPlaces.address");
            if (place.latitude() != null && (place.latitude() < -90 || place.latitude() > 90)) {
                invalid();
            }
            if (place.longitude() != null && (place.longitude() < -180 || place.longitude() > 180)) {
                invalid();
            }
            if (place.score() != null && (place.score() < 0 || place.score() > 100)) {
                invalid();
            }
        }
    }

    private void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            invalid();
        }
    }

    private void invalid() {
        throw new AnalysisReportException(AnalysisReportErrorCode.AI_RESPONSE_INVALID, null);
    }
}
