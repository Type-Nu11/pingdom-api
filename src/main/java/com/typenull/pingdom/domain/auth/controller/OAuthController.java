package com.typenull.pingdom.domain.auth.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class OAuthController {

    @GetMapping("/auth/google")
    public String googleLogin() {
        return "redirect:/oauth2/authorization/google";
    }
}

