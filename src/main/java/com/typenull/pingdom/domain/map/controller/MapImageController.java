package com.typenull.pingdom.domain.map.controller;

import com.typenull.pingdom.global.config.security.JwtAuthenticatedUser;
import com.typenull.pingdom.domain.map.dto.ImageUploadRequest;
import com.typenull.pingdom.domain.map.dto.PictureReportRequest;
import com.typenull.pingdom.domain.map.service.PictureReportService;
import com.typenull.pingdom.domain.map.service.S3Service;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/map")
@RequiredArgsConstructor
public class MapImageController {

    private final S3Service s3Service;
    private final PictureReportService pictureReportService;

    @PostMapping("/pictures/create")
    public ResponseEntity<String> upload(@Valid @ModelAttribute ImageUploadRequest request,
                                         @AuthenticationPrincipal JwtAuthenticatedUser user) throws IOException {
        Long userId = user.userId();
        s3Service.uploadImage(request,userId);
        return ResponseEntity.ok("사진을 저장했습니다.");
    }

    @DeleteMapping("/pictures/{id}/delete")
    public ResponseEntity<String> delete(@Valid @PathVariable("id") Long imageId,
                                         @AuthenticationPrincipal JwtAuthenticatedUser user) throws IOException {
        Long userId = user.userId();
        s3Service.deleteImage(imageId,userId);
        return ResponseEntity.ok("사진을 삭제했습니다.");
    }

    @PostMapping("/pictures/{id}/report")
    public ResponseEntity<String> report(@PathVariable("id") Long imageId,
                                         @Valid @RequestBody PictureReportRequest request,
                                         @AuthenticationPrincipal JwtAuthenticatedUser user) {
        pictureReportService.report(imageId, user.userId(), user.username(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body("사진 신고를 등록했습니다.");
    }
}
