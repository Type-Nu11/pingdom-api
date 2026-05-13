package com.typenull.pingdom.global.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Common", description = "앱/웹 공통")
public class HomeController {

    @GetMapping("/")
    public Map<String, Object> home() {
        return Map.of(
                "message", "Pingdom Backend is running.",
                "availableEndpoints", new String[]{"/auth/signup", "/auth/login", "/auth/email/verify", "/auth/token/refresh", "/users/me"},
                "signupFields", new String[]{"username", "name", "email", "password"},
                "loginFields", new String[]{"username", "password"}
        );
    }
}
