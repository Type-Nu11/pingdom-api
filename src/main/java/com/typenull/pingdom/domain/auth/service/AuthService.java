package com.typenull.pingdom.domain.auth.service;

import com.typenull.pingdom.domain.auth.dto.request.LoginRequest;
import com.typenull.pingdom.domain.auth.dto.response.LoginResponse;
import com.typenull.pingdom.domain.auth.dto.request.SignupRequest;
import com.typenull.pingdom.domain.auth.dto.response.UserResponse;

public interface AuthService {
    public UserResponse signup(SignupRequest request);

    public LoginResponse login(LoginRequest request);
}
