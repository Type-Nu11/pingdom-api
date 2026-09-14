package com.typenull.pingdom.community.application;

import com.typenull.pingdom.community.domain.exception.CommunityErrorCode;
import com.typenull.pingdom.community.domain.exception.CommunityException;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPlaceDailyViewRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostPlaceRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostRepository;
import com.typenull.pingdom.place.api.dto.place.detail.PlaceDetailResponse;
import com.typenull.pingdom.place.application.service.place.PlaceQueryService;
import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommunityPlaceViewService {

    private static final ZoneId KOREA_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final CommunityPostRepository communityPostRepository;
    private final CommunityPostPlaceRepository communityPostPlaceRepository;
    private final CommunityPlaceDailyViewRepository communityPlaceDailyViewRepository;
    private final MapPlaceRepository mapPlaceRepository;
    private final PlaceQueryService placeQueryService;
    private final Clock clock;

    /** 게시글에 실제 연결된 공개 장소만 KST 날짜별 사용자 1회로 조회수를 기록합니다. */
    @Transactional
    public PlaceDetailResponse record(long postId, long placeId, long userId) {
        requirePost(postId);
        requireLinkedPlace(postId, placeId);

        // 먼저 공개 가능한 장소인지 검증해 실패 요청은 일별 조회 기록과 집계에서 제외한다.
        requirePublicPlace(placeId);

        if (communityPlaceDailyViewRepository.insertIgnoreDuplicate(
                userId,
                placeId,
                LocalDate.now(clock.withZone(KOREA_ZONE_ID))
        ) == 1) {
            mapPlaceRepository.increaseCommunityViewCount(placeId);
        }

        // 원자적 UPDATE 후 최신 집계값을 포함한 장소 상세를 반환한다.
        return placeQueryService.getPlace(placeId);
    }

    private void requirePost(long postId) {
        if (!communityPostRepository.existsById(postId)) {
            throw new CommunityException(CommunityErrorCode.POST_NOT_FOUND);
        }
    }

    private void requireLinkedPlace(long postId, long placeId) {
        if (!communityPostPlaceRepository.existsByCommunityPost_IdAndMapPlace_Id(postId, placeId)) {
            throw new CommunityException(CommunityErrorCode.PLACE_NOT_LINKED);
        }
    }

    private void requirePublicPlace(long placeId) {
        if (!mapPlaceRepository.existsByIdAndOperatingStatusAndDiscoveryStatus(
                placeId,
                PlaceOperatingStatus.OPERATING,
                PlaceDiscoveryStatus.VISIBLE
        )) {
            throw new MapException(MapErrorCode.PLACE_NOT_FOUND);
        }
    }
}
