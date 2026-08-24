package com.typenull.pingdom.shared.config.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class DevSeedProfileConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(DevAdminSeedConfig.class)
            .withBean(UserRepository.class, () -> mock(UserRepository.class))
            .withBean(MapPlaceRepository.class, () -> mock(MapPlaceRepository.class))
            .withBean(MapImageRepository.class, () -> mock(MapImageRepository.class))
            .withBean(MapBookmarkRepository.class, () -> mock(MapBookmarkRepository.class))
            .withBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class))
            .withBean(PlatformTransactionManager.class, SimplePlatformTransactionManager::new);

    @Test
    void local_프로필은_seed_runner와_로컬_기본값을_활성화한다() {
        contextRunner
                .withPropertyValues("spring.profiles.active=local")
                .run(context -> {
                    assertThat(context).hasBean("devAdminSeeder");
                    assertThat(context).hasBean("devDataSeeder");
                    assertThat(context).getBeans(ApplicationRunner.class).hasSize(2);
                    assertThat(context.getEnvironment().getProperty("analysis.docker.compose.enabled")).isEqualTo("true");
                    assertThat(context.getEnvironment().getProperty("springdoc.swagger-ui.enabled")).isEqualTo("true");
                    assertThat(context.getEnvironment().getProperty("seed.admin.username")).isEqualTo("admin");
                    assertThat(context.getEnvironment().getProperty("seed.admin.enabled")).isEqualTo("true");
                    assertThat(context.getEnvironment().getProperty("seed.dev-data.enabled")).isEqualTo("true");
                    assertThat(context.getEnvironment().getProperty("seed.dev-data.user-password")).isEqualTo("pingdom1234!");
                    assertThat(context.getEnvironment().getProperty("fcm.enabled")).isEqualTo("false");
                });
    }

    @Test
    void dev_프로필은_seed_runner와_dev_seed_기본값을_활성화한다() {
        contextRunner
                .withPropertyValues("spring.profiles.active=dev")
                .run(context -> {
                    assertThat(context).hasBean("devAdminSeeder");
                    assertThat(context).hasBean("devDataSeeder");
                    assertThat(context).getBeans(ApplicationRunner.class).hasSize(2);
                    assertThat(context.getEnvironment().getProperty("springdoc.swagger-ui.enabled")).isEqualTo("true");
                    assertThat(context.getEnvironment().getProperty("seed.admin.enabled")).isEqualTo("false");
                    assertThat(context.getEnvironment().getProperty("seed.dev-data.enabled")).isEqualTo("true");
                    assertThat(context.getEnvironment().getProperty("seed.dev-data.user-password")).isEqualTo("pingdom1234!");
                    assertThat(context.getEnvironment().getProperty("fcm.enabled")).isEqualTo("false");
                });
    }

    @Test
    void 일반_프로필에서는_seed_runner를_등록하지_않는다() {
        contextRunner
                .withPropertyValues("spring.profiles.active=test")
                .run(context -> {
                    assertThat(context).doesNotHaveBean("devAdminSeeder");
                    assertThat(context).doesNotHaveBean("devDataSeeder");
                    assertThat(context).getBeans(ApplicationRunner.class).isEmpty();
                });
    }

    @Test
    void local_프로필에서도_seed_토글을_끄면_개발_데이터를_변경하지_않는다() throws Exception {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=local",
                        "seed.admin.enabled=false",
                        "seed.dev-data.enabled=false"
                )
                .run(context -> {
                    context.getBean("devAdminSeeder", ApplicationRunner.class).run(null);
                    context.getBean("devDataSeeder", ApplicationRunner.class).run(null);

                    verifyNoInteractions(
                            context.getBean(UserRepository.class),
                            context.getBean(MapPlaceRepository.class),
                            context.getBean(MapImageRepository.class),
                            context.getBean(MapBookmarkRepository.class),
                            context.getBean(PasswordEncoder.class)
                    );
                });
    }

    private static class SimplePlatformTransactionManager implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
