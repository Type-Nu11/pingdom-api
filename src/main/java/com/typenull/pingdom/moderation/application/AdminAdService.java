package com.typenull.pingdom.moderation.application;

import com.typenull.pingdom.moderation.api.dto.ad.AdminAdCreateRequest;
import com.typenull.pingdom.moderation.api.dto.ad.AdminAdCreateResponse;

public interface AdminAdService {

    AdminAdCreateResponse create(AdminAdCreateRequest request, Long adminUserId);

    void delete(Long adId, Long adminUserId);
}
