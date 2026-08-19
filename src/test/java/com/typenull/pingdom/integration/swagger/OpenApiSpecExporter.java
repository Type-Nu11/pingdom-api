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

public class OpenApiSpecExporter {

    private static final String DEFAULT_OUTPUT_DIR = "build/openapi";
    private static final String STABLE_SERVER_URL = "http://127.0.0.1:64060";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

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
                    "web.json", "/v3/api-docs/web"
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
