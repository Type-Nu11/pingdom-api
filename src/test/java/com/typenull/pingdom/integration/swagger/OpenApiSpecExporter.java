package com.typenull.pingdom.integration.swagger;

import com.typenull.pingdom.PingdomApplication;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * export 전용 프로필의 로컬 서버에서 생성 문서를 가져와 파일로 저장하는 실행 진입점.
 */
public class OpenApiSpecExporter {

    private static final String DEFAULT_OUTPUT_DIR = "build/openapi";
    private static final String STABLE_SERVER_URL = "http://127.0.0.1:64060";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * 임의 포트로 export 프로필 서버를 시작해 여섯 문서를 UTF-8 파일로 저장. 포트 문자열을 고정 URL로 치환하며 중간 실패 시 이미 쓴 파일은 남을 수 있음.
     */
    public static void main(String[] args) throws Exception {
        ExportOptions options = ExportOptions.from(args);

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(PingdomApplication.class)
                .profiles("openapi-export")
                .properties(
                        "server.port=0",
                        "spring.main.banner-mode=off"
                )
                .run(args)) {
            Integer actualPort = context.getEnvironment().getProperty("local.server.port", Integer.class);
            if (actualPort == null) {
                throw new IllegalStateException("OpenAPI export failed because the embedded server port was not assigned.");
            }

            Files.createDirectories(options.outputDirectory());

            Map<String, String> specs = Map.of(
                    "openapi.json", "/v3/api-docs",
                    "app.json", "/v3/api-docs/app",
                    "common.json", "/v3/api-docs/common",
                    "consulting.json", "/v3/api-docs/consulting",
                    "admin.json", "/v3/api-docs/admin",
                    "merchant.json", "/v3/api-docs/merchant"
            );

            for (Map.Entry<String, String> spec : specs.entrySet()) {
                String responseBody = fetchSpec(actualPort, spec.getValue())
                        .replace("http://127.0.0.1:" + actualPort, STABLE_SERVER_URL);
                Files.writeString(
                        options.outputDirectory().resolve(spec.getKey()),
                        responseBody,
                        StandardCharsets.UTF_8
                );
            }
        }
    }

    /**
     * 로컬 서버에서 최대 10초 요청 제한으로 문서를 읽고 200이 아니면 경로·상태를 담은 예외로 실패. 자동 재시도는 없음.
     */
    private static String fetchSpec(int port, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("OpenAPI export failed for " + path + " with status " + response.statusCode());
        }

        return response.body();
    }

    private record ExportOptions(Path outputDirectory) {

        /**
         * 출력 경로 기본값은 build/openapi이며 같은 옵션이 여러 번 있으면 마지막 값을 사용.
         */
        private static ExportOptions from(String[] args) {
            String outputDirectoryValue = DEFAULT_OUTPUT_DIR;

            for (String arg : args) {
                if (arg.startsWith("--openapi.output-dir=")) {
                    outputDirectoryValue = arg.substring("--openapi.output-dir=".length());
                }
            }

            return new ExportOptions(Path.of(outputDirectoryValue));
        }
    }
}
