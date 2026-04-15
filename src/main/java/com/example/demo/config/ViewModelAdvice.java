package com.example.demo.config;

import com.example.demo.user.util.SecurityUtil;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class ViewModelAdvice {

    @ModelAttribute("isAdmin")
    public boolean isAdmin() {
        return SecurityUtil.isAdmin();
    }
}
