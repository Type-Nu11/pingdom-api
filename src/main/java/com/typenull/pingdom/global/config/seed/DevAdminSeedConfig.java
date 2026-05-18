
package com.typenull.pingdom.global.config.seed;

import com.typenull.pingdom.domain.auth.domain.User;
import com.typenull.pingdom.domain.auth.domain.UserRole;
import com.typenull.pingdom.domain.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("dev")
public class DevAdminSeedConfig {

    private static final Logger log = LoggerFactory.getLogger(DevAdminSeedConfig.class);

    @Bean
    public ApplicationRunner devAdminSeeder(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            Environment environment
    ) {
        return args -> {
            String username = environment.getProperty("seed.admin.username", "admin");
            String name = environment.getProperty("seed.admin.name", "관리자");
            String email = environment.getProperty("seed.admin.email", "admin@local");
            String rawPassword = environment.getProperty("seed.admin.password", "admin1234!");

            if (userRepository.existsByUsername(username)) {
                return;
            }

            User admin = User.builder()
                    .username(username)
                    .name(name)
                    .email(email)
                    .emailVerified(true)
                    .password(passwordEncoder.encode(rawPassword))
                    .role(UserRole.ADMIN)
                    .build();

            userRepository.save(admin);
            log.warn("Dev admin user seeded. username={}, password={} (change via seed.admin.* properties)", username, rawPassword);
        };
    }
}

