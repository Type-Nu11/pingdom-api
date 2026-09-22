package com.typenull.pingdom.place.infrastructure.localhot;

import com.typenull.pingdom.place.application.service.localhot.PlaceAdministrativeRegionService;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.shared.exception.MapException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * 시작 시 행정구역 코드가 없는 장소를 ID 순으로 한 배치만 보강.
 * 각 장소는 서비스의 별도 호출 트랜잭션으로 처리하고 MapException은 개별 실패로 남겨 다음 장소를 계속 처리.
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "place.local-hot.backfill", name = "enabled", havingValue = "true")
class PlaceAdministrativeRegionBackfillRunner implements ApplicationRunner {

    private final PlaceAdministrativeRegionBackfillProperties properties;
    private final MapPlaceRepository mapPlaceRepository;
    private final PlaceAdministrativeRegionService placeAdministrativeRegionService;

    @Override
    public void run(ApplicationArguments args) {
        int succeeded = 0;
        int failed = 0;
        for (MapPlace place : mapPlaceRepository.findByRegionCodeIsNullOrderByIdAsc(PageRequest.of(0, properties.batchSize()))) {
            try {
                if (placeAdministrativeRegionService.synchronizeByIdIfConfigured(place.getId())) {
                    succeeded++;
                }
            } catch (MapException exception) {
                failed++;
                log.warn("Place administrative-region backfill failed. placeId={}, errorCode={}", place.getId(), exception.getErrorCode().getCode());
            }
        }
        log.info("Place administrative-region backfill completed. succeeded={}, failed={}", succeeded, failed);
    }
}
