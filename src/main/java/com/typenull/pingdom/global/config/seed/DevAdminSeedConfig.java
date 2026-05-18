
package com.typenull.pingdom.global.config.seed;

import com.typenull.pingdom.domain.auth.domain.User;
import com.typenull.pingdom.domain.auth.domain.UserRole;
import com.typenull.pingdom.domain.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("dev")
public class DevAdminSeedConfig {

    private static final Logger log = LoggerFactory.getLogger(DevAdminSeedConfig.class);

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
            if (userRepository.existsByUsername(adminUsername)) {
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
