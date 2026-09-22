package com.typenull.pingdom.place.application.service.registration;

import com.typenull.pingdom.place.domain.registration.PlaceRegistrationApplication;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationStatus;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.registration.PlaceRegistrationApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 기존 COMPLETED 신청의 누락 대표 이미지를 한 건씩 복구하는 transaction 경계. */
@Service
@RequiredArgsConstructor
public class PlaceRegistrationMediaBackfillService {

    private final PlaceRegistrationApplicationRepository applicationRepository;
    private final MapPlaceRepository placeRepository;
    private final PlaceRegistrationMediaPromotionService promotionService;

    /**
     * 신청 행을 잠가 COMPLETED와 완료 장소 ID를 확인하고, 조건에 맞지 않으면 건너뜀 결과를 반환.
     * 완료 장소도 잠가 대표 미디어를 승격하고 신규·기존 승격 건수를 반환. 프록시 호출은 신청별 REQUIRES_NEW 경계로 처리되며 없는 신청·장소는 실패.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BackfillResult backfill(Long applicationId) {
        PlaceRegistrationApplication application = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow();
        if (application.getStatus() != PlaceRegistrationStatus.COMPLETED
                || application.getCompletedPlaceId() == null) {
            return BackfillResult.skipped();
        }
        var place = placeRepository.findByIdForUpdate(application.getCompletedPlaceId()).orElseThrow();
        var promotionResult = promotionService.promote(place, application);
        return new BackfillResult(
                promotionResult.hasPromotedMedia(),
                promotionResult.promotedCount(),
                promotionResult.alreadyPromotedCount()
        );
    }

    /** 한 신청의 결과를 반환해 이미 복구된 건과 실제 변경 건을 구분. */
    public record BackfillResult(
            boolean processed,
            int promotedMediaCount,
            int alreadyPromotedMediaCount
    ) {

        public static BackfillResult skipped() {
            return new BackfillResult(false, 0, 0);
        }
    }
}
