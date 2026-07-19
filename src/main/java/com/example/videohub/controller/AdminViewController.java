package com.example.videohub.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the two admin HTML pages (Thymeleaf templates under /templates).
 * Access to /admin is locked down in {@link com.example.videohub.config.SecurityConfig};
 * /admin/login is the only admin page a logged-out visitor can reach.
 */
@Controller
public class AdminViewController {

    @GetMapping("/admin")
    public String dashboard() {
        return "admin"; // -> templates/admin.html
    }

    @GetMapping("/admin/login")
    public String login() {
        return "login"; // -> templates/login.html
    }
}
