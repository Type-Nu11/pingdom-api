package com.typenull.pingdom.identity.application.service.retention;

import com.typenull.pingdom.identity.domain.UserStatus;
import com.typenull.pingdom.identity.domain.repository.TravelScheduleRepository;
import com.typenull.pingdom.identity.domain.repository.UserCurrentActivityIntentRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 만료된 현재 활동 의도와 탈퇴 보존 기간이 지난 회원의 여행 데이터를 정리합니다.
 * 활동 의도 만료 삭제는 전체 대상에 적용하고 탈퇴 회원의 일정 정리만 설정된 배치 크기로 제한합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TravelDataRetentionService {

    private final UserRepository userRepository;
    private final TravelScheduleRepository travelScheduleRepository;
    private final UserCurrentActivityIntentRepository currentActivityIntentRepository;
    private final TravelDataRetentionProperties properties;
    private final Clock clock;

    /**
     * 현재 시각까지 만료된 활동 의도를 삭제하고, 탈퇴 보존 기간이 지난 회원 한 배치의 활동 의도·여행 일정을 정리합니다.
     * 활동 의도 만료 삭제에는 배치 제한을 적용하지 않으며 각 종류의 삭제 건수를 반환하고 삭제가 있으면 기록합니다.
     */
    @Transactional
    public TravelDataRetentionResult purgeExpiredData() {
        LocalDateTime now = LocalDateTime.now(clock);
        int expiredIntentCount = currentActivityIntentRepository.deleteExpiredAtOrBefore(now);
        List<Long> withdrawnUserIds = userRepository.findExpiredWithdrawnUserIdsWithTravelData(
                UserStatus.WITHDRAWN,
                now.minus(properties.withdrawnUserRetention()),
                PageRequest.of(0, properties.cleanupBatchSize())
        );

        int withdrawnIntentCount = 0;
        int travelScheduleCount = 0;
        if (!withdrawnUserIds.isEmpty()) {
            withdrawnIntentCount = currentActivityIntentRepository.deleteAllByUserIds(withdrawnUserIds);
            travelScheduleCount = travelScheduleRepository.deleteAllByUserIds(withdrawnUserIds);
        }

        TravelDataRetentionResult result = new TravelDataRetentionResult(
                expiredIntentCount,
                withdrawnIntentCount,
                travelScheduleCount
        );
        if (result.totalDeletedCount() > 0) {
            log.info(
                    "만료 또는 탈퇴 보관기간이 지난 여행 데이터를 삭제했습니다. expiredIntentCount={}, withdrawnIntentCount={}, travelScheduleCount={}",
                    expiredIntentCount,
                    withdrawnIntentCount,
                    travelScheduleCount
            );
        }
        return result;
    }

    public record TravelDataRetentionResult(
            int expiredIntentCount,
            int withdrawnIntentCount,
            int travelScheduleCount
    ) {
        public int totalDeletedCount() {
            return expiredIntentCount + withdrawnIntentCount + travelScheduleCount;
        }
    }
}
