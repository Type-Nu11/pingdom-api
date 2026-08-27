package com.typenull.pingdom.place.application.service.registration;

import com.typenull.pingdom.place.domain.registration.PlaceRegistrationApplication;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationStatus;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.registration.PlaceRegistrationApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 기존 COMPLETED 신청의 누락 대표 이미지를 한 건씩 복구하는 transaction 경계입니다. */
@Service
@RequiredArgsConstructor
public class PlaceRegistrationMediaBackfillService {

    private final PlaceRegistrationApplicationRepository applicationRepository;
    private final MapPlaceRepository placeRepository;
    private final PlaceRegistrationMediaPromotionService promotionService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean backfill(Long applicationId) {
        PlaceRegistrationApplication application = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow();
        if (application.getStatus() != PlaceRegistrationStatus.COMPLETED
                || application.getCompletedPlaceId() == null) {
            return false;
        }
        var place = placeRepository.findByIdForUpdate(application.getCompletedPlaceId()).orElseThrow();
        promotionService.promote(place, application);
        return true;
    }
}
