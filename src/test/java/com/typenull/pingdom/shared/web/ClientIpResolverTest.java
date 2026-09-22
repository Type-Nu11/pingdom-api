package com.typenull.pingdom.shared.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    /**
     * 원시 Forwarded/X-Real-IP 헤더가 다른 값을 담아도 컨테이너가 해석한 remoteAddr만 클라이언트 IP로 사용하는지 검증한다.
     */
    @Test
    void usesContainerResolvedClientIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.10");
        request.addHeader("X-Forwarded-For", "203.0.113.250, 198.51.100.10");
        request.addHeader("X-Real-IP", "203.0.113.251");

        assertEquals("198.51.100.10", ClientIpResolver.resolve(request));
    }

    /**
     * 요청 객체가 null이면 unknown을 반환하는지 검증한다.
     */
    @Test
    void missingRequestReturnsUnknown() {
        assertEquals("unknown", ClientIpResolver.resolve(null));
    }
}
