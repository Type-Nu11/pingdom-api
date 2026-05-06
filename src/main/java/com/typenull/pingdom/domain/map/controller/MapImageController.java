package com.typenull.pingdom.domain.map.controller;

import com.typenull.pingdom.domain.map.dto.MapImageRequest;
import com.typenull.pingdom.domain.map.service.S3Service;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<String> upload(@Valid @ModelAttribute MapImageRequest request) throws IOException {
        s3Service.upload(request);
        return ResponseEntity.ok("사진을 저장했습니다.");
    }
}
