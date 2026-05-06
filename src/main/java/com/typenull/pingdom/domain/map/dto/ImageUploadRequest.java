package com.typenull.pingdom.domain.map.dto;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

public record ImageUploadRequest(
        @NotNull(message = "파일은 필수입니다.") MultipartFile file) {
}
