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
                "availableEndpoints", new String[]{"/api/auth/signup", "/api/auth/login"},
                "signupFields", new String[]{"username", "name", "password"},
                "loginFields", new String[]{"username", "password"}
        );
    }
}
