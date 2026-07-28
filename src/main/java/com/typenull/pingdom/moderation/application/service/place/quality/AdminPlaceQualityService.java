package com.typenull.pingdom.moderation.application.service.place.quality;

import com.typenull.pingdom.moderation.api.dto.place.quality.coordinate.AdminMapPlaceCoordinateUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.coordinate.AdminMapPlaceCoordinateUpdateResponse;
import com.typenull.pingdom.moderation.api.dto.place.quality.discovery.AdminMapPlaceDiscoveryStatusUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.discovery.AdminMapPlaceDiscoveryStatusUpdateResponse;
import com.typenull.pingdom.moderation.api.dto.place.quality.geocoding.AdminMapPlaceGeocodingUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.geocoding.AdminMapPlaceGeocodingUpdateResponse;
import com.typenull.pingdom.moderation.api.dto.place.quality.information.AdminPlaceInformationEvidenceCreateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.information.AdminPlaceInformationEvidenceResponse;
import com.typenull.pingdom.moderation.api.dto.place.quality.information.AdminPlaceInformationEvidenceReviewRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.information.AdminPlaceInformationEvidenceUpdateResponse;
import com.typenull.pingdom.moderation.api.dto.place.quality.kakao.AdminMapPlaceKakaoPlaceIdUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.kakao.AdminMapPlaceKakaoPlaceIdUpdateResponse;
import com.typenull.pingdom.moderation.api.dto.place.quality.operating.AdminMapPlaceOperatingStatusUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.operating.AdminMapPlaceOperatingStatusUpdateResponse;
import com.typenull.pingdom.moderation.api.dto.place.quality.tourist.AdminMapPlaceTouristInfoUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.tourist.AdminMapPlaceTouristInfoUpdateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 장소 품질 보정과 정보 근거 관리를 담당하는 관리자 유스케이스의 진입점이다. */
@Service
@RequiredArgsConstructor
public class AdminPlaceQualityService {
    private final AdminMapPlaceService adminMapPlaceService;

    public AdminMapPlaceCoordinateUpdateResponse updatePlaceCoordinates(Long adminUserId, Long placeId, AdminMapPlaceCoordinateUpdateRequest request) { return adminMapPlaceService.updatePlaceCoordinates(adminUserId, placeId, request); }
    public AdminMapPlaceGeocodingUpdateResponse updatePlaceGeocoding(Long adminUserId, Long placeId, AdminMapPlaceGeocodingUpdateRequest request) { return adminMapPlaceService.updatePlaceGeocoding(adminUserId, placeId, request); }
    public AdminMapPlaceKakaoPlaceIdUpdateResponse updatePlaceKakaoPlaceId(Long adminUserId, Long placeId, AdminMapPlaceKakaoPlaceIdUpdateRequest request) { return adminMapPlaceService.updatePlaceKakaoPlaceId(adminUserId, placeId, request); }
    public AdminMapPlaceTouristInfoUpdateResponse updatePlaceTouristInfo(Long adminUserId, Long placeId, AdminMapPlaceTouristInfoUpdateRequest request) { return adminMapPlaceService.updatePlaceTouristInfo(adminUserId, placeId, request); }
    public AdminMapPlaceOperatingStatusUpdateResponse updatePlaceOperatingStatus(Long adminUserId, Long placeId, AdminMapPlaceOperatingStatusUpdateRequest request) { return adminMapPlaceService.updatePlaceOperatingStatus(adminUserId, placeId, request); }
    public AdminMapPlaceDiscoveryStatusUpdateResponse updatePlaceDiscoveryStatus(Long adminUserId, Long placeId, AdminMapPlaceDiscoveryStatusUpdateRequest request) { return adminMapPlaceService.updatePlaceDiscoveryStatus(adminUserId, placeId, request); }
    public AdminPlaceInformationEvidenceResponse getPlaceInformationEvidence(Long placeId) { return adminMapPlaceService.getPlaceInformationEvidence(placeId); }
    public AdminPlaceInformationEvidenceUpdateResponse createPlaceInformationEvidence(Long adminUserId, Long placeId, AdminPlaceInformationEvidenceCreateRequest request) { return adminMapPlaceService.createPlaceInformationEvidence(adminUserId, placeId, request); }
    public AdminPlaceInformationEvidenceUpdateResponse reviewPlaceInformationEvidence(Long adminUserId, Long placeId, Long evidenceId, AdminPlaceInformationEvidenceReviewRequest request) { return adminMapPlaceService.reviewPlaceInformationEvidence(adminUserId, placeId, evidenceId, request); }
}
