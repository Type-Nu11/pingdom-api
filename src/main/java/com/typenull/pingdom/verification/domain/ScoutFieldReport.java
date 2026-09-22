package com.typenull.pingdom.verification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Scout의 장소 현장 제보와 일회성 심사 결과를 보관한다.
 * 제출은 장소 데이터를 직접 수정하지 않으며 심사 권한과 활동 자격 확인은 서비스가 담당한다.
 */
@Entity
@Getter
@Table(name = "scout_field_report")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScoutFieldReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scout_user_id")
    private Long scoutUserId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 30)
    private ScoutFieldReportType reportType;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(name = "evidence_url", length = 500)
    private String evidenceUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScoutFieldReportStatus status;

    @Column(name = "reviewer_admin_user_id")
    private Long reviewerAdminUserId;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    /**
     * 필수 사용자·장소·유형·본문·시각으로 SUBMITTED 제보를 만든다.
     * 본문은 공백 제거 후 필수이고 선택 증빙 URL은 빈 값이면 null이다.
     */
    public static ScoutFieldReport submit(
            Long scoutUserId,
            Long placeId,
            ScoutFieldReportType reportType,
            String description,
            String evidenceUrl,
            LocalDateTime now
    ) {
        ScoutFieldReport report = new ScoutFieldReport();
        report.scoutUserId = Objects.requireNonNull(scoutUserId);
        report.placeId = Objects.requireNonNull(placeId);
        report.reportType = Objects.requireNonNull(reportType);
        report.description = requireText(description, "description");
        report.evidenceUrl = normalize(evidenceUrl);
        report.status = ScoutFieldReportStatus.SUBMITTED;
        report.createdAt = Objects.requireNonNull(now);
        report.updatedAt = now;
        return report;
    }

    /**
     * 미심사 제보만 승인 또는 거절할 수 있으며 거절에는 비어 있지 않은 사유가 필요하다.
     * 심사자와 심사 시각을 기록하되 이 메서드 자체는 관리자 권한을 검사하지 않는다.
     */
    public void review(
            Long adminUserId,
            ScoutFieldReportStatus decision,
            String reviewNote,
            LocalDateTime now
    ) {
        if (status != ScoutFieldReportStatus.SUBMITTED) {
            throw new IllegalStateException("제출 상태의 Scout 현장 제보만 심사할 수 있습니다.");
        }
        if (decision == null || decision == ScoutFieldReportStatus.SUBMITTED) {
            throw new IllegalArgumentException("심사 결과는 승인 또는 거절이어야 합니다.");
        }

        String normalizedNote = normalize(reviewNote);
        if (decision == ScoutFieldReportStatus.REJECTED && normalizedNote == null) {
            throw new IllegalArgumentException("거절 사유는 필수입니다.");
        }

        status = decision;
        reviewerAdminUserId = Objects.requireNonNull(adminUserId);
        this.reviewNote = normalizedNote;
        reviewedAt = Objects.requireNonNull(now);
        updatedAt = now;
    }

    /** 정규화 후 비어 있는 필수 본문을 인자 오류로 거부한다. */
    static String requireText(String value, String name) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    /** 선택 문자열의 앞뒤 공백을 제거하고 빈 값은 null로 통일한다. */
    static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
