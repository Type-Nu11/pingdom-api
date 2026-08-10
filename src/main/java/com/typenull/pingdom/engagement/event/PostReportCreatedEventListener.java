package com.typenull.pingdom.engagement.event;

import com.typenull.pingdom.engagement.domain.PostReport;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.moderation.outbox.notification.AdminNotificationOutboxPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PostReportCreatedEventListener {

    private final PostReportRepository postReportRepository;
    private final UserRepository userRepository;
    private final AdminNotificationOutboxPublisher adminNotificationOutboxPublisher;

    @Transactional
    @EventListener
    public void handle(PostReportCreatedEvent event) {
        Long postReportId = event.postReportId();

        PostReport report = postReportRepository.findById(postReportId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.REPORT_NOT_FOUND));

        User reporter = userRepository.findById(report.getReporterUserId())
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        // 최근 3개 신고 사유 복붙 체크
        if (postReportRepository.existsSameReasonInLatestThreeBeforeCurrent(
                report.getReporterUserId(),
                report.getId(),
                report.getReason())
        ){
            report.increaseReportScore(15);
        }

        // 10명 이상의 사람이 같은 이미지를 신고했다면
        if (postReportRepository.existsAtLeastTenDistinctReportersByReportedImageId(report.getReportedImageId())){
            report.decreaseReportScore(15);
        }

        adminNotificationOutboxPublisher.publishReportReceived(
                report.getId(),
                report.getReportedImageId()
        );
    }
}
