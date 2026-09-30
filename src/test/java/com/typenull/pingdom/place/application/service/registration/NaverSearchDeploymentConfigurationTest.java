package com.typenull.pingdom.place.application.service.registration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** 네이버 검색 인증 환경변수가 운영 Compose 배포 과정에서 누락되지 않는지 검증합니다. */
class NaverSearchDeploymentConfigurationTest {

    /** GitHub secret이 원격 배포 환경 파일과 Compose 컨테이너 환경으로 연결되는지 검증합니다. */
    @Test
    void passesDeploymentEnvironmentFileToApplicationContainer() throws IOException {
        String workflow = Files.readString(Path.of(".github/workflows/workflow.yml"));
        String compose = Files.readString(Path.of("compose.yaml"));

        assertThat(workflow)
                .contains("ENV_FILE_PATH: ${{ secrets.ENV_FILE_PATH }}")
                .contains("envs: ENV_FILE_PATH")
                .contains("export PINGDOM_ENV_FILE=\"$deployment_env_file\"")
                .contains("docker compose --env-file \"$deployment_env_file\"");
        long environmentFileReferences = compose.lines()
                .filter(line -> line.contains("- ${PINGDOM_ENV_FILE:-.env}"))
                .count();
        assertThat(environmentFileReferences).isEqualTo(2);
    }
}
