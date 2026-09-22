package com.typenull.pingdom.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.typenull.pingdom.engagement.application.service.ReportPolicyService;
import com.typenull.pingdom.moderation.application.query.notification.AdminNotificationQueryService;
import com.typenull.pingdom.moderation.application.service.notification.AdminNotificationCommandService;
import com.typenull.pingdom.place.application.service.recommendation.feature.PlaceRecommendationFeatureLogService;
import com.typenull.pingdom.place.application.service.recommendation.query.PlaceRecommendationQueryServiceImpl;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.RecommendationStage;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

class ServiceTransactionConventionTest {

    private static final String BASE_PACKAGE = "com.typenull.pingdom";
    private static final String APPLICATION_QUERY_PACKAGE = ".application.query.";

    /**
     * 저장소 필드를 직접 가진 application.query 서비스의 공개 메서드마다 유효한 readOnly 트랜잭션이 있는지 검증한다.
     */
    @Test
    @DisplayName("Repository를 직접 보유한 application.query Service는 조회 전용 트랜잭션을 사용한다")
    void requiresReadOnlyRepositoryQueryServices() throws ClassNotFoundException {
        List<String> violations = new ArrayList<>();

        for (Class<?> service : serviceClasses()) {
            if (!isRepositoryBackedApplicationQueryService(service)) {
                continue;
            }

            for (Method method : service.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers())) {
                    continue;
                }

                Transactional transactional = effectiveTransaction(service, method);
                if (transactional == null || !transactional.readOnly()) {
                    violations.add(service.getSimpleName() + "." + method.getName());
                }
            }
        }

        assertTrue(
                violations.isEmpty(),
                "조회 전용 트랜잭션이 없는 Query Service: " + String.join(", ", violations)
        );
    }

    /**
     * 신고 정책과 관리자 알림 명령 서비스의 클래스 트랜잭션이 쓰기 가능하도록 선언되어 있는지 검증한다.
     */
    @Test
    @DisplayName("상태 변경 Service는 쓰기 트랜잭션을 사용한다")
    void commandServicesUseWriteTransactions() {
        assertWriteTransaction(ReportPolicyService.class);
        assertWriteTransaction(AdminNotificationCommandService.class);
    }

    /**
     * 관리자 알림 조회는 목록·미읽음 수, 명령은 단건·전체 읽음 변경 메서드만 소유하는지 reflection으로 검증한다.
     */
    @Test
    @DisplayName("관리자 알림 Query와 Command 책임을 분리한다")
    void adminNotificationResponsibilitiesAreSeparated() {
        Set<String> queryMethods = Arrays.stream(AdminNotificationQueryService.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
        Set<String> commandMethods = Arrays.stream(AdminNotificationCommandService.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertThat(queryMethods).containsExactlyInAnyOrder("listNotifications", "countUnread");
        assertThat(commandMethods).containsExactlyInAnyOrder("markAsRead", "markAllAsRead");
    }

    /**
     * 추천 응답과 노출 관측 기록 메서드는 조회성 이름에도 상태를 저장하므로 명시한 시그니처의 유효 트랜잭션이 쓰기인지 검증한다.
     */
    @Test
    @DisplayName("추천 응답과 관측 기록 유스케이스는 명시적 쓰기 트랜잭션 예외로 둔다")
    void requiresWriteTransactionForRecommendationObservations() throws NoSuchMethodException {
        assertWriteTransaction(
                PlaceRecommendationQueryServiceImpl.class,
                "recommendAndRecordObservations",
                Long.class,
                double.class,
                double.class,
                int.class,
                double.class,
                String.class
        );
        assertWriteTransaction(
                PlaceRecommendationFeatureLogService.class,
                "recordShownCandidates",
                String.class,
                Long.class,
                String.class,
                RecommendationStage.class,
                List.class
        );
    }

    /**
     * 기본 패키지의 Service 컴포넌트를 스캔해 트랜잭션 규칙 검사 대상 클래스를 수집한다.
     */
    private List<Class<?>> serviceClasses() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Service.class));

        List<Class<?>> services = new ArrayList<>();
        for (BeanDefinition beanDefinition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            services.add(Class.forName(beanDefinition.getBeanClassName()));
        }
        return services;
    }

    /**
     * application.query 패키지에 속하고 Repository 접미사 타입 필드를 직접 가진 서비스만 일반 조회 규칙의 대상으로 분류한다.
     */
    private boolean isRepositoryBackedApplicationQueryService(Class<?> service) {
        if (!service.getPackageName().contains(APPLICATION_QUERY_PACKAGE)) {
            return false;
        }
        return Arrays.stream(service.getDeclaredFields())
                .anyMatch(field -> field.getType().getSimpleName().endsWith("Repository"));
    }

    /**
     * 합성 어노테이션을 포함해 메서드의 트랜잭션 선언을 우선하고 없으면 클래스 선언으로 보완한다.
     */
    private Transactional effectiveTransaction(Class<?> service, Method method) {
        Transactional methodTransaction =
                AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);
        if (methodTransaction != null) {
            return methodTransaction;
        }
        return AnnotatedElementUtils.findMergedAnnotation(service, Transactional.class);
    }

    /**
     * 서비스 클래스의 트랜잭션이 존재하고 readOnly가 false인지 검사하며 실패 메시지에 서비스명을 포함한다.
     */
    private void assertWriteTransaction(Class<?> service) {
        Transactional transactional =
                AnnotatedElementUtils.findMergedAnnotation(service, Transactional.class);
        assertThat(transactional)
                .as("%s의 class-level transaction", service.getSimpleName())
                .isNotNull();
        assertThat(transactional.readOnly())
                .as("%s의 readOnly", service.getSimpleName())
                .isFalse();
    }

    /**
     * 지정 메서드 시그니처의 유효 트랜잭션이 존재하고 쓰기 가능한지 검사해 오버로드를 구분한다.
     */
    private void assertWriteTransaction(Class<?> service, String methodName, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = service.getDeclaredMethod(methodName, parameterTypes);
        Transactional transactional = effectiveTransaction(service, method);

        assertThat(transactional)
                .as("%s.%s의 transaction", service.getSimpleName(), methodName)
                .isNotNull();
        assertThat(transactional.readOnly())
                .as("%s.%s의 readOnly", service.getSimpleName(), methodName)
                .isFalse();
    }
}
