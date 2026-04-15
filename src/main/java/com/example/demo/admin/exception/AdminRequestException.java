package com.example.demo.admin.exception;

import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
public class AdminRequestException extends RuntimeException {

    private final Map<String, String> errors;

    public AdminRequestException(String message) {
        this(message, Collections.emptyMap());
    }

    public AdminRequestException(String message, Map<String, String> errors) {
        super(message);
        this.errors = Collections.unmodifiableMap(new LinkedHashMap<>(errors));
    }
}
