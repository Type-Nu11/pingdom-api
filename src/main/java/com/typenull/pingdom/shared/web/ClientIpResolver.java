package com.typenull.pingdom.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

public final class ClientIpResolver {

    private static final String UNKNOWN_IP = "unknown";

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN_IP;
        }

        // Tomcat RemoteIpValve가 신뢰된 프록시의 forwarded header만 반영한 뒤 전달하는 주소를 사용한다.
        String remoteAddr = request.getRemoteAddr();
        return StringUtils.hasText(remoteAddr) ? remoteAddr.trim() : UNKNOWN_IP;
    }
}
