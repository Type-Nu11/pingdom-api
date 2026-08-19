package com.typenull.pingdom.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SensitiveLoggingConventionTest {

    private static final Path JAVA_SOURCE_ROOT = Path.of("src/main/java");
    private static final Pattern SENSITIVE_LOG = Pattern.compile(
            "log\\.(?:trace|debug|info|warn|error)\\s*\\([^;]*,\\s*[^;]*\\b"
                    + "(?:accessToken|refreshToken|authorizationHeader|password)\\b[^;]*\\);",
            Pattern.DOTALL
    );

    @Test
    @DisplayName("Token과 password 원문을 로그에 기록하지 않는다")
    void sensitiveAuthenticationValuesAreNotLogged() throws IOException {
        List<String> violations = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(JAVA_SOURCE_ROOT)) {
            for (Path sourceFile : paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList()) {
                if (SENSITIVE_LOG.matcher(Files.readString(sourceFile)).find()) {
                    violations.add(JAVA_SOURCE_ROOT.relativize(sourceFile).toString());
                }
            }
        }

        assertTrue(
                violations.isEmpty(),
                "민감 인증 정보를 기록하는 로그: " + String.join(", ", violations)
        );
    }
}
