package com.example.videohub.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the public viewer auth pages (Thymeleaf templates under /templates).
 * These are separate from the admin login at /admin/login.
 */
@Controller
public class AuthViewController {

    @GetMapping("/signup")
    public String signup() {
        return "signup"; // -> templates/signup.html
    }

    @GetMapping("/login")
    public String login() {
        return "signin"; // -> templates/signin.html
    }
}
