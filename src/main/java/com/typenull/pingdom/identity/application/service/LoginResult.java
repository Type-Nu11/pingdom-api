package com.typenull.pingdom.identity.application.service;

import com.typenull.pingdom.identity.api.dto.login.LoginResponse;

public record LoginResult(
        LoginResponse response,
        String refreshToken
) {
}
