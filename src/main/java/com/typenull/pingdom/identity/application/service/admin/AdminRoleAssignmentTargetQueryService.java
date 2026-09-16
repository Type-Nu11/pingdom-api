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

@Service
@RequiredArgsConstructor
public class AdminRoleAssignmentTargetQueryService {

    private static final int DEFAULT_PAGE = 1;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 100;

    private final UserRepository userRepository;
    private final AdminRoleAuthorizationService authorizationService;

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
