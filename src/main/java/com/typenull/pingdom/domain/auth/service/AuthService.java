package com.typenull.pingdom.domain.auth.service;

import com.typenull.pingdom.domain.auth.dto.login.LoginRequest;
import com.typenull.pingdom.domain.auth.dto.login.LoginResponse;
import com.typenull.pingdom.domain.auth.dto.signup.SignupRequest;
import com.typenull.pingdom.domain.auth.dto.signup.UserResponse;

public interface AuthService {
    public UserResponse signup(SignupRequest request);

    public LoginResponse login(LoginRequest request);
}
