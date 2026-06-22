package com.typenull.pingdom.identity.application.service;

import com.typenull.pingdom.identity.domain.UserStatus;
import com.typenull.pingdom.identity.domain.repository.OAuthAccountRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WithdrawnUserPurgeService {

    private final UserRepository userRepository;
    private final OAuthAccountRepository oAuthAccountRepository;
    private final UserWithdrawalProperties properties;

    @Transactional
    public int purgeExpiredUsers(LocalDateTime now) {
        LocalDateTime cutoff = now.minus(properties.retention());
        List<Long> expiredUserIds = userRepository.findExpiredWithdrawnUserIds(
                UserStatus.WITHDRAWN,
                cutoff,
                PageRequest.of(0, properties.cleanupBatchSize())
        );

        if (expiredUserIds.isEmpty()) {
            return 0;
        }

        int deletedOAuthAccountCount = oAuthAccountRepository.deleteAllByUserIds(expiredUserIds);
        userRepository.deleteAllByIdInBatch(expiredUserIds);

        log.info(
                "보존기간이 만료된 탈퇴 사용자를 최종 삭제했습니다. userCount={}, deletedOAuthAccountCount={}, cutoff={}",
                expiredUserIds.size(),
                deletedOAuthAccountCount,
                cutoff
        );
        return expiredUserIds.size();
    }
}
