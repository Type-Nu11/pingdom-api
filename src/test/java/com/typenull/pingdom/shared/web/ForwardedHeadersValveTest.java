package com.typenull.pingdom.shared.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.RemoteIpValve;
import org.apache.catalina.valves.ValveBase;
import org.junit.jupiter.api.Test;

/** 실제 Tomcat valve로 신뢰 경계와 헤더 해석을 확인하며 Spring 컨텍스트는 실행하지 않는다. */
class ForwardedHeadersValveTest {

    @Test
    void ignoresSpoofedHeadersFromUntrustedPeer() throws Exception {
        Request request = requestFrom("198.51.100.10");
        RemoteIpValve valve = valve();
        valve.setNext(new ValveBase() {
            @Override
            public void invoke(Request current, Response response) {
                assertEquals("198.51.100.10", current.getRemoteAddr());
                assertEquals("internal.example", current.getServerName());
                assertEquals(8080, current.getServerPort());
                assertEquals("http", current.getScheme());
                assertFalse(current.isSecure());
            }
        });

        valve.invoke(request, new Response());
    }

    @Test
    void resolvesPublicUrlOnlyForTrustedPeer() throws Exception {
        Request request = requestFrom("127.0.0.1");
        RemoteIpValve valve = valve();
        valve.setNext(new ValveBase() {
            @Override
            public void invoke(Request current, Response response) {
                assertEquals("203.0.113.250", current.getRemoteAddr());
                assertEquals("www.typenull.xyz", current.getServerName());
                assertEquals(443, current.getServerPort());
                assertEquals("https", current.getScheme());
                assertTrue(current.isSecure());
            }
        });

        valve.invoke(request, new Response());
    }

    private RemoteIpValve valve() {
        RemoteIpValve valve = new RemoteIpValve();
        valve.setInternalProxies("127[.]0[.]0[.]1|::1");
        valve.setRemoteIpHeader("X-Forwarded-For");
        valve.setProtocolHeader("X-Forwarded-Proto");
        valve.setHostHeader("X-Forwarded-Host");
        valve.setPortHeader("X-Forwarded-Port");
        return valve;
    }

    private Request requestFrom(String peerAddress) {
        Request request = new Request(new Connector());
        org.apache.coyote.Request coyoteRequest = new org.apache.coyote.Request();
        request.setCoyoteRequest(coyoteRequest);
        request.setRemoteAddr(peerAddress);
        request.setRemoteHost(peerAddress);
        coyoteRequest.scheme().setString("http");
        coyoteRequest.serverName().setString("internal.example");
        coyoteRequest.setServerPort(8080);
        coyoteRequest.getMimeHeaders().setValue("X-Forwarded-For").setString("203.0.113.250");
        coyoteRequest.getMimeHeaders().setValue("X-Forwarded-Proto").setString("https");
        coyoteRequest.getMimeHeaders().setValue("X-Forwarded-Host").setString("www.typenull.xyz");
        coyoteRequest.getMimeHeaders().setValue("X-Forwarded-Port").setString("443");
        return request;
    }
}
