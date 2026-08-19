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
import org.junit.jupiter.api.Test;

class TestSourceConventionTest {

    private static final Path TEST_SOURCE_ROOT = Path.of("src/test/java");
    private static final Path MAIN_SOURCE_ROOT = Path.of("src/main/java");
    private static final Path TEST_BASE_PACKAGE_ROOT = TEST_SOURCE_ROOT.resolve("com/typenull/pingdom");
    private static final Set<String> TEST_ONLY_ROOT_PACKAGES = Set.of("architecture", "fixture", "integration");
    private static final Pattern PACKAGE_DECLARATION = Pattern.compile("(?m)^package\\s+([\\w.]+);");
    private static final Pattern TOP_LEVEL_CLASS = Pattern.compile("(?m)^class\\s+(\\w+)");

    @Test
    void testPackageDeclarationMatchesDirectory() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path sourceFile : testSourceFiles()) {
            String expectedPackage = packageFor(sourceFile, TEST_SOURCE_ROOT);
            String actualPackage = declaredPackage(sourceFile);
            if (!expectedPackage.equals(actualPackage)) {
                violations.add(sourceFile + ": expected=" + expectedPackage + ", actual=" + actualPackage);
            }
        }

        assertTrue(violations.isEmpty(), String.join(System.lineSeparator(), violations));
    }

    @Test
    void productionAlignedTestsUseProductionPackageStructure() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path sourceFile : testSourceFiles()) {
            Path relativePath = TEST_BASE_PACKAGE_ROOT.relativize(sourceFile);
            if (isTestOnlyPackage(relativePath)) {
                continue;
            }

            Path productionPackage = MAIN_SOURCE_ROOT.resolve(TEST_SOURCE_ROOT.relativize(sourceFile.getParent()));
            if (!Files.isDirectory(productionPackage)) {
                violations.add(sourceFile + ": 운영 코드 패키지가 없습니다: " + productionPackage);
            }
        }

        assertTrue(violations.isEmpty(), String.join(System.lineSeparator(), violations));
    }

    @Test
    void testClassNameMatchesFileName() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path sourceFile : testSourceFiles()) {
            String fileName = sourceFile.getFileName().toString();
            if (!fileName.endsWith("Test.java")) {
                continue;
            }

            Matcher matcher = TOP_LEVEL_CLASS.matcher(Files.readString(sourceFile));
            if (!matcher.find() || !fileName.equals(matcher.group(1) + ".java")) {
                violations.add(sourceFile + ": 테스트 클래스명은 파일명과 일치해야 합니다.");
            }
        }

        assertTrue(violations.isEmpty(), String.join(System.lineSeparator(), violations));
    }

    private List<Path> testSourceFiles() throws IOException {
        try (Stream<Path> paths = Files.walk(TEST_BASE_PACKAGE_ROOT)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }
    }

    private boolean isTestOnlyPackage(Path relativePath) {
        return relativePath.getNameCount() > 1
                && TEST_ONLY_ROOT_PACKAGES.contains(relativePath.getName(0).toString());
    }

    private String packageFor(Path sourceFile, Path sourceRoot) {
        return sourceRoot.relativize(sourceFile.getParent())
                .toString()
                .replace(sourceFile.getFileSystem().getSeparator(), ".");
    }

    private String declaredPackage(Path sourceFile) throws IOException {
        Matcher matcher = PACKAGE_DECLARATION.matcher(Files.readString(sourceFile));
        return matcher.find() ? matcher.group(1) : "";
    }
}
