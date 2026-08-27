package com.example.demo.chatbot.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatAskRequest(@NotBlank String question) {
}
