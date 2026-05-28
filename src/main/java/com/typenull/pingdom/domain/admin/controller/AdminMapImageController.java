package com.typenull.pingdom.domain.admin.controller;

import com.typenull.pingdom.domain.admin.service.AdminPictureService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/map-images")
@RequiredArgsConstructor
@Tag(name = "Web", description = "웹(관리자) 전용 API")
public class AdminMapImageController {

    private final AdminPictureService adminPictureService;
}
