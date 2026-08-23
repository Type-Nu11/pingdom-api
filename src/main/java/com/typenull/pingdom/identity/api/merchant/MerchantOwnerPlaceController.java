package com.typenull.pingdom.identity.api.merchant;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerMediaOrderUpdateRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerMediaResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerMediaUploadRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerMediaUploadResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerOperatingResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerOperatingScheduleResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerOperatingScheduleUpdateRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerOperatingStatusUpdateRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerPlaceDetailResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerRepresentativeMediaUpdateRequest;
import com.typenull.pingdom.identity.application.service.merchant.MerchantOwnerPlaceManagementService;
import com.typenull.pingdom.place.api.dto.place.media.PlaceMediaItem;
import com.typenull.pingdom.shared.security.annotation.ActiveMerchantOwnerOnly;
import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/merchant-owner/places/{placeId}")
@RequiredArgsConstructor
@ActiveMerchantOwnerOnly
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Merchant", description = "Merchant 전용 API")
public class MerchantOwnerPlaceController {

    private final MerchantOwnerPlaceManagementService service;

    @GetMapping
    @Operation(summary = "Merchant Owner 장소 상세 조회")
    public MerchantOwnerPlaceDetailResponse getPlace(
            @CurrentUser JwtAuthenticatedUser user,
            @PathVariable Long placeId
    ) {
        return service.getPlace(user.userId(), placeId);
    }

    @GetMapping("/operating")
    @Operation(summary = "Merchant Owner 장소 운영 정보 조회")
    public MerchantOwnerOperatingResponse getOperating(
            @CurrentUser JwtAuthenticatedUser user,
            @PathVariable Long placeId
    ) {
        return service.getOperating(user.userId(), placeId);
    }

    @PatchMapping("/operating-status")
    @Operation(summary = "Merchant Owner 장소 운영 상태 변경")
    public MerchantOwnerOperatingResponse updateOperatingStatus(
            @CurrentUser JwtAuthenticatedUser user,
            @PathVariable Long placeId,
            @Valid @RequestBody MerchantOwnerOperatingStatusUpdateRequest request
    ) {
        return service.updateOperatingStatus(user.userId(), placeId, request);
    }

    @PutMapping("/operating-schedule")
    @Operation(summary = "Merchant Owner 장소 영업시간 변경")
    public MerchantOwnerOperatingScheduleResponse updateOperatingSchedule(
            @CurrentUser JwtAuthenticatedUser user,
            @PathVariable Long placeId,
            @Valid @RequestBody MerchantOwnerOperatingScheduleUpdateRequest request
    ) {
        return service.updateOperatingSchedule(user.userId(), placeId, request);
    }

    @PostMapping("/media/upload-url")
    @Operation(summary = "Merchant Owner 탐색 미디어 업로드 URL 발급")
    public MerchantOwnerMediaUploadResponse createMediaUploadUrl(
            @CurrentUser JwtAuthenticatedUser user,
            @PathVariable Long placeId,
            @Valid @RequestBody MerchantOwnerMediaUploadRequest request
    ) {
        return service.createUploadUrl(user.userId(), placeId, request);
    }

    @GetMapping("/media")
    @Operation(summary = "Merchant Owner 탐색 미디어 조회")
    public MerchantOwnerMediaResponse getMedia(
            @CurrentUser JwtAuthenticatedUser user,
            @PathVariable Long placeId
    ) {
        return service.getMedia(user.userId(), placeId);
    }

    @PatchMapping("/media/{mediaId}")
    @Operation(summary = "Merchant Owner 탐색 미디어 순서 변경")
    public PlaceMediaItem updateMediaOrder(
            @CurrentUser JwtAuthenticatedUser user,
            @PathVariable Long placeId,
            @PathVariable Long mediaId,
            @Valid @RequestBody MerchantOwnerMediaOrderUpdateRequest request
    ) {
        return service.updateMediaOrder(user.userId(), placeId, mediaId, request);
    }

    @PutMapping("/media/representative")
    @Operation(summary = "Merchant Owner 대표 미디어 지정")
    public MerchantOwnerMediaResponse updateRepresentative(
            @CurrentUser JwtAuthenticatedUser user,
            @PathVariable Long placeId,
            @Valid @RequestBody MerchantOwnerRepresentativeMediaUpdateRequest request
    ) {
        return service.updateRepresentative(user.userId(), placeId, request.mediaId());
    }

    @DeleteMapping("/media/{mediaId}")
    @Operation(summary = "Merchant Owner 탐색 미디어 삭제")
    public ResponseEntity<Void> deleteMedia(
            @CurrentUser JwtAuthenticatedUser user,
            @PathVariable Long placeId,
            @PathVariable Long mediaId
    ) {
        service.deleteMedia(user.userId(), placeId, mediaId);
        return ResponseEntity.noContent().build();
    }
}
