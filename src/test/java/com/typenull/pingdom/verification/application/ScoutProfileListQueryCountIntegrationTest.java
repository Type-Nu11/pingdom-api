package com.typenull.pingdom.verification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.typenull.pingdom.identity.application.service.admin.AdminRoleAuthorizationService;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.shared.observability.ScoutFieldReportMetrics;
import com.typenull.pingdom.verification.domain.ScoutActivityEligibility;
import com.typenull.pingdom.verification.domain.ScoutProfile;
import com.typenull.pingdom.verification.infrastructure.ScoutActivityEligibilityRepository;
import com.typenull.pingdom.verification.infrastructure.ScoutProfileRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.LongStream;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

@Tag("integration")
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.typenull.pingdom.verification.application.ScoutProfileListQueryCountIntegrationTest$SqlCapture"
})
@ContextConfiguration(classes = ScoutProfileListQueryCountIntegrationTest.Config.class)
class ScoutProfileListQueryCountIntegrationTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Autowired private ScoutProfileRepository profileRepository;
    @Autowired private ScoutActivityEligibilityRepository eligibilityRepository;
    @Autowired private EntityManager entityManager;

    private ScoutProfileService service;

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = ScoutProfile.class)
    @EnableJpaRepositories(
            basePackageClasses = ScoutProfileRepository.class,
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = {ScoutProfileRepository.class, ScoutActivityEligibilityRepository.class}
            )
    )
    static class Config {
    }

    @BeforeEach
    void setUp() {
        service = new ScoutProfileService(
                mock(UserRepository.class),
                profileRepository,
                eligibilityRepository,
                mock(AdminRoleAuthorizationService.class),
                mock(AdminAuditLogService.class),
                mock(ScoutFieldReportMetrics.class),
                mock(ApplicationEventPublisher.class),
                CLOCK
        );
    }

    @AfterEach
    void clearCapture() {
        SqlCapture.clear();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 20, 100})
    void executesOneEligibilitySelectRegardlessOfPageSize(int count) {
        for (long id = 1; id <= count + 1; id++) {
            persistProfile(id);
        }
        entityManager.flush();
        entityManager.clear();
        SqlCapture.reset();

        var result = service.listForAdmin(999L, null, 1, count);

        var expectedIds = LongStream.rangeClosed(2, count + 1).boxed()
                .sorted(Comparator.reverseOrder()).toList();
        assertThat(result.profiles()).extracting(item -> item.userId()).containsExactlyElementsOf(expectedIds);
        assertThat(result.totalCount()).isEqualTo(count + 1);
        assertThat(result.hasNext()).isTrue();
        assertThat(eligibilitySelects()).hasSize(1);
        assertThat(SqlCapture.statements()).hasSize(3);
    }

    @Test
    void executesNoEligibilitySelectForEmptyPage() {
        persistProfile(1L);
        entityManager.flush();
        entityManager.clear();
        SqlCapture.reset();

        var result = service.listForAdmin(999L, null, 3, 20);

        assertThat(result.profiles()).isEmpty();
        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(eligibilitySelects()).isEmpty();
    }

    private void persistProfile(long id) {
        entityManager.persist(ScoutProfile.pending(id, "Scout-" + id, null, NOW));
        entityManager.persist(ScoutActivityEligibility.pending(id, NOW));
    }

    private List<String> eligibilitySelects() {
        return SqlCapture.statements().stream()
                .filter(sql -> sql.contains(" from scout_activity_eligibility "))
                .toList();
    }

    public static class SqlCapture implements StatementInspector {
        private static final ThreadLocal<List<String>> STATEMENTS = ThreadLocal.withInitial(ArrayList::new);

        @Override
        public String inspect(String sql) {
            STATEMENTS.get().add(sql.toLowerCase(Locale.ROOT));
            return sql;
        }

        static void reset() {
            STATEMENTS.get().clear();
        }

        static List<String> statements() {
            return List.copyOf(STATEMENTS.get());
        }

        static void clear() {
            STATEMENTS.remove();
        }
    }
}
