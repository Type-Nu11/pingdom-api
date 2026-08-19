package com.typenull.pingdom.fixture.performance;

import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationDisputeStatus;
import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationReportReasonType;
import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationReportStatus;
import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationReportTargetType;

public record FixtureReport(
        long id,
        long placeId,
        long reporterUserId,
        Long disputedByUserId,
        PlaceInformationReportTargetType targetType,
        PlaceInformationReportReasonType reasonType,
        PlaceInformationReportStatus status,
        PlaceInformationDisputeStatus disputeStatus,
        String diagnosticReason
) {
}
