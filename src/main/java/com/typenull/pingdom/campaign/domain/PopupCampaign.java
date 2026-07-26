package com.typenull.pingdom.campaign.domain;

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

@Entity
@Getter
@Table(name = "popup_campaign")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PopupCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "popup_campaign_id")
    private Long id;

    @Column(name = "merchant_brand_id", nullable = false)
    private Long brandId;

    @Column(name = "merchant_owner_user_id", nullable = false)
    private Long merchantOwnerUserId;

    @Column(name = "map_place_id", nullable = false)
    private Long placeId;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PopupCampaignStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public static PopupCampaign draft(
            Long brandId,
            Long ownerId,
            Long placeId,
            String title,
            String description,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            LocalDateTime now
    ) {
        validatePeriod(startsAt, endsAt);
        PopupCampaign campaign = new PopupCampaign();
        campaign.brandId = Objects.requireNonNull(brandId, "brandId must not be null");
        campaign.merchantOwnerUserId = Objects.requireNonNull(ownerId, "ownerId must not be null");
        campaign.placeId = Objects.requireNonNull(placeId, "placeId must not be null");
        campaign.title = requireText(title, 150, "캠페인명");
        campaign.description = requireText(description, 2000, "캠페인 설명");
        campaign.startsAt = startsAt;
        campaign.endsAt = endsAt;
        campaign.status = PopupCampaignStatus.DRAFT;
        campaign.createdAt = Objects.requireNonNull(now, "now must not be null");
        campaign.updatedAt = now;
        return campaign;
    }

    public void publish(LocalDateTime now) {
        if (status != PopupCampaignStatus.DRAFT || !endsAt.isAfter(now)) {
            throw new IllegalStateException("공개할 수 없는 캠페인 상태입니다.");
        }
        status = PopupCampaignStatus.PUBLISHED;
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void update(
            Long brandId,
            Long placeId,
            String title,
            String description,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            LocalDateTime now
    ) {
        if (status != PopupCampaignStatus.DRAFT) {
            throw new IllegalStateException("초안 캠페인만 수정할 수 있습니다.");
        }
        validatePeriod(startsAt, endsAt);
        this.brandId = Objects.requireNonNull(brandId, "brandId must not be null");
        this.placeId = Objects.requireNonNull(placeId, "placeId must not be null");
        this.title = requireText(title, 150, "캠페인명");
        this.description = requireText(description, 2000, "캠페인 설명");
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void close(LocalDateTime now) {
        if (status != PopupCampaignStatus.PUBLISHED) {
            throw new IllegalStateException("종료할 수 없는 캠페인 상태입니다.");
        }
        status = PopupCampaignStatus.CLOSED;
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    private static void validatePeriod(LocalDateTime startsAt, LocalDateTime endsAt) {
        if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("캠페인 종료 시각은 시작 시각보다 이후여야 합니다.");
        }
    }

    private static String requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(field + "이 올바르지 않습니다.");
        }
        return value.trim();
    }
}
