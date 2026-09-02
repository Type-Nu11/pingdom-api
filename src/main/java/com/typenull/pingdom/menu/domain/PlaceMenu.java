package com.typenull.pingdom.menu.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "place_menu")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceMenu {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_menu_id")
    private Long id;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "merchant_owner_user_id", nullable = false)
    private Long merchantOwnerUserId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "price_amount", nullable = false)
    private long priceAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private MenuCurrency currency;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlaceMenuStatus status;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    private PlaceMenu(Long placeId, Long ownerId, String name, String description, long priceAmount,
            MenuCurrency currency, String imageUrl, int displayOrder, LocalDateTime now) {
        this.placeId = Objects.requireNonNull(placeId, "placeId must not be null");
        this.merchantOwnerUserId = Objects.requireNonNull(ownerId, "ownerId must not be null");
        this.name = requireText(name, "name must not be blank");
        this.description = trimToNull(description);
        if (priceAmount <= 0) throw new IllegalArgumentException("priceAmount must be positive");
        this.priceAmount = priceAmount;
        this.currency = Objects.requireNonNull(currency, "currency must not be null");
        this.imageUrl = trimToNull(imageUrl);
        this.status = PlaceMenuStatus.AVAILABLE;
        this.displayOrder = Math.max(0, displayOrder);
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
    }

    public static PlaceMenu create(Long placeId, Long ownerId, String name, String description, long priceAmount,
            MenuCurrency currency, String imageUrl, int displayOrder, LocalDateTime now) {
        return new PlaceMenu(placeId, ownerId, name, description, priceAmount, currency, imageUrl, displayOrder, now);
    }

    public void update(String name, String description, long priceAmount, MenuCurrency currency, String imageUrl,
            LocalDateTime now) {
        this.name = requireText(name, "name must not be blank");
        this.description = trimToNull(description);
        if (priceAmount <= 0) throw new IllegalArgumentException("priceAmount must be positive");
        this.priceAmount = priceAmount;
        this.currency = Objects.requireNonNull(currency, "currency must not be null");
        this.imageUrl = trimToNull(imageUrl);
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void changeStatus(PlaceMenuStatus status, LocalDateTime now) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void changeDisplayOrder(int displayOrder, LocalDateTime now) {
        if (displayOrder < 0) throw new IllegalArgumentException("displayOrder must not be negative");
        this.displayOrder = displayOrder;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    private static String requireText(String value, String message) {
        String text = trimToNull(value);
        if (text == null) throw new IllegalArgumentException(message);
        return text;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
