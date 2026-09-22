package com.typenull.pingdom.shared.config.tomcat;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TomcatMultipartConfig {

    static final int MAX_PART_COUNT = 10;
    static final int MAX_PART_HEADER_SIZE_BYTES = 512;

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatMultipartCustomizer() {
        return factory -> factory.addConnectorCustomizers(connector -> {
            connector.setMaxPartCount(MAX_PART_COUNT);
            connector.setMaxPartHeaderSize(MAX_PART_HEADER_SIZE_BYTES);
        });
    }
}
