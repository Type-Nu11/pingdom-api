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

@Service
@RequiredArgsConstructor
@Slf4j
public class TravelDataRetentionService {

    private final UserRepository userRepository;
    private final TravelScheduleRepository travelScheduleRepository;
    private final UserCurrentActivityIntentRepository currentActivityIntentRepository;
    private final TravelDataRetentionProperties properties;
    private final Clock clock;

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
