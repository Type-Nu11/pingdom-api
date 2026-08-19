package com.typenull.pingdom.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.Entity;
import jakarta.persistence.LockModeType;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

/** Entity와 Repository의 위치·명명 규칙을 정적 소스 검사로 검증합니다. */
class EntityRepositoryConventionTest {

    private static final Path JAVA_SOURCE_ROOT = Path.of("src/main/java");

    @Test
    @DisplayName("Entity는 범용 Setter 대신 의미 있는 상태 변경 메서드를 사용한다")
    void entitiesDoNotExposeGenericSetters() throws IOException, ClassNotFoundException {
        List<String> violations = new ArrayList<>();

        for (Class<?> entity : classesContaining("@Entity")) {
            if (!entity.isAnnotationPresent(Entity.class)) {
                continue;
            }

            for (Method method : entity.getDeclaredMethods()) {
                if (Modifier.isPublic(method.getModifiers()) && method.getName().matches("set[A-Z].*")) {
                    violations.add(entity.getSimpleName() + "." + method.getName());
                }
            }
        }

        assertTrue(violations.isEmpty(), "범용 Setter가 노출된 Entity: " + String.join(", ", violations));
    }

    @Test
    @DisplayName("비관적 쓰기 잠금 조회는 ForUpdate 접미사를 사용한다")
    void pessimisticWriteRepositoryMethodsUseForUpdateSuffix() throws IOException, ClassNotFoundException {
        List<String> violations = new ArrayList<>();

        for (Class<?> repository : classesContaining("extends JpaRepository")) {
            for (Method method : repository.getDeclaredMethods()) {
                Lock lock = method.getAnnotation(Lock.class);
                if (lock != null
                        && lock.value() == LockModeType.PESSIMISTIC_WRITE
                        && !method.getName().endsWith("ForUpdate")) {
                    violations.add(repository.getSimpleName() + "." + method.getName());
                }
            }
        }

        assertTrue(
                violations.isEmpty(),
                "ForUpdate 접미사가 없는 쓰기 잠금 조회: " + String.join(", ", violations)
        );
    }

    private List<Class<?>> classesContaining(String marker) throws IOException, ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(JAVA_SOURCE_ROOT)) {
            for (Path sourceFile : paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> contains(path, marker))
                    .toList()) {
                classes.add(Class.forName(toClassName(sourceFile)));
            }
        }
        return classes;
    }

    private boolean contains(Path sourceFile, String marker) {
        try {
            return Files.readString(sourceFile).contains(marker);
        } catch (IOException exception) {
            throw new IllegalStateException("Java source를 읽을 수 없습니다: " + sourceFile, exception);
        }
    }

    private String toClassName(Path sourceFile) {
        String relativePath = JAVA_SOURCE_ROOT.relativize(sourceFile).toString();
        return relativePath
                .substring(0, relativePath.length() - ".java".length())
                .replace(sourceFile.getFileSystem().getSeparator(), ".");
    }
}
