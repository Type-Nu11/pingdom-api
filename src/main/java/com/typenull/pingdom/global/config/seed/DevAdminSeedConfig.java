
package com.typenull.pingdom.global.config.seed;

import com.typenull.pingdom.domain.auth.domain.User;
import com.typenull.pingdom.domain.auth.domain.UserRole;
import com.typenull.pingdom.domain.auth.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@Profile("dev")
@Slf4j
public class DevAdminSeedConfig {

    @Value("${seed.admin.username}")
    private String adminUsername;

    @Value("${seed.admin.email}")
    private String adminEmail;

    @Value("${seed.admin.password}")
    private String adminPassword;

    @Bean
    public ApplicationRunner devAdminSeeder(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            PlatformTransactionManager transactionManager
    ) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        return args -> {
            transactionTemplate.executeWithoutResult(status -> {
                if (!StringUtils.hasText(adminUsername)
                        || !StringUtils.hasText(adminEmail)
                        || !StringUtils.hasText(adminPassword)) {
                    log.warn("Dev admin seed 스킵: 필수 관리자 정보(username, email, password) 중 일부가 비어있습니다.");
                    return;
                }
                if (userRepository.existsByUsername(adminUsername)) {
                    log.info("Dev admin seed 스킵: 이미 존재하는 username 입니다. username={}", adminUsername);
                    return;
                }
                if (userRepository.existsByEmail(adminEmail)) {
                    log.info("Dev admin seed 스킵: 이미 존재하는 email 입니다. email={}", adminEmail);
                    return;
                }

                User admin = User.builder()
                        .username(adminUsername)
                        .email(adminEmail)
                        .emailVerified(true)
                        .password(passwordEncoder.encode(adminPassword))
                        .role(UserRole.ADMIN)
                        .build();

                userRepository.save(admin);
                log.info("Dev admin user seeded. username={} (password는 seed.admin.* 설정으로 변경 가능합니다)", adminUsername);
            });
        };
    }
}
