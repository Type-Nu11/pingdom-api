package com.typenull.pingdom.place.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.typenull.pingdom.place.domain.registration.PlaceRegistrationApplication;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationCategory;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PlaceRegistrationApplicationTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 13, 0, 0);

    @Test
    void supportsSubmitRejectReopenAndRegisterFlow() {
        PlaceRegistrationApplication application = draft();
        application.attachFileIds("business-file", "identity-file", "image-file", NOW);
        application.submit(NOW);
        application.reject(99L, "주소 확인 필요", NOW);
        application.reopen(NOW);
        application.attachFileIds("business-file", "identity-file", "image-file", NOW);
        application.submit(NOW);
        application.approve(99L, "확인 완료", NOW);
        application.register(10L, NOW);

        assertThat(application.getStatus()).isEqualTo(PlaceRegistrationStatus.REGISTERED);
        assertThat(application.getRegisteredPlaceId()).isEqualTo(10L);
    }

    @Test
    void rejectsSubmitWithoutRequiredFiles() {
        PlaceRegistrationApplication application = draft();

        assertThatThrownBy(() -> application.submit(NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsInvalidStateTransitionsAndRegistrationReuse() {
        PlaceRegistrationApplication application = draft();
        application.attachFileIds("business-file", "identity-file", "image-file", NOW);
        application.submit(NOW);
        application.cancel(NOW);

        assertThatThrownBy(() -> application.approve(99L, "승인", NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> application.register(10L, NOW))
                .isInstanceOf(IllegalStateException.class);

        PlaceRegistrationApplication approved = draft();
        approved.attachFileIds("business-file", "identity-file", "image-file", NOW);
        approved.submit(NOW);
        approved.approve(99L, "승인", NOW);
        approved.register(10L, NOW);

        assertThatThrownBy(() -> approved.register(11L, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsReopenFromNonRejectedState() {
        PlaceRegistrationApplication application = draft();

        assertThatThrownBy(() -> application.reopen(NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    private PlaceRegistrationApplication draft() {
        return PlaceRegistrationApplication.draft(1L, "테스트 장소", PlaceRegistrationCategory.CAFE,
                35.1, 128.1, "도로명 주소", "지번 주소", "12345", "장소 설명", NOW);
    }
}
