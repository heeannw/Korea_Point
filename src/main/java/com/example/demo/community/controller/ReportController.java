package com.example.demo.community.controller;

import com.example.demo.config.LocalizedMessages;
import com.example.demo.community.dto.ContentReportDto;
import com.example.demo.community.service.ContentReportService;
import com.example.demo.user.util.SecurityUtil;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ContentReportService contentReportService;
    private final LocalizedMessages messages;

    @PostMapping("/posts/{postId}")
    public ContentReportDto.Response reportPost(@PathVariable Long postId,
                                                @Valid @RequestBody ContentReportDto.CreateRequest request) {
        return contentReportService.reportPost(SecurityUtil.getCurrentUserId(), postId, request);
    }

    @PostMapping("/comments/{commentId}")
    public ContentReportDto.Response reportComment(@PathVariable Long commentId,
                                                   @Valid @RequestBody ContentReportDto.CreateRequest request) {
        return contentReportService.reportComment(SecurityUtil.getCurrentUserId(), commentId, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(Map.of(
                "message", messages.get("community.error.invalidInput"),
                "errors", errors
        ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException exception) {
        String message = messages.get("community.error.invalidInput");
        if (exception.getCause() instanceof InvalidFormatException) {
            message = messages.get("community.error.unsupportedReason");
        }
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(RuntimeException exception) {
        // 서비스 오류는 문구 키를 갖고 있어 화면 언어로 바꿔 보낸다(LocalizedMessages).
        return ResponseEntity.badRequest().body(Map.of("message", messages.of(exception)));
    }
}
