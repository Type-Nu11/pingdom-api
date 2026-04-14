package com.typenull.pingdom.global.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping("/")
    public Map<String, Object> home() {
        return Map.of(
                "message", "Pingdom Backend is running.",
                "availableEndpoints", new String[]{"/auth/signup", "/auth/login", "/auth/email/verify", "/auth/token/refresh"},
                "signupFields", new String[]{"username", "name", "email", "password"},
                "loginFields", new String[]{"username", "password"}
        );
    }
}
