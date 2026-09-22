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

/** Entity와 Repository의 위치·명명 규칙을 정적 소스 검사로 검증. */
class EntityRepositoryConventionTest {

    private static final Path JAVA_SOURCE_ROOT = Path.of("src/main/java");

    /**
     * Entity 선언이 있는 운영 클래스를 로드해 공개 set 접두사 메서드가 노출되지 않는지 검증.
     */
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

    /**
     * JpaRepository 소스에서 선언한 PESSIMISTIC_WRITE 조회 메서드가 ForUpdate 접미사로 잠금 의도를 드러내는지 검증.
     */
    @Test
    @DisplayName("비관적 쓰기 잠금 조회는 ForUpdate 접미사를 사용한다")
    void requiresForUpdateWriteLockSuffix() throws IOException, ClassNotFoundException {
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

    /**
     * 지정 소스 표식을 포함한 Java 파일을 찾아 reflection 검사에 필요한 클래스로 로드.
     */
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

    /**
     * 소스의 표식 포함 여부를 확인하고 파일을 읽지 못하면 해당 경로를 포함한 오류로 검사 실패를 명시.
     */
    private boolean contains(Path sourceFile, String marker) {
        try {
            return Files.readString(sourceFile).contains(marker);
        } catch (IOException exception) {
            throw new IllegalStateException("Java source를 읽을 수 없습니다: " + sourceFile, exception);
        }
    }

    /**
     * 운영 소스 루트 상대 경로에서 확장자를 제거하고 디렉터리 구분자를 패키지 구분자로 바꾸어 클래스명을 획득.
     */
    private String toClassName(Path sourceFile) {
        String relativePath = JAVA_SOURCE_ROOT.relativize(sourceFile).toString();
        return relativePath
                .substring(0, relativePath.length() - ".java".length())
                .replace(sourceFile.getFileSystem().getSeparator(), ".");
    }
}
