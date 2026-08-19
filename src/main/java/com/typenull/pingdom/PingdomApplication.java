package com.typenull.pingdom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableJpaAuditing
@EnableScheduling
/** Spring Boot 애플리케이션의 구성 검색과 실행을 시작하는 진입점입니다. */
public class PingdomApplication {

    public static void main(String[] args) {
        SpringApplication.run(PingdomApplication.class, args);
    }

}
