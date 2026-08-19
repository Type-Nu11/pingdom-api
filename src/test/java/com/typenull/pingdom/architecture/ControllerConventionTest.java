package com.typenull.pingdom.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.typenull.pingdom.shared.security.annotation.CurrentUser;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import jakarta.persistence.Entity;
import jakarta.validation.Valid;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class ControllerConventionTest {

    private static final String BASE_PACKAGE = "com.typenull.pingdom";

    @Test
    @DisplayName("RequestBody DTO는 Valid로 검증한다")
    void requestBodyParametersUseValid() throws ClassNotFoundException {
        List<String> violations = new ArrayList<>();

        for (Class<?> controller : controllerClasses()) {
            for (Method method : controller.getDeclaredMethods()) {
                if (!isRequestHandler(method)) {
                    continue;
                }

                for (Parameter parameter : method.getParameters()) {
                    if (parameter.isAnnotationPresent(RequestBody.class)
                            && !parameter.isAnnotationPresent(Valid.class)) {
                        violations.add(controller.getSimpleName() + "." + method.getName());
                    }
                }
            }
        }

        assertTrue(
                violations.isEmpty(),
                "@Valid가 없는 RequestBody: " + String.join(", ", violations)
        );
    }

    @Test
    @DisplayName("Controller는 Entity를 응답으로 직접 반환하지 않는다")
    void controllersDoNotExposeEntities() throws ClassNotFoundException {
        List<String> violations = new ArrayList<>();

        for (Class<?> controller : controllerClasses()) {
            for (Method method : controller.getDeclaredMethods()) {
                if (isRequestHandler(method) && containsEntity(method.getGenericReturnType())) {
                    violations.add(controller.getSimpleName() + "." + method.getName());
                }
            }
        }

        assertTrue(
                violations.isEmpty(),
                "Entity를 반환하는 Controller: " + String.join(", ", violations)
        );
    }

    @Test
    @DisplayName("Controller의 JWT 인증 사용자는 CurrentUser로 전달한다")
    void jwtPrincipalsUseCurrentUser() throws ClassNotFoundException {
        List<String> violations = new ArrayList<>();

        for (Class<?> controller : controllerClasses()) {
            for (Method method : controller.getDeclaredMethods()) {
                if (!isRequestHandler(method)) {
                    continue;
                }
                for (Parameter parameter : method.getParameters()) {
                    if (parameter.getType() == JwtAuthenticatedUser.class
                            && !parameter.isAnnotationPresent(CurrentUser.class)) {
                        violations.add(controller.getSimpleName() + "." + method.getName());
                    }
                }
            }
        }

        assertTrue(
                violations.isEmpty(),
                "@CurrentUser를 사용하지 않은 JWT 사용자 파라미터: " + String.join(", ", violations)
        );
    }

    @Test
    @DisplayName("Controller는 공통 권한 애노테이션을 사용한다")
    void controllersDoNotDeclarePreAuthorizeDirectly() throws ClassNotFoundException {
        List<String> violations = new ArrayList<>();

        for (Class<?> controller : controllerClasses()) {
            if (controller.getDeclaredAnnotation(PreAuthorize.class) != null) {
                violations.add(controller.getSimpleName());
            }
            for (Method method : controller.getDeclaredMethods()) {
                if (method.getDeclaredAnnotation(PreAuthorize.class) != null) {
                    violations.add(controller.getSimpleName() + "." + method.getName());
                }
            }
        }

        assertTrue(
                violations.isEmpty(),
                "@PreAuthorize를 직접 사용한 Controller: " + String.join(", ", violations)
        );
    }

    private List<Class<?>> controllerClasses() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<Class<?>> controllers = new ArrayList<>();
        for (BeanDefinition beanDefinition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            controllers.add(Class.forName(beanDefinition.getBeanClassName()));
        }
        return controllers;
    }

    private boolean isRequestHandler(Method method) {
        return AnnotationUtils.findAnnotation(method, RequestMapping.class) != null;
    }

    private boolean containsEntity(Type type) {
        if (type instanceof Class<?> clazz) {
            if (clazz.isArray()) {
                return containsEntity(clazz.getComponentType());
            }
            return clazz.isAnnotationPresent(Entity.class);
        }

        if (type instanceof ParameterizedType parameterizedType) {
            for (Type argument : parameterizedType.getActualTypeArguments()) {
                if (containsEntity(argument)) {
                    return true;
                }
            }
        }

        return false;
    }
}
