package com.example.demo.community.controller;

import com.example.demo.community.service.PostImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 글쓰기 화면의 이미지 업로드.
 *
 * <p>{@code /community/**} 이므로 로그인해야 부를 수 있고 CSRF 토큰도 필요하다.
 */
@RestController
@RequestMapping("/community/api")
@RequiredArgsConstructor
public class PostImageApiController {

    private final PostImageStorage storage;

    @PostMapping("/image")
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(Map.of("url", storage.store(file)));
        } catch (IllegalArgumentException ex) {
            // 올린 쪽이 고칠 수 있는 문제(형식·용량)는 그대로 알려 준다.
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }
}
