package com.typenull.pingdom.moderation.application;

import com.typenull.pingdom.moderation.api.dto.ad.AdminAdCreateRequest;
import com.typenull.pingdom.moderation.api.dto.ad.AdminAdCreateResponse;
import com.typenull.pingdom.moderation.api.dto.ad.AdminAdListResponse;
import com.typenull.pingdom.moderation.api.dto.ad.AdminAdListItem;
import com.typenull.pingdom.moderation.domain.ad.AdminAdDisplayStatus;
import java.time.LocalDateTime;

public interface AdminAdService {

    AdminAdCreateResponse create(AdminAdCreateRequest request, Long adminUserId);

    void delete(Long adId, Long adminUserId);

    AdminAdListResponse list(String keyword, AdminAdDisplayStatus displayStatus,
            LocalDateTime startedFrom, LocalDateTime startedTo, int page, int limit);

    AdminAdListItem get(Long adId);
}
