package com.typenull.pingdom.place.application.service.registration;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.api.dto.registration.PlaceRegistrationPageResponse;
import com.typenull.pingdom.place.api.dto.registration.PlaceRegistrationRequest;
import com.typenull.pingdom.place.api.dto.registration.PlaceRegistrationResponse;
import com.typenull.pingdom.place.api.dto.registration.PlaceRegistrationReviewRequest;
import com.typenull.pingdom.place.domain.exception.PlaceRegistrationErrorCode;
import com.typenull.pingdom.place.domain.exception.PlaceRegistrationException;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationApplication;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationStatus;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.registration.PlaceRegistrationApplicationRepository;
import com.typenull.pingdom.place.application.service.recommendation.snapshot.PlaceRecommendationSnapshotService;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.geocoding.GeocodingSource;
import java.time.Clock;
import java.time.LocalDateTime;
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
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public PlaceRegistrationResponse create(Long userId, PlaceRegistrationRequest r) {
        PlaceRegistrationApplication application = PlaceRegistrationApplication.draft(userId, r.placeName(), r.category(), r.latitude(), r.longitude(),
                r.roadAddress(), r.jibunAddress(), r.postalCode(), r.description(), now());
        application.attachFileIds(r.businessRegistrationFileId(), r.identityDocumentFileId(), r.representativeImageFileIds(), now());
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
            a.update(r.placeName(), r.category(), r.latitude(), r.longitude(), r.roadAddress(), r.jibunAddress(), r.postalCode(), r.description(), now());
            a.attachFileIds(r.businessRegistrationFileId(), r.identityDocumentFileId(), r.representativeImageFileIds(), now());
        } catch (IllegalStateException e) {
            throw new PlaceRegistrationException(PlaceRegistrationErrorCode.INVALID_STATE);
        }
        return response(a);
    }

    @Transactional
    public PlaceRegistrationResponse submit(Long userId, Long id) {
        PlaceRegistrationApplication a = mine(userId, id);
        try { a.submit(now()); } catch (IllegalStateException e) {
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
        ownerPlaceRepository.save(MerchantOwnerPlace.builder().placeId(place.getId()).merchantOwnerUserId(userId).createdAt(now).build());
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
    private PlaceRegistrationPageResponse page(Page<PlaceRegistrationApplication> p) { return new PlaceRegistrationPageResponse(p.getContent().stream().map(this::response).toList(), p.getNumber()+1, p.getSize(), p.getTotalElements(), p.getTotalPages(), p.hasNext()); }
    private PageRequest pageable(int page, int limit) { return PageRequest.of(Math.max(0, page-1), Math.min(Math.max(limit, 1), 100), Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))); }
    private LocalDateTime now() { return LocalDateTime.now(clock); }
    private static Point point(double lat, double lon) { return WGS84.createPoint(new Coordinate(lon, lat)); }
}
