package com.typenull.pingdom.identity.domain;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import org.junit.jupiter.api.Test;

class ProfileImageUploadIntentTest {
    private static final Instant NOW = Instant.parse("2026-11-12T00:00:00Z");

    @Test
    void orphanCannotAttachOrDeleteBeforeFourteenDays() {
        var image = ProfileImageUploadIntent.create(1L, "jpg", NOW, Duration.ofMinutes(2));
        assertThat(image.ownsKey()).isTrue();
        assertThat(image.canComplete(1L, NOW)).isTrue();
        assertThat(image.canComplete(2L, NOW)).isFalse();
        assertThat(image.canComplete(1L, NOW.plus(Duration.ofMinutes(3)))).isFalse();
        image.orphaned(NOW);
        assertThat(image.canComplete(1L, NOW)).isFalse();
        assertThat(image.deletionDue(NOW.plus(Duration.ofDays(14)).minusNanos(1))).isFalse();
        assertThat(image.deletionDue(NOW.plus(Duration.ofDays(14)))).isTrue();
    }

    @Test
    void resumedReferenceInvalidatesDeleteAttemptAndRestartsGraceOnNextOrphan() {
        var image = ProfileImageUploadIntent.create(1L, "png", NOW, Duration.ofMinutes(2));
        image.orphaned(NOW);
        String attempt = image.queueDeletion(NOW.plus(Duration.ofDays(14)), Duration.ofDays(1));
        image.attached(NOW.plus(Duration.ofDays(14)), Duration.ofDays(1));
        assertThat(image.matchesAttempt(attempt)).isFalse();
        image.orphaned(NOW.plus(Duration.ofDays(15)));
        assertThat(image.deletionDue(NOW.plus(Duration.ofDays(28)))).isFalse();
    }
}
