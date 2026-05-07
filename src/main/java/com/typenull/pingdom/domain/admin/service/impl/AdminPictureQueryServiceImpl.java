package com.typenull.pingdom.domain.admin.service.impl;

import com.typenull.pingdom.domain.admin.dto.picture.AdminPictureResponse;
import com.typenull.pingdom.domain.admin.service.AdminPictureQueryService;
import com.typenull.pingdom.domain.pictures.domain.Picture;
import com.typenull.pingdom.domain.pictures.repository.PictureRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminPictureQueryServiceImpl implements AdminPictureQueryService {

    private final PictureRepository pictureRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AdminPictureResponse> listPictures(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return pictureRepository.findAll(PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "id")))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private AdminPictureResponse toResponse(Picture picture) {
        return new AdminPictureResponse(picture.getId(), picture.getUrl(), picture.getS3Key(), picture.getCreatedAt());
    }
}

