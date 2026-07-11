package com.typenull.pingdom.shared.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

class ForwardedHeadersConfigurationTest {

    @Test
    void productionConfigurationUsesTrustedNativeForwardedHeaders() throws IOException {
        PropertySource<?> properties = new YamlPropertySourceLoader()
                .load("application", new FileSystemResource("src/main/resources/application.yaml"))
                .getFirst();

        assertEquals("native", properties.getProperty("server.forward-headers-strategy"));
        assertEquals("X-Forwarded-For", properties.getProperty("server.tomcat.remoteip.remote-ip-header"));
        assertEquals("X-Forwarded-Proto", properties.getProperty("server.tomcat.remoteip.protocol-header"));
        assertTrue(properties.getProperty("server.tomcat.remoteip.internal-proxies")
                .toString()
                .contains("TRUSTED_PROXY_IPS_REGEX"));
    }
}
