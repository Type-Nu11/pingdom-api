package com.typenull.pingdom.moderation.domain.ad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AdminAdTest {

    private static final LocalDateTime START_AT = LocalDateTime.of(2026, 6, 20, 9, 0);
    private static final LocalDateTime END_AT = LocalDateTime.of(2026, 6, 30, 23, 59, 59);
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 6, 19, 12, 0);

    /**
     * 공백 제목으로 광고를 만들면 제목 필수값 오류 메시지를 가진 IllegalArgumentException이 발생하는지 검증한다.
     */
    @Test
    void constructorRejectsBlankRequiredText() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> createAdminAd(" ", "https://cdn.pingdom.com/ad.png", "https://pingdom.com/event", START_AT, END_AT, CREATED_AT)
        );

        assertEquals("title은 필수이며 공백일 수 없습니다.", exception.getMessage());
    }

    /**
     * 이미지 URL이 null이면 광고 생성 시 이미지 URL 필수값 오류 메시지를 반환하는지 검증한다.
     */
    @Test
    void constructorRejectsBlankImageUrl() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> createAdminAd("광고", null, "https://pingdom.com/event", START_AT, END_AT, CREATED_AT)
        );

        assertEquals("imageUrl은 필수이며 공백일 수 없습니다.", exception.getMessage());
    }

    /**
     * 빈 리다이렉트 URL로 광고를 만들면 해당 필수값 오류 메시지를 반환하는지 검증한다.
     */
    @Test
    void constructorRejectsBlankRedirectUrl() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> createAdminAd("광고", "https://cdn.pingdom.com/ad.png", "", START_AT, END_AT, CREATED_AT)
        );

        assertEquals("redirectUrl은 필수이며 공백일 수 없습니다.", exception.getMessage());
    }

    /**
     * 광고 시작과 종료가 같은 시각이면 종료가 더 늦어야 한다는 오류로 생성을 거절하는지 검증한다.
     */
    @Test
    void constructorRejectsInvalidPeriod() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> createAdminAd("광고", "https://cdn.pingdom.com/ad.png", "https://pingdom.com/event", START_AT, START_AT, CREATED_AT)
        );

        assertEquals("종료 시각은 시작 시각보다 이후여야 합니다.", exception.getMessage());
    }

    /**
     * 생성 시각이 없는 광고는 createdAt 필수값 오류로 거절하는지 검증한다.
     */
    @Test
    void constructorRejectsNullCreatedAt() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> createAdminAd("광고", "https://cdn.pingdom.com/ad.png", "https://pingdom.com/event", START_AT, END_AT, null)
        );

        assertEquals("createdAt은 필수입니다.", exception.getMessage());
    }

    /**
     * 각 필수값과 기간을 바꾸어 생성 규칙을 검사할 수 있도록 광고 builder 호출을 모은다.
     */
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
