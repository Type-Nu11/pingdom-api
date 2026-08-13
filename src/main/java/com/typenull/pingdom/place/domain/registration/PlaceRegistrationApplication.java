package com.typenull.pingdom.place.domain.registration;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "place_registration_application")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceRegistrationApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "applicant_user_id", nullable = false)
    private Long applicantUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlaceRegistrationStatus status;

    @Column(name = "place_name", nullable = false, length = 100)
    private String placeName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PlaceRegistrationCategory category;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "road_address", nullable = false, length = 255)
    private String roadAddress;

    @Column(name = "jibun_address", nullable = false, length = 255)
    private String jibunAddress;

    @Column(name = "postal_code", nullable = false, length = 20)
    private String postalCode;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(name = "business_registration_file_id", length = 100)
    private String businessRegistrationFileId;

    @Column(name = "identity_document_file_id", length = 100)
    private String identityDocumentFileId;

    @Column(name = "representative_image_file_ids", length = 2000)
    private String representativeImageFileIds;

    @Column(name = "review_reason", length = 500)
    private String reviewReason;

    @Column(name = "reviewer_user_id")
    private Long reviewerUserId;

    @Column(name = "registered_place_id", unique = true)
    private Long registeredPlaceId;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "registered_at")
    private LocalDateTime registeredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    private PlaceRegistrationApplication(Long applicantUserId, String placeName,
                                         PlaceRegistrationCategory category, double latitude, double longitude,
                                         String roadAddress, String jibunAddress, String postalCode,
                                         String description, LocalDateTime now) {
        this.applicantUserId = applicantUserId;
        this.status = PlaceRegistrationStatus.DRAFT;
        this.createdAt = now;
        update(placeName, category, latitude, longitude, roadAddress, jibunAddress, postalCode, description, now);
    }

    public static PlaceRegistrationApplication draft(Long applicantUserId, String placeName,
                                                     PlaceRegistrationCategory category, double latitude, double longitude,
                                                     String roadAddress, String jibunAddress, String postalCode,
                                                     String description, LocalDateTime now) {
        return new PlaceRegistrationApplication(applicantUserId, placeName, category, latitude, longitude,
                roadAddress, jibunAddress, postalCode, description, now);
    }

    public void update(String placeName, PlaceRegistrationCategory category, double latitude, double longitude,
                       String roadAddress, String jibunAddress, String postalCode, String description,
                       LocalDateTime now) {
        if (status != PlaceRegistrationStatus.DRAFT) {
            throw new IllegalStateException("초안 상태의 신청만 수정할 수 있습니다.");
        }
        this.placeName = placeName.trim();
        this.category = category;
        this.latitude = latitude;
        this.longitude = longitude;
        this.roadAddress = roadAddress.trim();
        this.jibunAddress = jibunAddress.trim();
        this.postalCode = postalCode.trim();
        this.description = description.trim();
        this.updatedAt = now;
    }

    public void attachFileIds(String businessRegistrationFileId, String identityDocumentFileId,
                              String representativeImageFileIds, LocalDateTime now) {
        if (status != PlaceRegistrationStatus.DRAFT) {
            throw new IllegalStateException("초안 상태의 신청만 파일을 연결할 수 있습니다.");
        }
        this.businessRegistrationFileId = businessRegistrationFileId;
        this.identityDocumentFileId = identityDocumentFileId;
        this.representativeImageFileIds = representativeImageFileIds;
        this.updatedAt = now;
    }

    public void submit(LocalDateTime now) {
        if (status != PlaceRegistrationStatus.DRAFT || !hasRequiredFiles()) {
            throw new IllegalStateException("필수 장소 정보와 파일이 있는 초안만 제출할 수 있습니다.");
        }
        status = PlaceRegistrationStatus.PENDING;
        submittedAt = now;
        updatedAt = now;
    }

    public void approve(Long reviewerUserId, String reason, LocalDateTime now) {
        requireStatus(PlaceRegistrationStatus.PENDING);
        status = PlaceRegistrationStatus.APPROVED;
        this.reviewerUserId = reviewerUserId;
        reviewReason = reason;
        reviewedAt = now;
        updatedAt = now;
    }

    public void reject(Long reviewerUserId, String reason, LocalDateTime now) {
        requireStatus(PlaceRegistrationStatus.PENDING);
        status = PlaceRegistrationStatus.REJECTED;
        this.reviewerUserId = reviewerUserId;
        reviewReason = reason;
        reviewedAt = now;
        updatedAt = now;
    }

    public void reopen(LocalDateTime now) {
        requireStatus(PlaceRegistrationStatus.REJECTED);
        status = PlaceRegistrationStatus.DRAFT;
        reviewReason = null;
        reviewerUserId = null;
        updatedAt = now;
    }

    public void cancel(LocalDateTime now) {
        if (status != PlaceRegistrationStatus.DRAFT && status != PlaceRegistrationStatus.PENDING) {
            throw new IllegalStateException("초안 또는 심사 대기 신청만 취소할 수 있습니다.");
        }
        status = PlaceRegistrationStatus.CANCELED;
        updatedAt = now;
    }

    public void register(Long placeId, LocalDateTime now) {
        requireStatus(PlaceRegistrationStatus.APPROVED);
        status = PlaceRegistrationStatus.REGISTERED;
        registeredPlaceId = placeId;
        registeredAt = now;
        updatedAt = now;
    }

    public boolean hasRequiredFiles() {
        return businessRegistrationFileId != null && !businessRegistrationFileId.isBlank()
                && identityDocumentFileId != null && !identityDocumentFileId.isBlank()
                && representativeImageFileIds != null && !representativeImageFileIds.isBlank();
    }

    private void requireStatus(PlaceRegistrationStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("현재 신청 상태에서는 요청을 처리할 수 없습니다.");
        }
    }
}
