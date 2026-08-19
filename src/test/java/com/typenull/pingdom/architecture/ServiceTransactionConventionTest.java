package com.typenull.pingdom.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.typenull.pingdom.engagement.application.service.ReportPolicyService;
import com.typenull.pingdom.moderation.application.query.notification.AdminNotificationQueryService;
import com.typenull.pingdom.moderation.application.service.notification.AdminNotificationCommandService;
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

    @Test
    @DisplayName("Repository를 사용하는 Query Service는 조회 전용 트랜잭션을 사용한다")
    void repositoryQueryServicesUseReadOnlyTransactions() throws ClassNotFoundException {
        List<String> violations = new ArrayList<>();

        for (Class<?> service : serviceClasses()) {
            if (!isRepositoryQueryService(service)) {
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

    @Test
    @DisplayName("상태 변경 Service는 쓰기 트랜잭션을 사용한다")
    void commandServicesUseWriteTransactions() {
        assertWriteTransaction(ReportPolicyService.class);
        assertWriteTransaction(AdminNotificationCommandService.class);
    }

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

    private boolean isRepositoryQueryService(Class<?> service) {
        if (!service.getPackageName().contains(".application.query.")) {
            return false;
        }
        return Arrays.stream(service.getDeclaredFields())
                .anyMatch(field -> field.getType().getSimpleName().endsWith("Repository"));
    }

    private Transactional effectiveTransaction(Class<?> service, Method method) {
        Transactional methodTransaction =
                AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);
        if (methodTransaction != null) {
            return methodTransaction;
        }
        return AnnotatedElementUtils.findMergedAnnotation(service, Transactional.class);
    }

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
}
