package com.typenull.pingdom.domain.admin.service.impl;

import com.typenull.pingdom.domain.admin.dto.picture.AdminPictureItem;
import com.typenull.pingdom.domain.admin.dto.picture.AdminPictureResponse;
import com.typenull.pingdom.domain.admin.enums.SortParam;
import com.typenull.pingdom.domain.admin.service.AdminPictureQueryService;
import com.typenull.pingdom.domain.map.domain.MapImage;
import com.typenull.pingdom.domain.map.repository.MapImageRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminPictureQueryServiceImpl implements AdminPictureQueryService {

    private final MapImageRepository mapImageRepository;

    @Override
    @Transactional(readOnly = true)
    public AdminPictureResponse listPictures(int limit, int page, SortParam sortParam) {
        // 리미트 값을 1~100사이로 고정
        int safeLimit = Math.max(1, Math.min(limit, 100));
        int targetPage = Math.max(page - 1, 0);

        Sort sort = switch (sortParam) {
            case OLDEST -> Sort.by(Sort.Direction.ASC, "createdAt");
            case LATEST -> Sort.by(Sort.Direction.DESC, "createdAt");
        };

        Page<MapImage> mapImagePage = mapImageRepository.findAllBy(
                PageRequest.of(targetPage, safeLimit, sort)
        );

        List<AdminPictureItem> pictures = mapImagePage.getContent()
                .stream()
                .map(this::toItem)
                .toList();

        return AdminPictureResponse.of(
                pictures,
                page,
                safeLimit,
                mapImagePage.getTotalElements(),     // totalCount
                mapImagePage.getTotalPages()        // totalPages
        );
    }

    private AdminPictureItem toItem(MapImage mapImage) {
        return new AdminPictureItem(
                mapImage.getId(),
                mapImage.getTitle(),
                mapImage.getImageUrl(), // thumbnailUrl
                mapImage.getImageUrl(), // imageUrl
                mapImage.getUserId(),
                mapImage.getUsername(),
                mapImage.getCreatedAt(),
                mapImage.getDescription(),
                mapImage.getLikeCount(),
                mapImage.getMapPlace() != null ? mapImage.getMapPlace().getName() : null
        );
    }
}
