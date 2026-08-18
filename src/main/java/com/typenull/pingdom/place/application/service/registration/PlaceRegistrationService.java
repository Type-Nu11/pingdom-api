package com.typenull.pingdom.place.application.service.registration;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMember;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceMemberRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.api.dto.registration.PlaceRegistrationPageResponse;
import com.typenull.pingdom.place.api.dto.registration.PlaceRegistrationAttachmentRequest;
import com.typenull.pingdom.place.api.dto.registration.PlaceRegistrationRequest;
import com.typenull.pingdom.place.api.dto.registration.PlaceRegistrationResponse;
import com.typenull.pingdom.place.api.dto.registration.PlaceRegistrationReviewRequest;
import com.typenull.pingdom.place.domain.exception.PlaceRegistrationErrorCode;
import com.typenull.pingdom.place.domain.exception.PlaceRegistrationException;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationApplication;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationAttachment;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationStatus;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.registration.PlaceRegistrationApplicationRepository;
import com.typenull.pingdom.place.application.service.recommendation.snapshot.PlaceRecommendationSnapshotService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlaceRegistrationService {
    private static final GeometryFactory WGS84 = new GeometryFactory(new PrecisionModel(), 4326);
    private final PlaceRegistrationApplicationRepository repository;
    private final MapPlaceRepository placeRepository;
    private final PlaceRecommendationSnapshotService snapshotService;
    private final MerchantOwnerProfileRepository profileRepository;
    private final MerchantOwnerPlaceRepository ownerPlaceRepository;
    private final MerchantPlaceMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    @Transactional
    public PlaceRegistrationResponse create(Long userId, PlaceRegistrationRequest r) {
        LocalDateTime now = now();
        PlaceRegistrationApplication application = PlaceRegistrationApplication.draft(userId, r.placeName(), r.category(), r.latitude(), r.longitude(),
                r.roadAddress(), r.jibunAddress(), r.postalCode(), r.description(), r.tags(), now);
        application.updateContactPhones(normalizePhone(r.businessContactPhone()), normalizePhone(r.applicantContactPhone()));
        updateOperatingSchedule(application, r, now);
        try {
            applyDraftFiles(application, userId, r, now);
        } catch (IllegalArgumentException exception) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_ATTACHMENT_METADATA);
        }
        return response(repository.save(application));
    }

    @Transactional(readOnly = true)
    public PlaceRegistrationPageResponse list(Long userId, int page, int limit) {
        Page<PlaceRegistrationApplication> result = repository.findAllByApplicantUserId(userId, pageable(page, limit));
        return page(result);
    }

    @Transactional(readOnly = true)
    public PlaceRegistrationPageResponse listAll(int page, int limit) {
        return page(repository.findAll(pageable(page, limit)));
    }

    @Transactional(readOnly = true)
    public PlaceRegistrationResponse getAny(Long id) {
        return response(repository.findById(id).orElseThrow(this::notFound));
    }

    @Transactional(readOnly = true)
    public PlaceRegistrationResponse get(Long userId, Long id) {
        return response(repository.findByIdAndApplicantUserId(id, userId).orElseThrow(this::notFound));
    }

    @Transactional
    public PlaceRegistrationResponse update(Long userId, Long id, PlaceRegistrationRequest r) {
        PlaceRegistrationApplication a = mine(userId, id);
        try {
            LocalDateTime now = now();
            a.update(r.placeName(), r.category(), r.latitude(), r.longitude(), r.roadAddress(), r.jibunAddress(), r.postalCode(), r.description(), r.tags(), now);
            a.updateContactPhones(normalizePhone(r.businessContactPhone()), normalizePhone(r.applicantContactPhone()));
            updateOperatingSchedule(a, r, now);
            applyDraftFiles(a, userId, r, now);
        } catch (IllegalArgumentException e) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_ATTACHMENT_METADATA);
        } catch (IllegalStateException e) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_STATE);
        }
        return response(a);
    }

    @Transactional
    public PlaceRegistrationResponse submit(Long userId, Long id) {
        PlaceRegistrationApplication a = mine(userId, id);
        try { a.submit(now(), contentHash(a)); } catch (IllegalStateException e) {
            throw new PlaceRegistrationException(a.hasRequiredFiles() ? PlaceRegistrationErrorCode.INVALID_STATE : PlaceRegistrationErrorCode.REQUIRED_FILES_MISSING);
        }
        return response(a);
    }

    @Transactional
    public PlaceRegistrationResponse cancel(Long userId, Long id) { return transition(userId, id, a -> a.cancel(now())); }

    @Transactional
    public PlaceRegistrationResponse reopen(Long userId, Long id) { return transition(userId, id, a -> a.reopen(now())); }

    @Transactional
    public PlaceRegistrationResponse approve(Long adminId, Long id, PlaceRegistrationReviewRequest r) {
        PlaceRegistrationApplication a = locked(id);
        try { a.approve(adminId, r.reason(), now()); } catch (IllegalStateException e) { throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_STATE); }
        return response(a);
    }

    @Transactional
    public PlaceRegistrationResponse reject(Long adminId, Long id, PlaceRegistrationReviewRequest r) {
        PlaceRegistrationApplication a = locked(id);
        try { a.reject(adminId, r.reason(), now()); } catch (IllegalStateException e) { throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_STATE); }
        return response(a);
    }

    @Transactional
    public PlaceRegistrationResponse complete(Long userId, Long id) {
        PlaceRegistrationApplication a = mine(userId, id);
        if (a.getStatus() != PlaceRegistrationStatus.APPROVED) throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_STATE);
        if (placeRepository.existsByNameAndAddressAndLatitudeAndLongitude(a.getPlaceName(), a.getRoadAddress(), a.getLatitude(), a.getLongitude())) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.DUPLICATE_PLACE);
        }
        MerchantOwnerProfile profile = profileRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new PlaceRegistrationException(PlaceRegistrationErrorCode.MERCHANT_PROFILE_REQUIRED));
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(() -> new PlaceRegistrationException(PlaceRegistrationErrorCode.ACCESS_DENIED));
        LocalDateTime now = now();
        try { profile.approve(a.getReviewerUserId(), now); user.activateMerchantOwnerRole(); } catch (IllegalStateException e) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.MERCHANT_PROFILE_REQUIRED);
        }
        MapPlace place = placeRepository.save(MapPlace.builder().name(a.getPlaceName()).address(a.getRoadAddress())
                .roadAddress(a.getRoadAddress()).jibunAddress(a.getJibunAddress()).postalCode(a.getPostalCode())
                .category(a.getCategory().name()).latitude(a.getLatitude()).longitude(a.getLongitude())
                .location(point(a.getLatitude(), a.getLongitude())).userId(userId).registrant(user.getUsername())
                .geocodingSource(GeocodingSource.LEGACY).build());
        place.replaceOperatingSchedule(toRegularHours(a), toBreakTimes(a), List.of());
        ownerPlaceRepository.save(MerchantOwnerPlace.builder().placeId(place.getId()).merchantOwnerUserId(userId).createdAt(now).build());
        memberRepository.save(MerchantPlaceMember.owner(place.getId(), userId, now));
        snapshotService.initialize(place.getId());
        a.register(place.getId(), now);
        return response(a);
    }

    private PlaceRegistrationResponse transition(Long userId, Long id, java.util.function.Consumer<PlaceRegistrationApplication> action) {
        PlaceRegistrationApplication a = mine(userId, id);
        try { action.accept(a); } catch (IllegalStateException e) { throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_STATE); }
        return response(a);
    }
    private PlaceRegistrationApplication mine(Long userId, Long id) {
        PlaceRegistrationApplication application = locked(id);
        if (!application.getApplicantUserId().equals(userId)) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.ACCESS_DENIED);
        }
        return application;
    }
    private PlaceRegistrationApplication locked(Long id) { return repository.findByIdForUpdate(id).orElseThrow(this::notFound); }
    private PlaceRegistrationException notFound() { return new PlaceRegistrationException(PlaceRegistrationErrorCode.APPLICATION_NOT_FOUND); }
    private PlaceRegistrationResponse response(PlaceRegistrationApplication a) { return PlaceRegistrationResponse.from(a); }

    private void applyDraftFiles(PlaceRegistrationApplication application, Long userId,
                                 PlaceRegistrationRequest request, LocalDateTime now) {
        application.attachFileIds(request.businessRegistrationFileId(), request.identityDocumentFileId(),
                request.representativeImageFileIds(), now);
        List<PlaceRegistrationAttachment> attachments = request.attachments() == null
                ? List.of()
                : request.attachments().stream()
                .map(attachment -> toAttachment(application, userId, attachment, now))
                .toList();
        application.replaceAttachments(attachments, now);
    }

    private PlaceRegistrationAttachment toAttachment(PlaceRegistrationApplication application, Long userId,
                                                     PlaceRegistrationAttachmentRequest request, LocalDateTime now) {
        LocalDateTime retentionExpiresAt = request.retentionDays() == null
                ? null : now.plusDays(request.retentionDays());
        return PlaceRegistrationAttachment.create(application, request.fileId(), request.documentType(),
                request.storageKey(), request.originalFilename(), request.contentType(), request.fileSize(),
                request.fileHash(), userId, now, retentionExpiresAt, request.resolvedDisplayOrder());
    }

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
    private PlaceRegistrationPageResponse page(Page<PlaceRegistrationApplication> p) { return new PlaceRegistrationPageResponse(p.getContent().stream().map(this::response).toList(), p.getNumber()+1, p.getSize(), p.getTotalElements(), p.getTotalPages(), p.hasNext()); }
    private PageRequest pageable(int page, int limit) { return PageRequest.of(Math.max(0, page-1), Math.min(Math.max(limit, 1), 100), Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))); }
    private LocalDateTime now() { return LocalDateTime.now(clock); }
    private String normalizePhone(String phone) {
        String normalized = phone == null ? "" : phone.replaceAll("[\\s-]", "");
        if (!normalized.matches("\\+[1-9]\\d{7,14}")) throw new IllegalArgumentException("전화번호 형식이 올바르지 않습니다.");
        return normalized;
    }
    private static Point point(double lat, double lon) { return WGS84.createPoint(new Coordinate(lon, lat)); }
}
