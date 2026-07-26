package com.typenull.pingdom.campaign.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "merchant_brand")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MerchantBrand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "merchant_brand_id")
    private Long id;

    @Column(name = "merchant_owner_user_id", nullable = false)
    private Long merchantOwnerUserId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public static MerchantBrand create(
            Long ownerId,
            String name,
            String description,
            String logoUrl,
            LocalDateTime now
    ) {
        MerchantBrand brand = new MerchantBrand();
        brand.merchantOwnerUserId = Objects.requireNonNull(ownerId, "ownerId must not be null");
        brand.name = requireText(name, 100, "브랜드명");
        brand.description = optionalText(description, 1000, "브랜드 설명");
        brand.logoUrl = optionalText(logoUrl, 500, "브랜드 로고 URL");
        brand.createdAt = Objects.requireNonNull(now, "now must not be null");
        brand.updatedAt = now;
        return brand;
    }

    public void update(String name, String description, String logoUrl, LocalDateTime now) {
        this.name = requireText(name, 100, "브랜드명");
        this.description = optionalText(description, 1000, "브랜드 설명");
        this.logoUrl = optionalText(logoUrl, 500, "브랜드 로고 URL");
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    private static String requireText(String value, int maxLength, String field) {
        String normalized = optionalText(value, maxLength, field);
        if (normalized == null) {
            throw new IllegalArgumentException(field + "은 비어 있을 수 없습니다.");
        }
        return normalized;
    }

    private static String optionalText(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " 길이가 허용 범위를 초과했습니다.");
        }
        return normalized;
    }
}
