package com.typenull.pingdom.domain.admin.service.impl;

import com.typenull.pingdom.domain.admin.dto.picture.AdminPictureResponse;
import com.typenull.pingdom.domain.admin.service.AdminPictureQueryService;
import com.typenull.pingdom.domain.map.domain.MapImage;
import com.typenull.pingdom.domain.map.repository.MapImageRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
    public List<AdminPictureResponse> listPictures(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return mapImageRepository.findAll(PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "id")))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private AdminPictureResponse toResponse(MapImage mapImage) {
        return new AdminPictureResponse(mapImage.getId(), mapImage.getImageUrl(), mapImage.getS3Key(), mapImage.getUserId());
    }
}
