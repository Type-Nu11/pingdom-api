package com.typenull.pingdom.shared.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    @Test
    void resolveUsesContainerResolvedRemoteAddressInsteadOfRawForwardedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.10");
        request.addHeader("X-Forwarded-For", "203.0.113.250, 198.51.100.10");
        request.addHeader("X-Real-IP", "203.0.113.251");

        assertEquals("198.51.100.10", ClientIpResolver.resolve(request));
    }

    @Test
    void resolveReturnsUnknownWhenRequestIsMissing() {
        assertEquals("unknown", ClientIpResolver.resolve(null));
    }
}
