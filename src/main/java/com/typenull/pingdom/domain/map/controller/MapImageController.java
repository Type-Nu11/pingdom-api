package com.typenull.pingdom.domain.map.controller;

import com.typenull.pingdom.domain.auth.security.JwtAuthenticationFilter;
import com.typenull.pingdom.domain.map.dto.ImageUploadRequest;
import com.typenull.pingdom.domain.map.service.S3Service;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/map")
@RequiredArgsConstructor
public class MapImageController {

    private final S3Service s3Service;

    @PostMapping("/pictures/create")
    public ResponseEntity<String> upload(@Valid @ModelAttribute ImageUploadRequest request,
                                         @AuthenticationPrincipal JwtAuthenticationFilter.JwtAuthenticatedUser user) throws IOException {
        Long userId = user.userId();
        s3Service.upload(request,userId);
        return ResponseEntity.ok("사진을 저장했습니다.");
    }
}
