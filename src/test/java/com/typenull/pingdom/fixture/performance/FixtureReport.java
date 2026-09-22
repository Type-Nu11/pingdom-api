package com.typenull.pingdom.fixture.performance;

import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationDisputeStatus;
import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationReportReasonType;
import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationReportStatus;
import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationReportTargetType;

/** 신고와 반박의 사용자 관계·대상·사유·처리 상태 및 진단 설명을 담는 성능 테스트 입력이다. */
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
