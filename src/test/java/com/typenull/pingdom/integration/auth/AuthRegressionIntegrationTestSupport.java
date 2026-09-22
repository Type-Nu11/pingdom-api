package com.typenull.pingdom.integration.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.engagement.infrastructure.persistence.MapImageLikeRepository;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.repository.OAuthAccountRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.infrastructure.persistence.UserSanctionHistoryRepository;
import com.typenull.pingdom.notification.infrastructure.persistence.NotificationsRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.outbox.infrastructure.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 인증 회귀 테스트가 공유하는 사용자 fixture와 연관 데이터 정리 절차를 제공.
 */
abstract class AuthRegressionIntegrationTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    private OAuthAccountRepository oAuthAccountRepository;

    @Autowired
    private UserSanctionHistoryRepository userSanctionHistoryRepository;

    @Autowired
    private NotificationsRepository notificationsRepository;

    @Autowired
    private PostReportRepository postReportRepository;

    @Autowired
    private MapImageLikeRepository mapImageLikeRepository;

    @Autowired
    private MapBookmarkRepository mapBookmarkRepository;

    @Autowired
    private MapImageRepository mapImageRepository;

    @Autowired
    private MapPlaceRepository mapPlaceRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    /**
     * 신고·알림·좋아요·북마크·게시물 등 사용자 연관 데이터를 사용자보다 먼저 삭제해 인증 회귀 시나리오를 격리.
     */
    @BeforeEach
    void cleanDatabase() {
        postReportRepository.deleteAllInBatch();
        notificationsRepository.deleteAllInBatch();
        mapImageLikeRepository.deleteAllInBatch();
        mapBookmarkRepository.deleteAllInBatch();
        mapImageRepository.deleteAllInBatch();
        mapPlaceRepository.deleteAllInBatch();
        userSanctionHistoryRepository.deleteAllInBatch();
        oAuthAccountRepository.deleteAllInBatch();
        outboxEventRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    /**
     * 암호화된 공통 비밀번호를 가진 일반 사용자를 저장하고 flush해 실제 로그인에 사용.
     */
    protected User createUser(String username) {
        return userRepository.saveAndFlush(User.builder()
                .username(username)
                .email(username + "@example.com")
                .password(passwordEncoder.encode("password123"))
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .role(UserRole.USER)
                .build());
    }
}
