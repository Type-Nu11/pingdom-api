package com.typenull.pingdom.domain.admin.dto.picture;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "관리자 사진 조회 응답")
public record AdminPictureResponse(
        List<AdminPictureItem> pictures,
        int page,
        int limit,
        long totalCount,
        long totalPages,
        boolean hasNext
) {
        public static AdminPictureResponse of(List<AdminPictureItem> pictures, int page, int limit, long totalCount, long totalPages) {
                return new AdminPictureResponse(pictures, page, limit, totalCount, totalPages, page < totalPages);
        }
}
