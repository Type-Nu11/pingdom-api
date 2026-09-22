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

/**
 * 장소 소유 매핑을 확인한 뒤 장소·북마크·추세 이력·추천 스냅샷 삭제를 조정.
 * 장소 등록자 ID와 현재 사업자 소유 매핑은 별개이므로 삭제 권한은 후자를 기준으로 판단.
 */
@Service
@RequiredArgsConstructor
public class MapPlaceService {

    private final MapPlaceRepository mapPlaceRepository;
    private final MerchantOwnerPlaceRepository merchantOwnerPlaceRepository;
    private final PlaceRecommendationSnapshotService placeRecommendationSnapshotService;
    private final MapBookmarkRepository mapBookmarkRepository;
    private final MapBookmarkTrendEventRepository mapBookmarkTrendEventRepository;

    /**
     * 현재 Merchant 소유 연결이 확인된 장소의 북마크 추세·북마크·장소·추천 스냅샷을 삭제.
     * 장소 부재나 소유권 불일치는 거절하며, 이 메서드에서 정리하지 않는 다른 참조의 FK 제약은 삭제를 실패시킬 수 있음.
     * 장소 및 소유 연결에 대한 별도 잠금은 미사용.
     */
    @Transactional
    public void deletePlace(long placeId, long userId) {
        MapPlace mapPlace = mapPlaceRepository.findById(placeId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_NOT_FOUND));

        if (!merchantOwnerPlaceRepository.existsByPlaceIdAndMerchantOwnerUserId(placeId, userId)) {
            throw new MapException(MapErrorCode.OTHERS_PLACE_NOT_DELETED);
        }

        // 북마크 추세 이벤트는 장소를 RESTRICT FK로 참조하므로 북마크보다 먼저 정리 필요.
        mapBookmarkTrendEventRepository.deleteAllByPlaceId(placeId);
        mapBookmarkRepository.deleteAllByPlaceId(placeId);
        mapPlaceRepository.delete(mapPlace);
        placeRecommendationSnapshotService.delete(placeId);
    }
}
