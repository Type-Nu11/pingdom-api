package com.typenull.pingdom.place.application.service.place;

import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.place.application.service.recommendation.snapshot.PlaceRecommendationSnapshotService;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkTrendEventRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MapPlaceService {

    private final MapPlaceRepository mapPlaceRepository;
    private final MerchantOwnerPlaceRepository merchantOwnerPlaceRepository;
    private final PlaceRecommendationSnapshotService placeRecommendationSnapshotService;
    private final MapBookmarkRepository mapBookmarkRepository;
    private final MapBookmarkTrendEventRepository mapBookmarkTrendEventRepository;

    @Transactional
    public void deletePlace(long placeId, long userId) {
        MapPlace mapPlace = mapPlaceRepository.findById(placeId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_NOT_FOUND));

        if (!merchantOwnerPlaceRepository.existsByPlaceIdAndMerchantOwnerUserId(placeId, userId)) {
            throw new MapException(MapErrorCode.OTHERS_PLACE_NOT_DELETED);
        }

        // 북마크 추세 이벤트는 장소를 RESTRICT FK로 참조하므로 북마크보다 먼저 정리해야 합니다.
        mapBookmarkTrendEventRepository.deleteAllByPlaceId(placeId);
        mapBookmarkRepository.deleteAllByPlaceId(placeId);
        mapPlaceRepository.delete(mapPlace);
        placeRecommendationSnapshotService.delete(placeId);
    }
}
