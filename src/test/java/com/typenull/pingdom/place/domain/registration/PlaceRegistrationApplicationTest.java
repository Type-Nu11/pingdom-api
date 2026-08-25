package com.typenull.pingdom.place.domain.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.typenull.pingdom.place.domain.registration.PlaceRegistrationApplication;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationAttachment;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationAttachmentType;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationCategory;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationStatus;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationTag;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
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
        assertThatThrownBy(() -> draft().submit(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void submitsWithStructuredAttachmentsAndTags() {
        PlaceRegistrationApplication application = PlaceRegistrationApplication.draft(1L, "태그 장소",
                PlaceRegistrationCategory.CAFE, 35.1, 128.1, "도로명 주소", "지번 주소", "12345",
                "장소 설명", Set.of(PlaceRegistrationTag.GOOD_AMBIENCE, PlaceRegistrationTag.ENGLISH_MENU_AVAILABLE), NOW);
        application.replaceAttachments(List.of(
                attachment(application, PlaceRegistrationAttachmentType.BUSINESS_REGISTRATION, "business"),
                attachment(application, PlaceRegistrationAttachmentType.IDENTITY_DOCUMENT, "identity"),
                attachment(application, PlaceRegistrationAttachmentType.REPRESENTATIVE_IMAGE, "image-1")), NOW);

        application.submit(NOW, "a".repeat(64));

        assertThat(application.getStatus()).isEqualTo(PlaceRegistrationStatus.PENDING);
        assertThat(application.getTags()).containsExactlyInAnyOrder(PlaceRegistrationTag.GOOD_AMBIENCE,
                PlaceRegistrationTag.ENGLISH_MENU_AVAILABLE);
        assertThat(application.getSubmissionVersion()).isEqualTo(1L);
        assertThat(application.hasRequiredAttachments()).isTrue();
    }

    @Test
    void doesNotPersistDynamicCommerceTagsInRegistrationApplication() {
        PlaceRegistrationApplication application = PlaceRegistrationApplication.draft(1L, "동적 태그 장소",
                PlaceRegistrationCategory.CAFE, 35.1, 128.1, "도로명 주소", "지번 주소", "12345",
                "장소 설명", Set.of(
                        PlaceRegistrationTag.ENGLISH_MENU_AVAILABLE,
                        PlaceRegistrationTag.RESERVATION_AVAILABLE,
                        PlaceRegistrationTag.RESERVATION_COUPON_AVAILABLE,
                        PlaceRegistrationTag.GENERAL_COUPON_AVAILABLE), NOW);

        assertThat(application.getTags())
                .containsExactly(PlaceRegistrationTag.ENGLISH_MENU_AVAILABLE);
    }

    @Test
    void doesNotSubmitWhenSensitiveAttachmentIsDuplicated() {
        PlaceRegistrationApplication application = draft();
        assertThatThrownBy(() -> application.replaceAttachments(List.of(
                attachment(application, PlaceRegistrationAttachmentType.BUSINESS_REGISTRATION, "business-1"),
                attachment(application, PlaceRegistrationAttachmentType.BUSINESS_REGISTRATION, "business-2"),
                attachment(application, PlaceRegistrationAttachmentType.IDENTITY_DOCUMENT, "identity"),
                attachment(application, PlaceRegistrationAttachmentType.REPRESENTATIVE_IMAGE, "image")), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOutOfRangeCoordinates() {
        assertThatThrownBy(() -> PlaceRegistrationApplication.draft(1L, "잘못된 장소",
                PlaceRegistrationCategory.CAFE, 91, 128.1, "도로명 주소", "지번 주소", "12345",
                "장소 설명", NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidStateTransitionsAndRegistrationReuse() {
        PlaceRegistrationApplication application = draft();
        application.attachFileIds("business-file", "identity-file", "image-file", NOW);
        application.submit(NOW);
        application.cancel(NOW);

        assertThatThrownBy(() -> application.approve(99L, "승인", NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> application.register(10L, NOW)).isInstanceOf(IllegalStateException.class);

        PlaceRegistrationApplication approved = draft();
        approved.attachFileIds("business-file", "identity-file", "image-file", NOW);
        approved.submit(NOW);
        approved.approve(99L, "승인", NOW);
        approved.register(10L, NOW);

        assertThatThrownBy(() -> approved.register(11L, NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsReopenFromNonRejectedState() {
        assertThatThrownBy(() -> draft().reopen(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void completesExistingPlaceClaimWithoutCreatingAnotherPlaceRegistration() {
        PlaceRegistrationApplication application = draft();
        application.configureMerchantSubmission(
                MerchantPlaceApplicationType.EXISTING_PLACE_CLAIM,
                "홍길동", "핑덤카페", "encrypted-registration-number", "핑덤카페",
                "owner@pingdom.test", "사업자 소개", "+821012345678", 30L, 20L, "운영권 이전", NOW
        );
        application.attachFileIds("business-file", "identity-file", "image-file", NOW);
        application.submit(NOW);
        application.approve(99L, "확인 완료", NOW);
        application.complete(30L, NOW);

        assertThat(application.getStatus()).isEqualTo(PlaceRegistrationStatus.COMPLETED);
        assertThat(application.getCompletedPlaceId()).isEqualTo(30L);
        assertThat(application.getRegisteredPlaceId()).isNull();
    }

    @Test
    void claimSubmissionRejectsExpiredSensitiveAttachmentAndRefreshesOwnerSnapshotOnlyWhileDraft() {
        PlaceRegistrationApplication application = draft();
        application.configureMerchantSubmission(
                MerchantPlaceApplicationType.EXISTING_PLACE_CLAIM,
                "홍길동", "핑덤카페", "encrypted-registration-number", "핑덤카페",
                "owner@pingdom.test", "사업자 소개", "+821012345678", 30L, 20L, "운영권 이전", NOW
        );
        application.replaceAttachments(List.of(
                PlaceRegistrationAttachment.create(application, null, PlaceRegistrationAttachmentType.BUSINESS_REGISTRATION,
                        "registration/business", "business.jpg", "image/jpeg", 1_024, "b".repeat(64), 1L,
                        NOW, NOW.minusDays(1), 0),
                attachment(application, PlaceRegistrationAttachmentType.IDENTITY_DOCUMENT, "identity"),
                attachment(application, PlaceRegistrationAttachmentType.REPRESENTATIVE_IMAGE, "image")), NOW);

        application.refreshClaimOwnershipSnapshot(21L, NOW);

        assertThat(application.getPreviousOwnerUserId()).isEqualTo(21L);
        assertThatThrownBy(() -> application.submit(NOW)).isInstanceOf(IllegalStateException.class);
    }

    private PlaceRegistrationAttachment attachment(PlaceRegistrationApplication application,
                                                   PlaceRegistrationAttachmentType type, String key) {
        return PlaceRegistrationAttachment.create(application, key, type, "registration/" + key,
                key + ".jpg", "image/jpeg", 1_024, "a".repeat(64), 1L, NOW, null, 0);
    }

    private PlaceRegistrationApplication draft() {
        return PlaceRegistrationApplication.draft(1L, "테스트 장소", PlaceRegistrationCategory.CAFE,
                35.1, 128.1, "도로명 주소", "지번 주소", "12345", "장소 설명", NOW);
    }
}
