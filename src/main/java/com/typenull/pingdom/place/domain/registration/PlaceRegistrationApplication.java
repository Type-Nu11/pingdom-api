package com.typenull.pingdom.place.domain.registration;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

    @Column(name = "business_contact_phone", length = 20)
    private String businessContactPhone;

    @Column(name = "applicant_contact_phone", length = 20)
    private String applicantContactPhone;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "place_registration_application_tag",
            joinColumns = @JoinColumn(name = "application_id", nullable = false,
                    foreignKey = @ForeignKey(name = "fk_place_registration_tag_application")))
    @Enumerated(EnumType.STRING)
    @Column(name = "tag", nullable = false, length = 40)
    private Set<PlaceRegistrationTag> tags = new LinkedHashSet<>();

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("documentType ASC, displayOrder ASC, id ASC")
    private List<PlaceRegistrationAttachment> attachments = new ArrayList<>();

    // Legacy file IDs remain readable while #1104 attachment metadata is adopted.
    @Column(name = "business_registration_file_id", length = 100)
    private String businessRegistrationFileId;

    @Column(name = "identity_document_file_id", length = 100)
    private String identityDocumentFileId;

    @Column(name = "representative_image_file_ids", length = 2000)
    private String representativeImageFileIds;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone = "Asia/Seoul";

    @Column(name = "operating_schedule_json", nullable = false, columnDefinition = "TEXT")
    private String operatingScheduleJson = "[]";

    @Column(name = "review_reason", length = 500)
    private String reviewReason;

    @Column(name = "reviewer_user_id")
    private Long reviewerUserId;

    @Column(name = "registered_place_id", unique = true)
    private Long registeredPlaceId;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "submission_version", nullable = false)
    private long submissionVersion;

    @Column(name = "submission_content_hash", length = 64)
    private String submissionContentHash;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "registered_at")
    private LocalDateTime registeredAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

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
                                         String description, Set<PlaceRegistrationTag> tags, LocalDateTime now) {
        this.applicantUserId = applicantUserId;
        this.status = PlaceRegistrationStatus.DRAFT;
        this.createdAt = now;
        update(placeName, category, latitude, longitude, roadAddress, jibunAddress, postalCode, description, tags, now);
    }

    public static PlaceRegistrationApplication draft(Long applicantUserId, String placeName,
                                                     PlaceRegistrationCategory category, double latitude, double longitude,
                                                     String roadAddress, String jibunAddress, String postalCode,
                                                     String description, LocalDateTime now) {
        return draft(applicantUserId, placeName, category, latitude, longitude, roadAddress, jibunAddress,
                postalCode, description, Set.of(), now);
    }

    public static PlaceRegistrationApplication draft(Long applicantUserId, String placeName,
                                                     PlaceRegistrationCategory category, double latitude, double longitude,
                                                     String roadAddress, String jibunAddress, String postalCode,
                                                     String description, Set<PlaceRegistrationTag> tags, LocalDateTime now) {
        return new PlaceRegistrationApplication(applicantUserId, placeName, category, latitude, longitude,
                roadAddress, jibunAddress, postalCode, description, tags, now);
    }

    public void setContactPhones(String businessContactPhone, String applicantContactPhone) {
        if (status != PlaceRegistrationStatus.DRAFT) throw new IllegalStateException("초안 상태의 신청만 수정할 수 있습니다.");
        this.businessContactPhone = businessContactPhone;
        this.applicantContactPhone = applicantContactPhone;
    }

    public void setOperatingSchedule(String timezone, String operatingScheduleJson, LocalDateTime now) {
        if (status != PlaceRegistrationStatus.DRAFT) throw new IllegalStateException("초안 상태의 신청만 수정할 수 있습니다.");
        this.timezone = timezone;
        this.operatingScheduleJson = operatingScheduleJson;
        this.updatedAt = now;
    }

    public void update(String placeName, PlaceRegistrationCategory category, double latitude, double longitude,
                       String roadAddress, String jibunAddress, String postalCode, String description,
                       LocalDateTime now) {
        update(placeName, category, latitude, longitude, roadAddress, jibunAddress, postalCode, description,
                tags, now);
    }

    public void update(String placeName, PlaceRegistrationCategory category, double latitude, double longitude,
                       String roadAddress, String jibunAddress, String postalCode, String description,
                       Set<PlaceRegistrationTag> tags, LocalDateTime now) {
        if (status != PlaceRegistrationStatus.DRAFT) {
            throw new IllegalStateException("초안 상태의 신청만 수정할 수 있습니다.");
        }
        validatePlaceData(placeName, category, latitude, longitude, roadAddress, jibunAddress, postalCode, description);
        this.placeName = placeName.trim();
        this.category = category;
        this.latitude = latitude;
        this.longitude = longitude;
        this.roadAddress = roadAddress.trim();
        this.jibunAddress = jibunAddress.trim();
        this.postalCode = postalCode.trim();
        this.description = description.trim();
        replaceTags(tags);
        this.updatedAt = now;
    }

    public void replaceTags(Set<PlaceRegistrationTag> tags) {
        this.tags.clear();
        if (tags != null) {
            this.tags.addAll(tags);
        }
    }

    public Set<PlaceRegistrationTag> getTags() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(tags));
    }

    public List<PlaceRegistrationAttachment> getAttachments() {
        return Collections.unmodifiableList(new ArrayList<>(attachments));
    }

    public void replaceAttachments(List<PlaceRegistrationAttachment> attachments, LocalDateTime now) {
        if (status != PlaceRegistrationStatus.DRAFT) {
            throw new IllegalStateException("초안 상태의 신청만 파일을 연결할 수 있습니다.");
        }
        Set<PlaceRegistrationAttachmentType> requiredTypes = new LinkedHashSet<>();
        Set<Integer> representativeImageOrders = new LinkedHashSet<>();
        List<PlaceRegistrationAttachment> validatedAttachments = new ArrayList<>();
        if (attachments != null) {
            attachments.forEach(attachment -> {
                if (attachment == null || attachment.getApplication() != this) {
                    throw new IllegalArgumentException("첨부 파일의 신청 정보가 일치하지 않습니다.");
                }
                if (attachment.getDocumentType() != PlaceRegistrationAttachmentType.REPRESENTATIVE_IMAGE
                        && !requiredTypes.add(attachment.getDocumentType())) {
                    throw new IllegalArgumentException("사업자등록증과 신분증은 신청별 1개만 등록할 수 있습니다.");
                }
                if (attachment.getDocumentType() == PlaceRegistrationAttachmentType.REPRESENTATIVE_IMAGE
                        && !representativeImageOrders.add(attachment.getDisplayOrder())) {
                    throw new IllegalArgumentException("대표 이미지 정렬 순서는 중복될 수 없습니다.");
                }
                validatedAttachments.add(attachment);
            });
        }
        this.attachments.clear();
        this.attachments.addAll(validatedAttachments);
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
        submit(now, null);
    }

    public void submit(LocalDateTime now, String contentHash) {
        if (status != PlaceRegistrationStatus.DRAFT || !hasRequiredFiles()) {
            throw new IllegalStateException("필수 장소 정보와 파일이 있는 초안만 제출할 수 있습니다.");
        }
        status = PlaceRegistrationStatus.PENDING;
        submittedAt = now;
        submissionVersion++;
        submissionContentHash = contentHash;
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
        submittedAt = null;
        reviewedAt = null;
        submissionContentHash = null;
        updatedAt = now;
    }

    public void cancel(LocalDateTime now) {
        if (status != PlaceRegistrationStatus.DRAFT && status != PlaceRegistrationStatus.PENDING) {
            throw new IllegalStateException("초안 또는 심사 대기 신청만 취소할 수 있습니다.");
        }
        status = PlaceRegistrationStatus.CANCELED;
        canceledAt = now;
        updatedAt = now;
    }

    public void register(Long placeId, LocalDateTime now) {
        requireStatus(PlaceRegistrationStatus.APPROVED);
        if (placeId == null) {
            throw new IllegalArgumentException("등록 장소 ID가 필요합니다.");
        }
        status = PlaceRegistrationStatus.REGISTERED;
        registeredPlaceId = placeId;
        registeredAt = now;
        updatedAt = now;
    }

    public boolean hasRequiredFiles() {
        if (!attachments.isEmpty()) {
            return hasRequiredAttachments();
        }
        return businessRegistrationFileId != null && !businessRegistrationFileId.isBlank()
                && identityDocumentFileId != null && !identityDocumentFileId.isBlank()
                && representativeImageFileIds != null && !representativeImageFileIds.isBlank();
    }

    public boolean hasRequiredAttachments() {
        long businessRegistrationCount = attachments.stream().filter(PlaceRegistrationAttachment::isActive)
                .filter(attachment -> attachment.getDocumentType() == PlaceRegistrationAttachmentType.BUSINESS_REGISTRATION).count();
        long identityDocumentCount = attachments.stream().filter(PlaceRegistrationAttachment::isActive)
                .filter(attachment -> attachment.getDocumentType() == PlaceRegistrationAttachmentType.IDENTITY_DOCUMENT).count();
        long representativeImageCount = attachments.stream().filter(PlaceRegistrationAttachment::isActive)
                .filter(attachment -> attachment.getDocumentType() == PlaceRegistrationAttachmentType.REPRESENTATIVE_IMAGE).count();
        return businessRegistrationCount == 1 && identityDocumentCount == 1 && representativeImageCount >= 1;
    }

    private static void validatePlaceData(String placeName, PlaceRegistrationCategory category, double latitude,
                                          double longitude, String roadAddress, String jibunAddress,
                                          String postalCode, String description) {
        if (placeName == null || placeName.isBlank() || category == null
                || roadAddress == null || roadAddress.isBlank()
                || jibunAddress == null || jibunAddress.isBlank()
                || postalCode == null || postalCode.isBlank()
                || description == null || description.isBlank()) {
            throw new IllegalArgumentException("필수 장소 정보가 누락되었습니다.");
        }
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("위도·경도 범위가 올바르지 않습니다.");
        }
    }

    private void requireStatus(PlaceRegistrationStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("현재 신청 상태에서는 요청을 처리할 수 없습니다.");
        }
    }
}
