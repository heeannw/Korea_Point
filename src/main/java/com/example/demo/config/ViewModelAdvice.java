package com.example.demo.config;

import com.example.demo.user.util.SecurityUtil;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class ViewModelAdvice {

    @ModelAttribute("isAdmin")
    public boolean isAdmin() {
        return SecurityUtil.isAdmin();
    }

    @ModelAttribute("lang")
    public String lang() {
        String language = LocaleContextHolder.getLocale().getLanguage();
        return switch (language) {
            case "en", "ja", "zh", "fr" -> language;
            default -> "ko";
        };
    }
}
