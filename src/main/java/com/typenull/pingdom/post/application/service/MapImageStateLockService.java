package com.typenull.pingdom.post.application.service;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 장소 병합과 동일하게 장소→사진 순서로 잠근 뒤 최신 노출 상태와 집계 대상을 반환합니다. */
@Service
@RequiredArgsConstructor
public class MapImageStateLockService {

    private final MapImageRepository mapImageRepository;
    private final MapPlaceRepository mapPlaceRepository;
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<LockedImage> findForUpdate(Long imageId) {
        MapImageRepository.ImagePlaceReference reference = mapImageRepository.findPlaceReferenceById(imageId)
                .orElse(null);
        if (reference == null) {
            return Optional.empty();
        }
        Long placeId = reference.getPlaceId();
        MapPlace place = null;
        if (placeId != null) {
            place = mapPlaceRepository.findByIdForUpdate(placeId)
                    .orElseThrow(() -> new MapException(MapErrorCode.IMAGE_PLACE_CHANGED));
            entityManager.refresh(place);
        }
        MapImage image = mapImageRepository.findByIdForUpdate(imageId).orElse(null);
        if (image == null) {
            return Optional.empty();
        }

        // 접수 등에서 먼저 읽었던 엔티티도 잠금 대기 이후의 상태로 검사해야 합니다.
        entityManager.refresh(image);
        Long lockedPlaceId = image.getMapPlace() == null ? null : image.getMapPlace().getId();
        if (!Objects.equals(placeId, lockedPlaceId)) {
            // 새 장소를 사진 잠금 뒤 추가로 잠그지 않고 전체 작업을 롤백해 잠금 순서를 유지합니다.
            throw new MapException(MapErrorCode.IMAGE_PLACE_CHANGED);
        }
        return Optional.of(new LockedImage(image, place));
    }

    public record LockedImage(MapImage image, MapPlace place) {
    }
}
