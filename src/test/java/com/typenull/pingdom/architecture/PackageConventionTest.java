package com.typenull.pingdom.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 운영 코드의 패키지 계층 규칙이 소스 경로와 일치하는지 검증합니다. */
class PackageConventionTest {

    private static final Path JAVA_SOURCE_ROOT = Path.of("src/main/java");
    private static final Path BASE_PACKAGE_ROOT = JAVA_SOURCE_ROOT.resolve("com/typenull/pingdom");
    private static final Pattern PACKAGE_DECLARATION = Pattern.compile("(?m)^package\\s+([\\w.]+);");
    private static final Set<String> MODULE_LAYERS = Set.of(
            "api",
            "application",
            "domain",
            "infrastructure",
            "event",
            "outbox",
            "support"
    );

    @Test
    @DisplayName("Java package 선언은 소스 디렉터리 경로와 일치한다")
    void packageDeclarationMatchesSourceDirectory() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path sourceFile : javaSourceFiles()) {
            String expectedPackage = JAVA_SOURCE_ROOT
                    .relativize(sourceFile.getParent())
                    .toString()
                    .replace(sourceFile.getFileSystem().getSeparator(), ".");
            String source = Files.readString(sourceFile);
            Matcher matcher = PACKAGE_DECLARATION.matcher(source);

            if (!matcher.find()) {
                violations.add(sourceFile + "에 package 선언이 없습니다.");
                continue;
            }

            String actualPackage = matcher.group(1);
            if (!actualPackage.equals(expectedPackage)) {
                violations.add(sourceFile + ": expected=" + expectedPackage + ", actual=" + actualPackage);
            }
        }

        assertTrue(violations.isEmpty(), String.join(System.lineSeparator(), violations));
    }

    @Test
    @DisplayName("도메인 모듈은 표준 최상위 계층만 사용한다")
    void domainModulesUseStandardTopLevelLayers() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path sourceFile : javaSourceFiles()) {
            Path relativePath = BASE_PACKAGE_ROOT.relativize(sourceFile);
            if (relativePath.getNameCount() < 3) {
                continue;
            }

            String module = relativePath.getName(0).toString();
            if (module.equals("shared")) {
                continue;
            }

            String topLevelLayer = relativePath.getName(1).toString();
            if (!MODULE_LAYERS.contains(topLevelLayer)) {
                violations.add(sourceFile + ": 허용되지 않은 최상위 계층 " + topLevelLayer);
            }
        }

        assertTrue(violations.isEmpty(), String.join(System.lineSeparator(), violations));
    }

    private List<Path> javaSourceFiles() throws IOException {
        try (Stream<Path> paths = Files.walk(BASE_PACKAGE_ROOT)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }
    }
}
