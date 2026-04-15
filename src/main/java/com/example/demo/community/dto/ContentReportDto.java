package com.example.demo.community.dto;

import com.example.demo.community.entity.ContentReportReason;
import com.example.demo.community.entity.ContentReportStatus;
import com.example.demo.community.entity.ReportTargetType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

public class ContentReportDto {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CreateRequest {
        @NotNull(message = "신고 사유를 선택해 주세요.")
        private ContentReportReason reasonCode;

        private String reasonDetail;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private ReportTargetType targetType;
        private Long targetId;
        private ContentReportStatus status;
        private LocalDateTime createdAt;
    }
}
