package com.example.demo.admin.dto;

import com.example.demo.admin.entity.AdminActionType;
import com.example.demo.admin.entity.AdminReasonCode;
import com.example.demo.admin.entity.AdminTargetType;
import com.example.demo.community.entity.ContentReportReason;
import com.example.demo.community.entity.ContentReportStatus;
import com.example.demo.community.entity.ReportTargetType;
import com.example.demo.user.entity.AccountStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

public class AdminDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PageResponse<T> {
        private List<T> items;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DashboardSummary {
        private long totalUsers;
        private long adminUsers;
        private long suspendedUsers;
        private long activePosts;
        private long activeComments;
        private long openReports;
        private long recent7dHideActions;
        private long recent7dRestoreActions;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DashboardResponse {
        private DashboardSummary summary;
        private List<UserItem> recentUsers;
        private List<PostItem> recentPosts;
        private List<CommentItem> recentComments;
        private List<ReportItem> recentReports;
        private List<AdminLogItem> recentLogs;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserItem {
        private Long id;
        private String name;
        private String email;
        private String provider;
        private String role;
        private AccountStatus accountStatus;
        private int point;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserActionResponse {
        private Long id;
        private String role;
        private AccountStatus accountStatus;
        private int point;
        private Long latestLogId;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class AdminActionRequest {
        @NotNull(message = "사유 코드를 선택해 주세요.")
        private AdminReasonCode reasonCode;

        private String reasonDetail;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class RoleUpdateRequest extends AdminActionRequest {
        @NotBlank(message = "변경할 역할을 선택해 주세요.")
        private String role;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class PointAdjustRequest extends AdminActionRequest {
        @NotNull(message = "포인트 변경값을 입력해 주세요.")
        private Integer delta;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class AccountStatusUpdateRequest extends AdminActionRequest {
        @NotNull(message = "계정 상태를 선택해 주세요.")
        private AccountStatus accountStatus;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class PostActionRequest extends AdminActionRequest {
    }

    /** 홍보 글을 광고 자리에서 내린다. 글 자체는 목록에 남는다. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class PromoTakedownRequest extends AdminActionRequest {
        /**
         * 500P 를 돌려줄지.
         *
         * <p>규칙 위반이면 환불하지 않고, 우리 쪽 문제(중복 노출·잘못된 심사 등)면 환불한다.
         * 판단이 갈리는 자리라 자동으로 정하지 않고 관리자가 고른다.
         */
        private boolean refund;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CommentActionRequest extends AdminActionRequest {
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PostItem {
        private Long id;
        private Long categoryId;
        private String categoryName;
        private Long authorId;
        private String authorName;
        private String title;
        private int viewCount;
        private int commentCount;
        private LocalDateTime createdAt;
        private LocalDateTime deletedAt;
        private boolean purgeEligible;
        /** 홍보 글이면서 아직 노출 기간이 남았는지. 내리기 버튼을 띄울지 판단한다. */
        private boolean promoActive;
        private LocalDateTime promoUntil;
        /** 내릴 때 돌려줄 수 있는 금액. 화면이 숫자를 지어내지 않도록 서버가 알려 준다. */
        private int promoRefundAmount;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PostActionResponse {
        private Long id;
        private String status;
        private LocalDateTime deletedAt;
        private boolean purgeEligible;
        private Long latestLogId;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CommentItem {
        private Long id;
        private Long postId;
        private String postTitle;
        private Long authorId;
        private String authorName;
        private String contentPreview;
        private Long parentId;
        private Long replyToId;
        private LocalDateTime createdAt;
        private LocalDateTime deletedAt;
        private boolean purgeEligible;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CommentActionResponse {
        private Long id;
        private String status;
        private LocalDateTime deletedAt;
        private boolean purgeEligible;
        private Long latestLogId;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PostCategoryOption {
        private Long id;
        private String name;
        private String emoji;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReportItem {
        private Long id;
        private Long reporterUserId;
        private String reporterUserName;
        private ReportTargetType targetType;
        private Long targetId;
        private Long postId;        // COMMENT 신고일 때 댓글이 속한 게시글 ID
        private String targetSummary;
        private ContentReportReason reasonCode;
        private String reasonDetail;
        private ContentReportStatus status;
        private String reviewNote;
        private Long reviewedByAdminId;
        private String reviewedByAdminName;
        private LocalDateTime reviewedAt;
        private LocalDateTime createdAt;
        private long openCountForTarget;
        /**
         * 신고 대상(글·댓글)의 작성자와 현재 상태.
         * 신고 목록에서 화면을 옮기지 않고 바로 조치하려면 이 값들이 필요하다.
         */
        private Long targetAuthorId;
        private String targetAuthorName;
        private boolean targetAuthorSuspended;
        /** 대상이 이미 숨겨져 있는지. 버튼을 숨김/복구 중 무엇으로 보일지 정한다. */
        private boolean targetHidden;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ReportStatusUpdateRequest extends AdminActionRequest {
        @NotNull(message = "신고 처리 상태를 선택해 주세요.")
        private ContentReportStatus status;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReportActionResponse {
        private Long id;
        private ContentReportStatus status;
        private String reviewNote;
        private LocalDateTime reviewedAt;
        private Long latestLogId;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AdminLogItem {
        private Long id;
        private Long adminUserId;
        private String adminUserName;
        private AdminTargetType targetType;
        private Long targetId;
        private AdminActionType actionType;
        private AdminReasonCode reasonCode;
        private String reasonDetail;
        private String targetSummary;
        private String beforeSnapshotJson;
        private String afterSnapshotJson;
        private LocalDateTime createdAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AdminLogPageResponse {
        private List<AdminLogItem> items;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }
}
