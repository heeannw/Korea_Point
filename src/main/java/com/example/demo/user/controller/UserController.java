package com.example.demo.user.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UserController {

    // Spring Security OAuth2의 loginPage("/login")이 이 URL을 바라봄
    @GetMapping("/login")
    public String loginPage() {
        return "user/login";
    }
}
