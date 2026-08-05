package com.typenull.pingdom.identity.domain.merchant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "merchant_place_information")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MerchantPlaceInformation {

    @Id
    @Column(name = "place_id")
    private Long placeId;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Column(name = "website_url", length = 500)
    private String websiteUrl;

    @Column(name = "reservation_url", length = 500)
    private String reservationUrl;

    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    private MerchantPlaceInformation(
            Long placeId,
            String description,
            String contactPhone,
            String websiteUrl,
            String reservationUrl,
            Long updatedByUserId,
            LocalDateTime now
    ) {
        this.placeId = Objects.requireNonNull(placeId, "placeId must not be null");
        this.updatedByUserId = updatedByUserId;
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
        apply(description, contactPhone, websiteUrl, reservationUrl);
    }

    public static MerchantPlaceInformation create(
            Long placeId,
            String description,
            String contactPhone,
            String websiteUrl,
            String reservationUrl,
            Long updatedByUserId,
            LocalDateTime now
    ) {
        return new MerchantPlaceInformation(
                placeId,
                description,
                contactPhone,
                websiteUrl,
                reservationUrl,
                updatedByUserId,
                now
        );
    }

    public void update(
            String description,
            String contactPhone,
            String websiteUrl,
            String reservationUrl,
            Long updatedByUserId,
            LocalDateTime now
    ) {
        apply(description, contactPhone, websiteUrl, reservationUrl);
        this.updatedByUserId = updatedByUserId;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    private void apply(
            String description,
            String contactPhone,
            String websiteUrl,
            String reservationUrl
    ) {
        this.description = trimToNull(description);
        this.contactPhone = trimToNull(contactPhone);
        this.websiteUrl = trimToNull(websiteUrl);
        this.reservationUrl = trimToNull(reservationUrl);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
