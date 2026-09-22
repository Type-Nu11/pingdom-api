package com.typenull.pingdom.place.application.service.registration;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMember;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceMemberRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.shared.security.access.UserAccessStatusService;
import com.typenull.pingdom.place.api.dto.registration.PlaceRegistrationRequest;
import com.typenull.pingdom.place.domain.exception.PlaceRegistrationErrorCode;
import com.typenull.pingdom.place.domain.exception.PlaceRegistrationException;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationApplication;
import com.typenull.pingdom.place.domain.registration.MerchantPlaceApplicationType;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationAttachment;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationStatus;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.registration.PlaceRegistrationApplicationRepository;
import com.typenull.pingdom.place.application.service.recommendation.snapshot.PlaceRecommendationSnapshotService;
import com.typenull.pingdom.place.application.service.localhot.PlaceAdministrativeRegionService;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.geocoding.GeocodingSource;
import java.time.Clock;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.HexFormat;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.place.api.dto.registration.PlaceRegistrationOperatingDay;
import com.typenull.pingdom.place.domain.place.operating.PlaceRegularOperatingHour;
import com.typenull.pingdom.place.domain.place.operating.PlaceRegularOperatingBreakTime;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 통합 Merchant 신청 서비스와 협력하여 신규 장소 초안을 편집·제출하고 승인된 신청을 운영 장소로 변환.
 * 기존 신청 변경 진입점은 신청 행의 쓰기 잠금과 신청자·유형 검사를 거치며 심사 권한 검사는 호출 측에서 수행.
 */
@Service
@RequiredArgsConstructor
public class PlaceRegistrationService {
    private static final GeometryFactory WGS84 = new GeometryFactory(new PrecisionModel(), 4326);
    private final PlaceRegistrationApplicationRepository repository;
    private final MapPlaceRepository placeRepository;
    private final PlaceAdministrativeRegionService placeAdministrativeRegionService;
    private final PlaceRecommendationSnapshotService snapshotService;
    private final MerchantOwnerProfileRepository profileRepository;
    private final MerchantOwnerPlaceRepository ownerPlaceRepository;
    private final MerchantPlaceMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final UserAccessStatusService userAccessStatusService;
    private final PlaceRegistrationMediaPromotionService mediaPromotionService;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    /**
     * 신규 장소의 기본 정보와 정규화된 국제 전화번호·7일 영업 일정을 초안으로 저장하고 신청 ID를 반환.
     * 입력 장소 정보·시간대·일정이 유효해야 하며 사업자 검증 정보 반영과 제출은 통합 신청 서비스가 이어서 수행.
     */
    @Transactional
    public Long createForUnifiedApplication(Long userId, PlaceRegistrationRequest r) {
        LocalDateTime now = now();
        PlaceRegistrationApplication application = PlaceRegistrationApplication.merchantPlaceDraft(userId, r.placeName(), r.category(), r.latitude(), r.longitude(),
                r.roadAddress(), r.jibunAddress(), r.postalCode(), r.description(), r.tags(), now);
        application.updateContactPhones(normalizePhone(r.businessContactPhone()), normalizePhone(r.applicantContactPhone()));
        updateOperatingSchedule(application, r, now);
        return repository.save(application).getId();
    }

    /**
     * 신청 행을 쓰기 잠금으로 읽어 본인의 NEW_PLACE 초안만 기본 정보·연락처·영업 일정으로 교체.
     * 신청 부재·유형 불일치는 APPLICATION_NOT_FOUND, 다른 신청자는 ACCESS_DENIED로 거절.
     * 초안이 아니거나 일정이 잘못되면 INVALID_STATE, 갱신 중 입력 형식 오류는 INVALID_ATTACHMENT_METADATA로 변환.
     * 별도 save 없이 현재 트랜잭션의 변경 감지로 반영하며 제출·심사 상태는 유지.
     */
    @Transactional
    public void updateForUnifiedApplication(Long userId, Long id, PlaceRegistrationRequest r) {
        PlaceRegistrationApplication application = mine(userId, id);
        requireUnifiedNewPlace(application);
        updateDraft(application, userId, r);
    }

