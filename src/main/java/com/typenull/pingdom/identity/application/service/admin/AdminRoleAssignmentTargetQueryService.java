package com.typenull.pingdom.identity.application.service.admin;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.admin.AdminPermission;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.api.dto.user.AdminRoleAssignmentTargetItem;
import com.typenull.pingdom.moderation.api.dto.user.AdminRoleAssignmentTargetSearchResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 역할 관리 권한이 있는 요청자의 관리자 대상 검색을 수행합니다.
 * 외부 페이지는 1부터 시작하고 크기는 1~100으로 보정하며 사용자명·ID 오름차순으로 조회합니다.
 */
@Service
@RequiredArgsConstructor
public class AdminRoleAssignmentTargetQueryService {

    private static final int DEFAULT_PAGE = 1;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 100;

    private final UserRepository userRepository;
    private final AdminRoleAuthorizationService authorizationService;

    /**
     * 역할 관리 권한을 확인한 뒤 ADMIN 계정을 정규화한 검색어로 조회하고 페이지 메타데이터와 함께 반환합니다.
     * 페이지는 최소 1, 크기는 1~100으로 보정하며 사용자명·ID 오름차순을 적용합니다. 권한이 없으면 조회 전에 거절합니다.
     */
    @Transactional(readOnly = true)
    public AdminRoleAssignmentTargetSearchResponse search(
            Long actorUserId,
            String keyword,
            int page,
            int limit
    ) {
        authorizationService.requirePermission(actorUserId, AdminPermission.ADMIN_ROLE_MANAGE);

        int safePage = Math.max(page, DEFAULT_PAGE);
        int safeLimit = Math.min(Math.max(limit, MIN_LIMIT), MAX_LIMIT);
        Page<User> targetPage = userRepository.findAllRoleAssignmentTargets(
                UserRole.ADMIN,
                normalizeKeyword(keyword),
                PageRequest.of(safePage - 1, safeLimit, Sort.by("username").ascending().and(Sort.by("id").ascending()))
        );
        List<AdminRoleAssignmentTargetItem> users = targetPage.getContent().stream()
                .map(AdminRoleAssignmentTargetItem::from)
                .toList();

        return AdminRoleAssignmentTargetSearchResponse.of(
                users,
                safePage,
                safeLimit,
                targetPage.getTotalElements(),
                targetPage.getTotalPages()
        );
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmedKeyword = keyword.trim();
        return trimmedKeyword.isEmpty() ? null : trimmedKeyword;
    }
}
