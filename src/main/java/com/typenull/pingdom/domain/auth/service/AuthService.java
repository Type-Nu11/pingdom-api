package com.typenull.pingdom.domain.auth.service;

import com.typenull.pingdom.domain.auth.dto.LoginRequest;
import com.typenull.pingdom.domain.auth.dto.LoginResponse;
import com.typenull.pingdom.domain.auth.dto.SignupRequest;
import com.typenull.pingdom.domain.auth.dto.UserResponse;

public interface AuthService {
    public UserResponse signup(SignupRequest request);

    public LoginResponse login(LoginRequest request);
}
