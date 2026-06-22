package com.typenull.pingdom.moderation.domain.ad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AdminAdTest {

    private static final LocalDateTime START_AT = LocalDateTime.of(2026, 6, 20, 9, 0);
    private static final LocalDateTime END_AT = LocalDateTime.of(2026, 6, 30, 23, 59, 59);
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 6, 19, 12, 0);

    @Test
    void constructorRejectsBlankRequiredText() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> createAdminAd(" ", "https://cdn.pingdom.com/ad.png", "https://pingdom.com/event", START_AT, END_AT, CREATED_AT)
        );

        assertEquals("title은 필수이며 공백일 수 없습니다.", exception.getMessage());
    }

    @Test
    void constructorRejectsBlankImageUrl() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> createAdminAd("광고", null, "https://pingdom.com/event", START_AT, END_AT, CREATED_AT)
        );

        assertEquals("imageUrl은 필수이며 공백일 수 없습니다.", exception.getMessage());
    }

    @Test
    void constructorRejectsBlankRedirectUrl() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> createAdminAd("광고", "https://cdn.pingdom.com/ad.png", "", START_AT, END_AT, CREATED_AT)
        );

        assertEquals("redirectUrl은 필수이며 공백일 수 없습니다.", exception.getMessage());
    }

    @Test
    void constructorRejectsInvalidPeriod() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> createAdminAd("광고", "https://cdn.pingdom.com/ad.png", "https://pingdom.com/event", START_AT, START_AT, CREATED_AT)
        );

        assertEquals("종료 시각은 시작 시각보다 이후여야 합니다.", exception.getMessage());
    }

    @Test
    void constructorRejectsNullCreatedAt() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> createAdminAd("광고", "https://cdn.pingdom.com/ad.png", "https://pingdom.com/event", START_AT, END_AT, null)
        );

        assertEquals("createdAt은 필수입니다.", exception.getMessage());
    }

    private AdminAd createAdminAd(
            String title,
            String imageUrl,
            String redirectUrl,
            LocalDateTime startAt,
            LocalDateTime endAt,
            LocalDateTime createdAt
    ) {
        return AdminAd.builder()
                .title(title)
                .imageUrl(imageUrl)
                .redirectUrl(redirectUrl)
                .startAt(startAt)
                .endAt(endAt)
                .createdAt(createdAt)
                .build();
    }
}
