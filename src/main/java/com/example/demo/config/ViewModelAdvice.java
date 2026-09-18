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

    /** 모든 화면의 기본 언어 값. 컨트롤러가 따로 넣지 않아도 헤더가 현재 언어를 안다. */
    @ModelAttribute("lang")
    public String lang() {
        return CurrentLanguage.get();
    }
}
