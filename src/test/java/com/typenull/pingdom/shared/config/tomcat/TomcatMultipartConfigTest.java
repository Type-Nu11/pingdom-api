package com.typenull.pingdom.shared.config.tomcat;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.catalina.connector.Connector;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;

class TomcatMultipartConfigTest {

    private final TomcatMultipartConfig config = new TomcatMultipartConfig();

    /**
     * Tomcat customizer 적용 후 Connector의 part 수·part 헤더 크기가 설정 상수와 일치하는지 검증한다.
     */
    @Test
    void customizesMultipartLimits() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory(0);
        config.tomcatCustomizer().customize(factory);
        Connector connector = new Connector();

        factory.getTomcatConnectorCustomizers().forEach(customizer -> customizer.customize(connector));

        assertThat(connector.getMaxPartCount()).isEqualTo(TomcatMultipartConfig.MAX_PART_COUNT);
        assertThat(connector.getMaxPartHeaderSize()).isEqualTo(TomcatMultipartConfig.MAX_PART_HEADER_SIZE_BYTES);
    }
}
