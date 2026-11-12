package com.typenull.pingdom.verification.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class VisitEvidenceUploadIntentTest {
    private static final Instant NOW = Instant.parse("2026-11-12T00:00:00Z");

    @Test
    void acceptsOnlyOwnedLiveUpload() {
        var intent = intent();
        assertThat(intent.ownsKey()).isTrue();
        assertThat(intent.canComplete(1L, 2L, NOW)).isTrue();
        assertThat(intent.canComplete(3L, 2L, NOW)).isFalse();
        assertThat(intent.canComplete(1L, 3L, NOW)).isFalse();
        assertThat(intent.canComplete(1L, 2L, intent.getUploadDeadline())).isFalse();
        intent.orphaned(NOW);
        assertThat(intent.canComplete(1L, 2L, NOW)).isFalse();
    }

    @Test
    void retainsOrphanForFourteenDaysFromFirstObservation() {
        var intent = intent();
        intent.orphaned(NOW);
        intent.orphaned(NOW.plusSeconds(60));
        assertThat(intent.getOrphanedAt()).isEqualTo(NOW);
        assertThat(intent.deletionDue(NOW.plus(Duration.ofDays(14)).minusNanos(1))).isFalse();
        assertThat(intent.deletionDue(NOW.plus(Duration.ofDays(14)))).isTrue();
    }

    @Test
    void attachmentInvalidatesQueuedDeletion() {
        var intent = intent();
        intent.orphaned(NOW);
        String attempt = intent.queueDeletion(NOW.plus(Duration.ofDays(14)), Duration.ofDays(1));
        intent.attached(NOW, Duration.ofDays(1));
        assertThat(intent.matchesAttempt(attempt)).isFalse();
        assertThat(intent.deletionDue(NOW.plus(Duration.ofDays(40)))).isFalse();
    }

    @Test
    void rejectsForeignPrefixAndDifferentIntentKey() {
        var intent = intent();
        ReflectionTestUtils.setField(intent, "s3Key", "profile/" + intent.getId() + ".jpg");
        assertThat(intent.ownsKey()).isFalse();
        assertThatThrownBy(() -> VisitEvidenceUploadIntent.create(1L, 2L, "../jpg", NOW, Duration.ofMinutes(2)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private VisitEvidenceUploadIntent intent() {
        return VisitEvidenceUploadIntent.create(1L, 2L, "jpg", NOW, Duration.ofMinutes(2));
    }
}
