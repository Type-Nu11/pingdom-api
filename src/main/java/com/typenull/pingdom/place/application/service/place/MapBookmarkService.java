package com.typenull.pingdom.place.application.service.place;

import com.typenull.pingdom.place.api.dto.bookmark.BookmarkCreateRequest;
import com.typenull.pingdom.place.api.dto.bookmark.BookmarkCreateResponse;
import com.typenull.pingdom.place.api.dto.bookmark.BookmarkRemoveResponse;
import com.typenull.pingdom.place.application.service.recommendation.feedback.PlaceRecommendationConversionService;
import com.typenull.pingdom.place.application.service.recommendation.snapshot.PlaceRecommendationSnapshotService;
import com.typenull.pingdom.place.domain.place.core.MapBookmark;
import com.typenull.pingdom.place.domain.place.core.MapBookmarkTrendEvent;
import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import com.typenull.pingdom.place.domain.recommendation.engagement.PlaceRecommendationConversionType;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkTrendEventRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.support.MapMessages;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개·운영 중인 장소의 북마크를 생성하거나 회원 소유 북마크를 제거.
 * 북마크 변경과 함께 증감 이력·추천 스냅샷을 갱신하고 생성에는 적격 추천 전환을 기록.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MapBookmarkService {

    private final MapBookmarkRepository mapBookmarkRepository;
    private final MapBookmarkTrendEventRepository mapBookmarkTrendEventRepository;
    private final MapPlaceRepository mapPlaceRepository;
    private final PlaceRecommendationSnapshotService placeRecommendationSnapshotService;
    private final PlaceRecommendationConversionService placeRecommendationConversionService;
    private final Clock clock;

    /**
     * 운영 중이며 공개된 장소에 사용자 북마크를 생성하고 생성된 북마크·장소 ID를 반환.
     * 중복 사전 조회와 saveAndFlush의 무결성 실패는 BOOKMARK_ALREADY_EXISTS로 변환.
     * 추세 추가 이력·추천 스냅샷 갱신·최근 클릭에 대한 전환 귀속도 현재 트랜잭션에 참여.
     */
    @Transactional
    public BookmarkCreateResponse createBookmark(BookmarkCreateRequest request, long userId) {
        Long placeId = request.placeId();

        boolean placeExists = mapPlaceRepository.existsByIdAndOperatingStatusAndDiscoveryStatus(
                placeId,
                PlaceOperatingStatus.OPERATING,
                PlaceDiscoveryStatus.VISIBLE
        );
        if (!placeExists) {
            throw new MapException(MapErrorCode.PLACE_NOT_FOUND);
        }

        boolean alreadyExists = mapBookmarkRepository.existsByUserIdAndPlaceId(userId, placeId);
        if (alreadyExists) {
            throw new MapException(MapErrorCode.BOOKMARK_ALREADY_EXISTS);
        }

        MapBookmark bookmark = MapBookmark.builder()
                .userId(userId)
                .placeId(placeId)
                .build();

        MapBookmark saved;
        try {
            saved = mapBookmarkRepository.saveAndFlush(bookmark);
        } catch (DataIntegrityViolationException exception) {
            throw new MapException(MapErrorCode.BOOKMARK_ALREADY_EXISTS);
        }
        mapBookmarkTrendEventRepository.save(MapBookmarkTrendEvent.added(userId, placeId, LocalDateTime.now(clock)));
        placeRecommendationSnapshotService.refresh(placeId);
        placeRecommendationConversionService.recordConversionIfEligible(
                userId,
                placeId,
                PlaceRecommendationConversionType.BOOKMARK
        );
        return new BookmarkCreateResponse(saved.getId(), saved.getPlaceId(), MapMessages.BOOKMARK_CREATED);
    }

    /**
     * 사용자와 장소가 일치하는 북마크를 삭제하고 해제 추세 이력 및 추천 스냅샷을 같은 트랜잭션에서 갱신.
     * 삭제된 행이 없으면 BOOKMARK_NOT_FOUND로 거절하고, 성공하면 사용자·장소 ID를 반환.
     */
    @Transactional
    public BookmarkRemoveResponse removeBookmark(Long placeId, long userId) {
        if (mapBookmarkRepository.deleteByPlaceIdAndUserId(placeId, userId) == 0) {
            throw new MapException(MapErrorCode.BOOKMARK_NOT_FOUND);
        }
        mapBookmarkTrendEventRepository.save(MapBookmarkTrendEvent.removed(userId, placeId, LocalDateTime.now(clock)));
        placeRecommendationSnapshotService.refresh(placeId);

        return new BookmarkRemoveResponse(userId, placeId, MapMessages.BOOKMARK_REMOVED);
    }
}
