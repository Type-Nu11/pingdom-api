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

    /**
     * 테스트 Java 파일의 package 선언이 소스 디렉터리와 일치하는지 확인하고 불일치 경로를 보고한다.
     */
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

    /**
     * architecture·fixture·integration 전용 패키지를 제외한 테스트에는 같은 경로의 운영 패키지 디렉터리가 존재하는지 검증한다.
     */
    @Test
    void requiresMatchingProductionTestPackages() throws IOException {
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

    /**
     * Test.java로 끝나는 파일의 최상위 package-private 클래스 이름이 파일명과 일치하는지 검증한다.
     */
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

    /**
     * 테스트 기본 패키지 아래 모든 일반 Java 파일을 수집하고 탐색 스트림을 정리한다.
     */
    private List<Path> testSourceFiles() throws IOException {
        try (Stream<Path> paths = Files.walk(TEST_BASE_PACKAGE_ROOT)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }
    }

    /**
     * 상대 경로의 첫 디렉터리로 운영 패키지 대응이 필요 없는 테스트 전용 영역을 구분한다.
     */
    private boolean isTestOnlyPackage(Path relativePath) {
        return relativePath.getNameCount() > 1
                && TEST_ONLY_ROOT_PACKAGES.contains(relativePath.getName(0).toString());
    }

    /**
     * 소스 루트와 파일 디렉터리의 상대 경로로 기대 package 이름을 계산한다.
     */
    private String packageFor(Path sourceFile, Path sourceRoot) {
        return sourceRoot.relativize(sourceFile.getParent())
                .toString()
                .replace(sourceFile.getFileSystem().getSeparator(), ".");
    }

    /**
     * 소스의 package 선언을 읽고 선언이 없으면 빈 문자열을 반환해 일치 검사에서 누락을 드러낸다.
     */
    private String declaredPackage(Path sourceFile) throws IOException {
        Matcher matcher = PACKAGE_DECLARATION.matcher(Files.readString(sourceFile));
        return matcher.find() ? matcher.group(1) : "";
    }
}
