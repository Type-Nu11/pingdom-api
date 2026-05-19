
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

@Configuration
@Profile("dev")
@Slf4j
public class DevAdminSeedConfig {

    @Value("${seed.admin.username}")
    private String adminUsername;

    @Value("${seed.admin.name}")
    private String adminName;

    @Value("${seed.admin.email}")
    private String adminEmail;

    @Value("${seed.admin.password}")
    private String adminPassword;

    @Bean
    public ApplicationRunner devAdminSeeder(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        return args -> {
            if (!StringUtils.hasText(adminUsername) || !StringUtils.hasText(adminEmail)) {
                log.warn("Dev admin seed skipped: adminUsername or adminEmail is empty.");
                return;
            }
            if (userRepository.existsByUsername(adminUsername)) {
                log.info("Dev admin seed skipped (username already exists). username={}", adminUsername);
                return;
            }
            if (userRepository.existsByEmail(adminEmail)) {
                log.info("Dev admin seed skipped (email already exists). email={}", adminEmail);
                return;
            }

            User admin = User.builder()
                    .username(adminUsername)
                    .name(adminName)
                    .email(adminEmail)
                    .emailVerified(true)
                    .password(passwordEncoder.encode(adminPassword))
                    .role(UserRole.ADMIN)
                    .build();

            userRepository.save(admin);
            log.info("Dev admin user seeded. username={} (password configurable via seed.admin.* properties)", adminUsername);
        };
    }
}
