package com.typenull.pingdom.domain.pictures.controller;

import com.typenull.pingdom.domain.pictures.dto.PictureUploadRequest;
import com.typenull.pingdom.domain.pictures.dto.PictureUploadResponse;
import com.typenull.pingdom.domain.pictures.service.PictureService;
import jakarta.validation.Valid;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pictures")
@RequiredArgsConstructor
public class PictureController {

    private final PictureService pictureService;

    @PostMapping("/upload")
    public ResponseEntity<PictureUploadResponse> upload(@Valid @ModelAttribute PictureUploadRequest request) throws IOException {
        return ResponseEntity.ok(pictureService.upload(request));
    }
}

