package com.typenull.pingdom.domain.admin.controller;

import com.typenull.pingdom.domain.admin.dto.picture.AdminPictureResponse;
import com.typenull.pingdom.domain.admin.service.AdminPictureQueryService;
import com.typenull.pingdom.domain.admin.service.AdminPictureService;
import java.util.List;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Tag(name = "Web", description = "웹(관리자) 전용 API")
public class AdminPictureController {

    private final AdminPictureService adminPictureService;
    private final AdminPictureQueryService adminPictureQueryService;

    @GetMapping("/pictures")
    public List<AdminPictureResponse> listPictures(@RequestParam(defaultValue = "20") int limit) {
        return adminPictureQueryService.listPictures(limit);
    }

    @DeleteMapping("/pictures/{id}/delete")
    public ResponseEntity<Void> deletePicture(@PathVariable Long id) {
        adminPictureService.deletePicture(id);
        return ResponseEntity.noContent().build();
    }
}
