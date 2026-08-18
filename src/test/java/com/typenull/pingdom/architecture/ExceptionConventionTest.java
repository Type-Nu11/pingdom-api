package com.typenull.pingdom.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.typenull.pingdom.shared.exception.DomainException;
import com.typenull.pingdom.shared.exception.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExceptionConventionTest {

    private static final Path JAVA_SOURCE_ROOT = Path.of("src/main/java");
    private static final Pattern TYPE_DECLARATION = Pattern.compile(
            "public\\s+(?:enum|class)\\s+(\\w+)"
    );

    @Test
    @DisplayName("도메인 ErrorCode는 공통 ErrorCode 계약과 대문자 이름을 사용한다")
    void domainErrorCodesUseSharedContract() throws IOException, ClassNotFoundException {
        List<String> violations = new ArrayList<>();

        for (Class<?> errorCodeClass : classesInDomainExceptionPackages("ErrorCode.java")) {
            if (!ErrorCode.class.isAssignableFrom(errorCodeClass)) {
                violations.add(errorCodeClass.getName() + "가 ErrorCode를 구현하지 않습니다.");
                continue;
            }

            for (Object constant : errorCodeClass.getEnumConstants()) {
                ErrorCode errorCode = (ErrorCode) constant;
                if (!errorCode.getCode().matches("[A-Z][A-Z0-9_]*")) {
                    violations.add(errorCodeClass.getSimpleName() + "." + errorCode.getCode());
                }
                if (errorCode.getMessage() == null || errorCode.getMessage().isBlank()) {
                    violations.add(errorCodeClass.getSimpleName() + "." + errorCode.getCode() + "의 메시지가 비어 있습니다.");
                }
            }
        }

        assertTrue(violations.isEmpty(), String.join(System.lineSeparator(), violations));
    }

    @Test
    @DisplayName("도메인 Exception은 공통 DomainException을 상속한다")
    void domainExceptionsExtendSharedException() throws IOException, ClassNotFoundException {
        List<String> violations = new ArrayList<>();

        for (Class<?> exceptionClass : classesInDomainExceptionPackages("Exception.java")) {
            if (!DomainException.class.isAssignableFrom(exceptionClass)) {
                violations.add(exceptionClass.getName());
            }
        }

        assertTrue(
                violations.isEmpty(),
                "DomainException을 상속하지 않은 도메인 예외: " + String.join(", ", violations)
        );
    }

    private List<Class<?>> classesInDomainExceptionPackages(String suffix)
            throws IOException, ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(JAVA_SOURCE_ROOT)) {
            for (Path sourceFile : paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().contains("/domain/exception/"))
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .toList()) {
                classes.add(Class.forName(className(sourceFile)));
            }
        }
        return classes;
    }

    private String className(Path sourceFile) throws IOException {
        Matcher matcher = TYPE_DECLARATION.matcher(Files.readString(sourceFile));
        if (!matcher.find()) {
            throw new IllegalStateException("public type 선언을 찾을 수 없습니다: " + sourceFile);
        }
        String packageName = JAVA_SOURCE_ROOT.relativize(sourceFile.getParent())
                .toString()
                .replace(sourceFile.getFileSystem().getSeparator(), ".");
        return packageName + "." + matcher.group(1);
    }
}
