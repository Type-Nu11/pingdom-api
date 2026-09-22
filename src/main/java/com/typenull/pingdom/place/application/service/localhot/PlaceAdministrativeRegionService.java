package com.typenull.pingdom.place.application.service.localhot;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.region.PlaceAdministrativeRegion;
import com.typenull.pingdom.place.domain.place.region.PlaceAdministrativeRegionResolver;
import com.typenull.pingdom.place.domain.place.region.ResolvedPlaceAdministrativeRegion;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceAdministrativeRegionRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 외부 좌표 해석 결과로 행정구역 이름을 갱신하고 장소에 구역 코드를 연결.
 * 설정 조건부 메서드는 resolver 미설정 시 false를 반환하며 외부 조회와 DB 갱신 사이의 원자성은 보장 범위에서 제외.
 */
@Service
@RequiredArgsConstructor
public class PlaceAdministrativeRegionService {

    private final PlaceAdministrativeRegionResolver resolver;
    private final PlaceAdministrativeRegionRepository regionRepository;
    private final MapPlaceRepository mapPlaceRepository;
    private final Clock clock;

    @Transactional
    public boolean synchronizeIfConfigured(MapPlace place) {
        if (!resolver.isConfigured()) {
            return false;
        }
        synchronize(place);
        return true;
    }

    /**
     * 지역 판별 설정이 없으면 조회 없이 false를 반환하고, 설정이 있으면 장소 행을 쓰기 잠금으로 읽어 지역을 동기화.
     * 장소 부재 시 예외를 던지며 정상 동기화는 true를 반환. 외부 지역 판별 실패는 전파.
     */
    @Transactional
    public boolean synchronizeByIdIfConfigured(long placeId) {
        if (!resolver.isConfigured()) {
            return false;
        }
        MapPlace place = mapPlaceRepository.findByIdForUpdate(placeId)
                .orElseThrow(() -> new IllegalArgumentException("지역을 갱신할 장소가 없습니다. placeId=" + placeId));
        synchronize(place);
        return true;
    }

    /**
     * 장소 좌표로 행정구역을 판별하여 지역 사전 행을 생성·갱신하고 장소의 지역 코드를 변경.
     * 설정·장소 null 여부와 잠금은 호출자가 관리하며, 관리 상태 장소의 변경 저장에는 호출 트랜잭션이 필요.
     */
    public void synchronize(MapPlace place) {
        ResolvedPlaceAdministrativeRegion region = resolver.resolve(place.getLatitude(), place.getLongitude());
        LocalDateTime now = LocalDateTime.now(clock);
        regionRepository.findById(region.code())
                .ifPresentOrElse(
                        existing -> existing.refresh(region, now),
                        () -> regionRepository.save(PlaceAdministrativeRegion.from(region, now))
                );
        place.updateAdministrativeRegion(region.code());
    }
}
