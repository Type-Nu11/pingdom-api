package com.typenull.pingdom.analysis.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.typenull.pingdom.analysis.api.dto.LocationAnalysisReportUpdateRequest;
import com.typenull.pingdom.analysis.application.LocationAnalysisReportArchiveService;
import com.typenull.pingdom.analysis.domain.LocationAnalysisReport;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportErrorCode;
import com.typenull.pingdom.analysis.domain.exception.AnalysisReportException;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("postgres-integration")
@Testcontainers
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.typenull.pingdom.analysis.infrastructure.LocationAnalysisReportPayloadPostgreSqlIntegrationTest$SqlCapture"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = LocationAnalysisReportPayloadPostgreSqlIntegrationTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LocationAnalysisReportPayloadPostgreSqlIntegrationTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final String HTML = "<p>생성 당시 원문</p>".repeat(32_768);
    private static final byte[] PDF = new byte[512 * 1024];

    static {
        Arrays.fill(PDF, (byte) 42);
    }

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = LocationAnalysisReport.class)
    @EnableJpaRepositories(basePackageClasses = LocationAnalysisReportRepository.class)
    @Import(LocationAnalysisReportArchiveService.class)
    static class Config {
        @Bean
        Clock clock() {
            return CLOCK;
        }
    }

    @Autowired private LocationAnalysisReportRepository repository;
    @Autowired private LocationAnalysisReportArchiveService service;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    @BeforeEach
    void saveReport() {
        transaction().executeWithoutResult(status -> entityManager.persist(report("report-1")));
        SqlCapture.reset();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM location_analysis_report");
        SqlCapture.clear();
    }

    @Test
    void updatesOnlyMetadataAndVersionWithoutSelectingOrUpdatingPayload() {
        var response = service.update("report-1", 7L,
                new LocationAnalysisReportUpdateRequest("수정된 제목", " New@Example.com "));

        assertThat(response.reportName()).isEqualTo("수정된 제목");
        assertThat(response.email()).isEqualTo("new@example.com");
        assertThat(SqlCapture.statements()).isNotEmpty()
                .noneMatch(sql -> sql.contains("pdf_content") || sql.contains("html_content"));
        assertThat(SqlCapture.statements()).anyMatch(sql -> sql.startsWith("update ")
                && sql.contains("owner_user_id") && sql.contains("version"));
        assertThat(jdbc.queryForObject("SELECT version FROM location_analysis_report WHERE report_id = 'report-1'",
                Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT updated_at FROM location_analysis_report WHERE report_id = 'report-1'",
                LocalDateTime.class)).isEqualTo(NOW);
        assertStoredPayloadAndOwner();
    }

    @Test
    void deletesWithoutLoadingPayloadAndRemovesTheOwnedRow() {
        service.delete("report-1", 7L);

        assertThat(SqlCapture.statements()).isNotEmpty()
                .noneMatch(sql -> sql.contains("pdf_content") || sql.contains("html_content"));
        assertThat(SqlCapture.statements()).anyMatch(sql -> sql.startsWith("delete ")
                && sql.contains("owner_user_id") && sql.contains("version"));
        assertThat(repository.findById("report-1")).isEmpty();
    }

    @Test
    void readsOnlyTheRequestedPayloadAndPreservesDownloadMetadata() {
        var download = service.download("report-1", 7L);

        assertThat(download.reportId()).isEqualTo("report-1");
        assertThat(download.reportName()).isEqualTo("분석");
        assertThat(download.publishedDate()).isEqualTo(LocalDate.of(2026, 11, 12));
        assertThat(download.version()).isZero();
        assertThat(download.content()).containsExactly(PDF);
        assertThat(SqlCapture.statements()).hasSize(1).allMatch(sql -> sql.contains("pdf_content")
                && !sql.contains("html_content"));
        SqlCapture.reset();

        assertThat(service.html("report-1", 7L)).isEqualTo(HTML);
        assertThat(SqlCapture.statements()).hasSize(1).allMatch(sql -> sql.contains("html_content")
                && !sql.contains("pdf_content"));
    }

    @Test
    void ownerScopedReadsAndCommandsCannotAccessAnotherOwnerOrQuarantinedReport() {
        assertNotFound(() -> service.update("report-1", 8L,
                new LocationAnalysisReportUpdateRequest("탈취", "other@example.com")));
        assertNotFound(() -> service.delete("report-1", 8L));
        assertNotFound(() -> service.download("report-1", 8L));
        transaction().executeWithoutResult(status -> {
            assertThat(repository.updateMetadataIfVersionMatches("report-1", 8L, 0,
                    "탈취", "other@example.com", NOW)).isZero();
            assertThat(repository.deleteIfVersionMatches("report-1", 8L, 0)).isZero();
        });
        jdbc.update("UPDATE location_analysis_report SET owner_user_id = NULL WHERE report_id = 'report-1'");

        assertNotFound(() -> service.update("report-1", 7L,
                new LocationAnalysisReportUpdateRequest("탈취", "owner@example.com")));
        assertNotFound(() -> service.delete("report-1", 7L));
        assertNotFound(() -> service.download("report-1", 7L));
    }

    @Test
    void clearsStaleManagedReportsAfterBulkUpdateAndDelete() {
        transaction().executeWithoutResult(status -> {
            var managed = repository.findById("report-1").orElseThrow();

            service.update("report-1", 7L, new LocationAnalysisReportUpdateRequest("변경", "new@example.com"));

            assertThat(entityManager.contains(managed)).isFalse();
            var reloaded = repository.findById("report-1").orElseThrow();
            assertThat(reloaded.getReportName()).isEqualTo("변경");
            assertThat(reloaded.getVersion()).isEqualTo(1L);
            service.delete("report-1", 7L);
            assertThat(entityManager.contains(reloaded)).isFalse();
            assertThat(repository.findById("report-1")).isEmpty();
        });
        assertThat(repository.findById("report-1")).isEmpty();
    }

    @Test
    void flushesOtherPendingWritesBeforeClearingThePersistenceContext() {
        transaction().executeWithoutResult(status -> {
            var pending = report("pending-report");
            entityManager.persist(pending);

            service.update("report-1", 7L, new LocationAnalysisReportUpdateRequest("변경", "new@example.com"));

            assertThat(entityManager.contains(pending)).isFalse();
        });
        assertThat(repository.findById("pending-report")).isPresent();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsTheStaleVersionAcrossSeparateConcurrentTransactions(boolean deleteSecond) throws Exception {
        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch releaseSecond = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> transaction().execute(status -> {
                long version = readVersionAndWait(bothRead, releaseFirst);
                return repository.updateMetadataIfVersionMatches("report-1", 7L, version,
                        "먼저 커밋", "first@example.com", NOW);
            }));
            Future<Integer> second = executor.submit(() -> transaction().execute(status -> {
                long version = readVersionAndWait(bothRead, releaseSecond);
                return deleteSecond
                        ? repository.deleteIfVersionMatches("report-1", 7L, version)
                        : repository.updateMetadataIfVersionMatches("report-1", 7L, version,
                                "늦은 변경", "second@example.com", NOW);
            }));
            assertThat(bothRead.await(10, TimeUnit.SECONDS)).isTrue();
            releaseFirst.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(1);
            releaseSecond.countDown();
            assertThat(second.get(10, TimeUnit.SECONDS)).isZero();
        } finally {
            releaseFirst.countDown();
            releaseSecond.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(service.get("report-1", 7L).reportName()).isEqualTo("먼저 커밋");
        assertThat(jdbc.queryForObject("SELECT version FROM location_analysis_report WHERE report_id = 'report-1'",
                Long.class)).isEqualTo(1L);
        assertStoredPayloadAndOwner();
    }

    private long readVersionAndWait(CountDownLatch bothRead, CountDownLatch release) {
        long version = repository.findMetadataByReportIdAndOwnerUserId("report-1", 7L).orElseThrow().getVersion();
        bothRead.countDown();
        try {
            if (!release.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("트랜잭션 실행 순서 제어 시간 초과");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
        return version;
    }

    private void assertStoredPayloadAndOwner() {
        assertThat(jdbc.queryForObject("SELECT pdf_content FROM location_analysis_report WHERE report_id = 'report-1'",
                byte[].class)).containsExactly(PDF);
        assertThat(jdbc.queryForObject("SELECT html_content FROM location_analysis_report WHERE report_id = 'report-1'",
                String.class)).isEqualTo(HTML);
        assertThat(jdbc.queryForObject("SELECT owner_user_id FROM location_analysis_report WHERE report_id = 'report-1'",
                Long.class)).isEqualTo(7L);
    }

    private void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(AnalysisReportException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(AnalysisReportErrorCode.ANALYSIS_REPORT_NOT_FOUND));
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private LocationAnalysisReport report(String reportId) {
        return LocationAnalysisReport.create(reportId, 7L, "분석", "카페", "서울", null, null,
                "owner@example.com", true, LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 11),
                HTML, PDF, NOW.minusMinutes(1));
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