    /**
     * 초안의 장소 입력·정규화 연락처·7일 영업 일정을 같은 관리 객체에 반영.
     * 입력 형식 오류는 INVALID_ATTACHMENT_METADATA, 도메인 상태 오류는 INVALID_STATE로 변환하며 소유권·유형 검증은 호출자가 선행.
     */
    private void updateDraft(PlaceRegistrationApplication a, Long userId, PlaceRegistrationRequest r) {
        try {
            LocalDateTime now = now();
            a.update(r.placeName(), r.category(), r.latitude(), r.longitude(), r.roadAddress(), r.jibunAddress(), r.postalCode(), r.description(), r.tags(), now);
            a.updateContactPhones(normalizePhone(r.businessContactPhone()), normalizePhone(r.applicantContactPhone()));
            updateOperatingSchedule(a, r, now);
        } catch (IllegalArgumentException e) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_ATTACHMENT_METADATA);
        } catch (IllegalStateException e) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_STATE);
        }
    }

    /**
     * 본인의 NEW_PLACE 신청 행을 잠그고 DRAFT 상태와 보존 기한 내 필수 첨부를 검증하여 PENDING으로 전이.
     * 제출 시각·횟수·내용 해시를 갱신하며 장소 생성은 미수행. 사업자 정보 검증은 호출 측에서 선행.
     * 제출 실패 시 첨부 존재 여부를 다시 확인해 REQUIRED_FILES_MISSING 또는 INVALID_STATE로 변환.
     */
    @Transactional
    public void submitForUnifiedApplication(Long userId, Long id) {
        PlaceRegistrationApplication application = mine(userId, id);
        requireUnifiedNewPlace(application);
        submitDraft(application);
    }

    private void submitDraft(PlaceRegistrationApplication a) {
        try { a.submit(now(), contentHash(a)); } catch (IllegalStateException e) {
            throw new PlaceRegistrationException(a.hasRequiredFiles() ? PlaceRegistrationErrorCode.INVALID_STATE : PlaceRegistrationErrorCode.REQUIRED_FILES_MISSING);
        }
    }

    /**
     * 신청 행을 잠가 신청자·NEW_PLACE 유형·APPROVED 상태를 확인한 뒤 생성된 장소 ID를 반환.
     * 이름·도로명 주소·좌표 중복을 검사하고 프로필과 사용자 행도 쓰기 잠금으로 읽어 프로필·역할을 활성화.
     * 호출자의 승인 트랜잭션에 참여하여 장소·공개 미디어·영업 일정·소유권·OWNER 멤버십·추천 스냅샷을 저장하며,
     * 신청의 COMPLETED 전이는 호출 측이 수행. 중복 사전 조회는 서로 다른 신청의 동시 생성에 대한 잠금 없이 수행.
     * 접근 상태 로컬 캐시는 즉시 제거됨. S3 복사는 DB 트랜잭션 밖의 부작용이며 활성 트랜잭션 동기화가 있을 때만
     * 롤백 후 복사 객체 삭제를 예약. 삭제 실패는 로그로 남으므로 S3 정리까지의 원자성은 보장 범위에서 제외.
     */
    @Transactional
    public Long createApprovedPlaceForUnifiedApplication(Long userId, Long id) {
        PlaceRegistrationApplication application = mine(userId, id);
        requireUnifiedNewPlace(application);
        return createApprovedPlace(application, userId);
    }

    private Long createApprovedPlace(PlaceRegistrationApplication a, Long userId) {
        if (a.getStatus() != PlaceRegistrationStatus.APPROVED) throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_STATE);
        if (placeRepository.existsByNameAndAddressAndLatitudeAndLongitude(a.getPlaceName(), a.getRoadAddress(), a.getLatitude(), a.getLongitude())) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.DUPLICATE_PLACE);
        }
        MerchantOwnerProfile profile = profileRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new PlaceRegistrationException(PlaceRegistrationErrorCode.MERCHANT_PROFILE_REQUIRED));
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(() -> new PlaceRegistrationException(PlaceRegistrationErrorCode.ACCESS_DENIED));
        LocalDateTime now = now();
        try {
            // 기존 Merchant Owner의 신규 장소 신청에서 이미 활성화된 프로필은 재심사 대상에서 제외.
            // ACTIVE 프로필에 approve()를 호출하면 PENDING 상태 검증으로 409(INVALID_STATE)가 발생.
            if (profile.getStatus() == MerchantOwnerStatus.PENDING) {
                profile.approve(a.getReviewerUserId(), now);
            } else if (profile.getStatus() != MerchantOwnerStatus.ACTIVE) {
                throw new IllegalStateException("신규 장소를 승인할 수 없는 Merchant Owner 프로필 상태입니다.");
            }
            user.activateMerchantOwnerRole();
        } catch (IllegalStateException e) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.MERCHANT_PROFILE_REQUIRED);
        }
        // 로컬 캐시 제거는 즉시 수행되며 DB 롤백 시에도 이전 캐시 항목 복원은 미수행.
        userAccessStatusService.evict(userId);
        MapPlace place = MapPlace.builder().name(a.getPlaceName()).address(a.getRoadAddress())
                .roadAddress(a.getRoadAddress()).jibunAddress(a.getJibunAddress()).postalCode(a.getPostalCode())
                .description(a.getDescription())
                .category(a.getCategory().name()).latitude(a.getLatitude()).longitude(a.getLongitude())
                .location(point(a.getLatitude(), a.getLongitude())).userId(userId).registrant(user.getUsername())
                .geocodingSource(GeocodingSource.LEGACY).build();
        // tags·연락처는 현재 MapPlace의 공개 canonical field가 아니므로 신청/상점주 계약에만 보관.
        placeAdministrativeRegionService.synchronizeIfConfigured(place);
        place = placeRepository.save(place);
        mediaPromotionService.promote(place, a);
        place.replaceOperatingSchedule(toRegularHours(a), toBreakTimes(a), List.of());
        ownerPlaceRepository.save(MerchantOwnerPlace.builder().placeId(place.getId()).merchantOwnerUserId(userId).createdAt(now).build());
        memberRepository.save(MerchantPlaceMember.owner(place.getId(), userId, now));
        snapshotService.initialize(place.getId());
        return place.getId();
    }

    private PlaceRegistrationApplication mine(Long userId, Long id) {
        PlaceRegistrationApplication application = locked(id);
        if (!application.getApplicantUserId().equals(userId)) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.ACCESS_DENIED);
        }
        return application;
    }
    private PlaceRegistrationApplication locked(Long id) { return repository.findByIdForUpdate(id).orElseThrow(this::notFound); }
    private void requireUnifiedNewPlace(PlaceRegistrationApplication application) {
        if (application.getApplicationType() != MerchantPlaceApplicationType.NEW_PLACE) {
            throw notFound();
        }
    }
    private PlaceRegistrationException notFound() { return new PlaceRegistrationException(PlaceRegistrationErrorCode.APPLICATION_NOT_FOUND); }

    /**
     * 장소 기본 정보·태그·영업 일정·정렬한 첨부 메타데이터를 바탕으로 제출 내용 해시 생성.
     * 사업자 검증 정보와 연락처는 이 정규 문자열에 포함되지 않으며 심사 충돌 검사는 엔티티 version을 사용.
     */
    private String contentHash(PlaceRegistrationApplication application) {
        String canonical = application.getPlaceName() + "|" + application.getCategory() + "|"
                + application.getLatitude() + "|" + application.getLongitude() + "|"
                + application.getRoadAddress() + "|" + application.getJibunAddress() + "|"
                + application.getPostalCode() + "|" + application.getDescription() + "|"
                + application.getTags().stream().map(Enum::name).sorted().toList() + "|"
                + application.getTimezone() + "|" + application.getOperatingScheduleJson() + "|"
                + application.getAttachments().stream()
                .sorted(Comparator.comparing(PlaceRegistrationAttachment::getDocumentType)
                        .thenComparing(PlaceRegistrationAttachment::getDisplayOrder)
                        .thenComparing(PlaceRegistrationAttachment::getStorageKey))
                .map(attachment -> attachment.getDocumentType() + ":" + attachment.getStorageKey() + ":"
                        + attachment.getFileHash() + ":" + attachment.getDisplayOrder())
                .toList();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }
    /**
     * 시간대 누락은 Asia/Seoul로 채우고 서로 다른 7개 요일의 입력을 JSON으로 저장.
     * 휴게 구간은 같은 날의 OPEN 구간 내부에서만 허용하므로 자정을 넘는 영업 구간에는 등록 불가.
     */
    private void updateOperatingSchedule(PlaceRegistrationApplication application, PlaceRegistrationRequest request, LocalDateTime now) {
        String timezone = request.timezone() == null || request.timezone().isBlank() ? "Asia/Seoul" : request.timezone();
        try { ZoneId.of(timezone); } catch (Exception e) { throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_STATE); }
        List<PlaceRegistrationOperatingDay> days = request.operatingDays() == null ? List.of() : request.operatingDays();
        if (days.size() != 7 || days.stream().map(PlaceRegistrationOperatingDay::dayOfWeek).distinct().count() != 7) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_STATE);
        }
        for (PlaceRegistrationOperatingDay day : days) validateDay(day);
        try { application.updateOperatingSchedule(timezone, objectMapper.writeValueAsString(days), now); }
        catch (JsonProcessingException e) { throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_STATE); }
    }
    private void validateDay(PlaceRegistrationOperatingDay day) {
        if (day.status() == com.typenull.pingdom.place.domain.registration.PlaceRegistrationOperatingStatus.OPEN) {
            if (day.opensAt() == null || day.closesAt() == null || day.opensAt().equals(day.closesAt())) throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_STATE);
        } else if (day.status() == com.typenull.pingdom.place.domain.registration.PlaceRegistrationOperatingStatus.CLOSED) {
            if (day.opensAt() != null || day.closesAt() != null || day.breakTimes() != null && !day.breakTimes().isEmpty()) throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_STATE);
        }
        List<PlaceRegistrationOperatingDay.BreakTime> breaks = day.breakTimes() == null ? List.of() : day.breakTimes();
        for (int i = 0; i < breaks.size(); i++) {
            var b = breaks.get(i);
            if (b.opensAt().equals(b.closesAt()) || day.status() != com.typenull.pingdom.place.domain.registration.PlaceRegistrationOperatingStatus.OPEN
                    || !contains(day.opensAt(), day.closesAt(), b.opensAt(), b.closesAt())) throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_STATE);
            for (int j = i + 1; j < breaks.size(); j++) if (overlap(b.opensAt(), b.closesAt(), breaks.get(j).opensAt(), breaks.get(j).closesAt())) throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_STATE);
        }
    }
    private boolean contains(LocalTime start, LocalTime end, LocalTime innerStart, LocalTime innerEnd) { return start.isBefore(end) && !innerStart.isBefore(start) && !innerEnd.isAfter(end) && innerStart.isBefore(innerEnd); }
    private boolean overlap(LocalTime a, LocalTime b, LocalTime c, LocalTime d) { return a.isBefore(d) && c.isBefore(b); }
    private Set<PlaceRegularOperatingHour> toRegularHours(PlaceRegistrationApplication application) {
        try { List<PlaceRegistrationOperatingDay> days = objectMapper.readValue(application.getOperatingScheduleJson(), new TypeReference<>() {});
            Set<PlaceRegularOperatingHour> result = new java.util.LinkedHashSet<>();
            for (var day : days) if (day.status() == com.typenull.pingdom.place.domain.registration.PlaceRegistrationOperatingStatus.OPEN) result.add(PlaceRegularOperatingHour.of(day.dayOfWeek(), day.opensAt(), day.closesAt()));
            return result;
        } catch (Exception e) { throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_STATE); }
    }
    private Set<PlaceRegularOperatingBreakTime> toBreakTimes(PlaceRegistrationApplication application) {
        try { List<PlaceRegistrationOperatingDay> days = objectMapper.readValue(application.getOperatingScheduleJson(), new TypeReference<>() {});
            Set<PlaceRegularOperatingBreakTime> result = new java.util.LinkedHashSet<>();
            for (var day : days) for (var b : day.breakTimes() == null ? List.<PlaceRegistrationOperatingDay.BreakTime>of() : day.breakTimes())
                result.add(PlaceRegularOperatingBreakTime.of(day.dayOfWeek(), b.opensAt(), b.closesAt()));
            return result;
        } catch (Exception e) { throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_STATE); }
    }
    private LocalDateTime now() { return LocalDateTime.now(clock); }
    private String normalizePhone(String phone) {
        String normalized = phone == null ? "" : phone.replaceAll("[\\s-]", "");
        if (!normalized.matches("\\+[1-9]\\d{7,14}")) throw new IllegalArgumentException("전화번호 형식이 올바르지 않습니다.");
        return normalized;
    }
    private static Point point(double lat, double lon) { return WGS84.createPoint(new Coordinate(lon, lat)); }
}
