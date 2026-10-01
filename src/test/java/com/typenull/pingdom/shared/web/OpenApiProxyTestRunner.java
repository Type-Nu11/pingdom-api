package com.typenull.pingdom.shared.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;

/** 별도 프록시 저장소의 검증기를 현재 실행 중인 Spring 서버에 연결한다. */
public final class OpenApiProxyTestRunner {
    private OpenApiProxyTestRunner() {
    }

    public static void run(int backendPort, int expectedStatus) throws Exception {
        String infraRoot = System.getenv("PINGDOM_INFRA_TEST_ROOT");
        Assumptions.assumeTrue(infraRoot != null && !infraRoot.isBlank(),
                "OpenResty 연동 검증은 PINGDOM_INFRA_TEST_ROOT와 Docker가 필요합니다.");
        Path script = Path.of(infraRoot, "tests", "openapi_proxy_test.py");
        assertTrue(Files.isRegularFile(script), script.toString());
        Path log = Files.createTempFile("pingdom-openapi-pair-", ".log");
        Process process = new ProcessBuilder("python3", script.toString(), "--backend-url",
                "http://host.docker.internal:" + backendPort, "--expect-docs-status",
                Integer.toString(expectedStatus))
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(120, TimeUnit.SECONDS), "OpenResty 검증 시간 초과: " + log);
            assertEquals(0, process.exitValue(), Files.readString(log));
        } finally {
            if (process.isAlive()) {
                process.destroy();
                // 검증기의 로그 수집(5초)·컨테이너 정리(10초)에 종료 여유를 준다.
                if (!process.waitFor(20, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            }
        }
    }
}
