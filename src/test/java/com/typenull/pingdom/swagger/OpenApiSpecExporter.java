package com.typenull.pingdom.swagger;

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

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getProperty("openapi.port", "9090"));
        Path outputDirectory = Path.of(System.getProperty("openapi.outputDir", "build/openapi"));

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(PingdomApplication.class)
                .profiles("openapi-export")
                .properties(
                        "server.port=" + port,
                        "spring.main.banner-mode=off"
                )
                .run(args)) {

            Files.createDirectories(outputDirectory);

            Map<String, String> specs = Map.of(
                    "openapi.json", "/v3/api-docs",
                    "app.json", "/v3/api-docs/app",
                    "common.json", "/v3/api-docs/common",
                    "web.json", "/v3/api-docs/web"
            );

            for (Map.Entry<String, String> spec : specs.entrySet()) {
                String responseBody = fetchSpec(port, spec.getValue());
                Files.writeString(
                        outputDirectory.resolve(spec.getKey()),
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
}
