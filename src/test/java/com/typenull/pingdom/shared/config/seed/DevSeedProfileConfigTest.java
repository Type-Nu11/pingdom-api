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

    /**
     * local에서 관리자·개발 데이터 runner 2개와 Compose/Swagger/seed 기본값·개발 사용자 비밀번호·FCM 비활성이 설정되는지 검증.
     */
    @Test
    void loadsLocalSeedDefaults() {
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

    /**
     * dev에서 runner 2개를 등록하되 관리자 seed 비활성·개발 데이터 seed 활성·Swagger 활성·FCM 비활성 기본값을 사용하는지 검증.
     */
    @Test
    void loadsDevSeedDefaults() {
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

    /**
     * test 프로필에는 관리자·개발 데이터 seeder와 ApplicationRunner가 등록되지 않는지 검증.
     */
    @Test
    void skipsSeedsInOtherProfiles() {
        contextRunner
                .withPropertyValues("spring.profiles.active=test")
                .run(context -> {
                    assertThat(context).doesNotHaveBean("devAdminSeeder");
                    assertThat(context).doesNotHaveBean("devDataSeeder");
                    assertThat(context).getBeans(ApplicationRunner.class).isEmpty();
                });
    }

    /**
     * local에서도 두 seed 토글을 끄면 runner 실행이 저장소·비밀번호 인코더를 호출하지 않는지 검증.
     */
    @Test
    void disabledSeedsAvoidDataChanges() throws Exception {
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

        /**
         * 실제 DB 연결 없이 seed 설정 컨텍스트에 필요한 트랜잭션 대역 상태를 제공.
         */
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        /**
         * 설정 검증용 트랜잭션 대역의 빈 커밋 훅.
         */
        @Override
        public void commit(TransactionStatus status) {
        }

        /**
         * 설정 검증용 트랜잭션 대역의 빈 롤백 훅.
         */
        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
