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

    /** 필수 첨부를 갖춘 신청이 반려·재개·재제출·승인·완료 흐름을 거쳐 장소 ID를 저장하는지 확인. */
    @Test
    void completesReopenedApplication() {
        PlaceRegistrationApplication application = draft();
        attachRequiredFiles(application);
        application.submit(NOW);
        application.reject(99L, "주소 확인 필요", NOW);
        application.reopen(NOW);
        attachRequiredFiles(application);
        application.submit(NOW);
        application.approve(99L, "확인 완료", NOW);
        application.complete(10L, NOW);

        assertThat(application.getStatus()).isEqualTo(PlaceRegistrationStatus.COMPLETED);
        assertThat(application.getCompletedPlaceId()).isEqualTo(10L);
    }

    /** 필수 첨부가 없는 초안을 제출하면 상태 오류가 발생하는지 확인. */
    @Test
    void rejectsSubmitWithoutRequiredFiles() {
        assertThatThrownBy(() -> draft().submit(NOW)).isInstanceOf(IllegalStateException.class);
    }

    /** 구조화된 세 필수 첨부와 정적 태그를 제출하면 PENDING과 제출 버전 1, 필수 첨부 충족 상태가 반영되는지 확인. */
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

    /** 예약·쿠폰 가능 여부 같은 동적 태그는 신청 저장 목록에서 제외하고 영어 메뉴 정적 태그만 남기는지 확인. */
    @Test
    void filtersDynamicCommerceTags() {
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

    /** 사업자등록증 첨부가 중복되면 첨부 교체 단계에서 거부하는지 확인. 검증 범위는 제출 전 첨부 교체 단계로 한정. */
    @Test
    void rejectsDuplicateSensitiveAttachments() {
        PlaceRegistrationApplication application = draft();
        assertThatThrownBy(() -> application.replaceAttachments(List.of(
                attachment(application, PlaceRegistrationAttachmentType.BUSINESS_REGISTRATION, "business-1"),
                attachment(application, PlaceRegistrationAttachmentType.BUSINESS_REGISTRATION, "business-2"),
                attachment(application, PlaceRegistrationAttachmentType.IDENTITY_DOCUMENT, "identity"),
                attachment(application, PlaceRegistrationAttachmentType.REPRESENTATIVE_IMAGE, "image")), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 허용 범위를 넘는 위도 91도로 신청 초안을 생성할 수 없는지 확인. */
    @Test
    void rejectsOutOfRangeCoordinates() {
        assertThatThrownBy(() -> PlaceRegistrationApplication.draft(1L, "잘못된 장소",
                PlaceRegistrationCategory.CAFE, 91, 128.1, "도로명 주소", "지번 주소", "12345",
                "장소 설명", NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    /** 취소된 신청은 승인·완료할 수 없고 이미 완료한 신청을 다른 장소로 재완료할 수 없는지 확인. */
    @Test
    void guardsApplicationTerminalTransitions() {
        PlaceRegistrationApplication application = draft();
        attachRequiredFiles(application);
        application.submit(NOW);
        application.cancel(NOW);

        assertThatThrownBy(() -> application.approve(99L, "승인", NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> application.complete(10L, NOW)).isInstanceOf(IllegalStateException.class);

        PlaceRegistrationApplication approved = draft();
        attachRequiredFiles(approved);
        approved.submit(NOW);
        approved.approve(99L, "승인", NOW);
        approved.complete(10L, NOW);

        assertThatThrownBy(() -> approved.complete(11L, NOW)).isInstanceOf(IllegalStateException.class);
    }

    /** 반려되지 않은 초안을 재개할 수 없는지 확인. */
    @Test
    void rejectsReopenFromNonRejectedState() {
        assertThatThrownBy(() -> draft().reopen(NOW)).isInstanceOf(IllegalStateException.class);
    }

    /** 기존 장소 운영권 신청을 승인·완료하면 지정 장소 ID와 COMPLETED 상태를 저장하는지 확인. 별도 장소 생성 여부는 검증 범위에서 제외. */
    @Test
    void completesExistingPlaceClaim() {
        PlaceRegistrationApplication application = draft();
        application.configureMerchantSubmission(
                MerchantPlaceApplicationType.EXISTING_PLACE_CLAIM,
                "홍길동", "핑덤카페", "encrypted-registration-number", "핑덤카페",
                "owner@pingdom.test", "사업자 소개", "+821012345678", 30L, 20L, "운영권 이전", NOW
        );
        attachRequiredFiles(application);
        application.submit(NOW);
        application.approve(99L, "확인 완료", NOW);
        application.complete(30L, NOW);

        assertThat(application.getStatus()).isEqualTo(PlaceRegistrationStatus.COMPLETED);
        assertThat(application.getCompletedPlaceId()).isEqualTo(30L);
    }

    /** 초안의 이전 소유자 스냅샷을 갱신하고 기한이 지난 사업자등록증이 포함된 신청 제출은 거부하는지 확인. */
    @Test
    void rejectsExpiredClaimAttachment() {
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

    /** 전달된 유형·key의 첨부 메타데이터를 만들며 만료 시각은 생략. 실제 파일 업로드는 없음. */
    private PlaceRegistrationAttachment attachment(PlaceRegistrationApplication application,
                                                   PlaceRegistrationAttachmentType type, String key) {
        return PlaceRegistrationAttachment.create(application, key, type, "registration/" + key,
                key + ".jpg", "image/jpeg", 1_024, "a".repeat(64), 1L, NOW, null, 0);
    }

    /** 사업자등록증·신분증·대표 이미지 세 가지를 신청에 연결해 제출 전제조건을 구비. */
    private void attachRequiredFiles(PlaceRegistrationApplication application) {
        application.replaceAttachments(List.of(
                attachment(application, PlaceRegistrationAttachmentType.BUSINESS_REGISTRATION, "business"),
                attachment(application, PlaceRegistrationAttachmentType.IDENTITY_DOCUMENT, "identity"),
                attachment(application, PlaceRegistrationAttachmentType.REPRESENTATIVE_IMAGE, "image")
        ), NOW);
    }

    /** 고정 신청자·좌표·주소의 카페 등록 초안을 생성. */
    private PlaceRegistrationApplication draft() {
        return PlaceRegistrationApplication.draft(1L, "테스트 장소", PlaceRegistrationCategory.CAFE,
                35.1, 128.1, "도로명 주소", "지번 주소", "12345", "장소 설명", NOW);
    }
}
