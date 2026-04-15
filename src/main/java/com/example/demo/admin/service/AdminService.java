package com.example.demo.admin.service;

import com.example.demo.admin.dto.AdminDto;
import com.example.demo.admin.entity.AdminActionLog;
import com.example.demo.admin.entity.AdminActionType;
import com.example.demo.admin.entity.AdminReasonCode;
import com.example.demo.admin.entity.AdminTargetType;
import com.example.demo.admin.exception.AdminRequestException;
import com.example.demo.admin.repository.AdminActionLogRepository;
import com.example.demo.community.entity.Category;
import com.example.demo.community.entity.Comment;
import com.example.demo.community.entity.ContentReport;
import com.example.demo.community.entity.ContentReportReason;
import com.example.demo.community.entity.ContentReportStatus;
import com.example.demo.community.entity.Post;
import com.example.demo.community.entity.ReportTargetType;
import com.example.demo.community.repository.CategoryRepository;
import com.example.demo.community.repository.CommentRepository;
import com.example.demo.community.repository.ContentReportRepository;
import com.example.demo.community.repository.PostRepository;
import com.example.demo.user.entity.AccountStatus;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final long PURGE_RETENTION_DAYS = 30L;
    private static final List<ContentReportStatus> OPEN_REPORT_STATUSES = List.of(
            ContentReportStatus.OPEN,
            ContentReportStatus.REVIEWING
    );

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final CategoryRepository categoryRepository;
    private final ContentReportRepository contentReportRepository;
    private final AdminActionLogRepository adminActionLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public AdminDto.DashboardResponse getDashboard() {
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);

        return AdminDto.DashboardResponse.builder()
                .summary(AdminDto.DashboardSummary.builder()
                        .totalUsers(userRepository.count())
                        .adminUsers(userRepository.countByRole("ADMIN"))
                        .suspendedUsers(userRepository.countByAccountStatus(AccountStatus.SUSPENDED))
                        .activePosts(postRepository.countByDeletedAtIsNull())
                        .activeComments(commentRepository.countByDeletedAtIsNull())
                        .openReports(contentReportRepository.countByStatusIn(OPEN_REPORT_STATUSES))
                        .recent7dHideActions(adminActionLogRepository.countByActionTypeInAndCreatedAtGreaterThanEqual(
                                List.of(AdminActionType.POST_HIDE, AdminActionType.COMMENT_HIDE), sevenDaysAgo))
                        .recent7dRestoreActions(adminActionLogRepository.countByActionTypeInAndCreatedAtGreaterThanEqual(
                                List.of(AdminActionType.POST_RESTORE, AdminActionType.COMMENT_RESTORE), sevenDaysAgo))
                        .build())
                .recentUsers(userRepository.findTop5ByOrderByCreatedAtDesc().stream().map(this::toUserItem).toList())
                .recentPosts(postRepository.findTop5ByDeletedAtIsNullOrderByCreatedAtDesc().stream().map(this::toPostItem).toList())
                .recentComments(commentRepository.findTop5ByDeletedAtIsNullOrderByCreatedAtDesc().stream().map(this::toCommentItem).toList())
                .recentReports(toReportItems(contentReportRepository.findTop5ByOrderByCreatedAtDesc()))
                .recentLogs(toAdminLogItems(adminActionLogRepository.findTop5ByOrderByCreatedAtDesc()))
                .build();
    }

    @Transactional(readOnly = true)
    public AdminDto.PageResponse<AdminDto.UserItem> getUsers(int page, int size, String keyword, String role,
                                                             String provider, AccountStatus accountStatus,
                                                             LocalDate dateFrom, LocalDate dateTo) {
        Page<User> result = userRepository.searchForAdmin(
                normalizeKeyword(keyword),
                normalizeRoleFilter(role),
                normalizeProvider(provider),
                accountStatus,
                toDateFrom(dateFrom),
                toDateTo(dateTo),
                pageRequest(page, size, "createdAt")
        );
        return toPageResponse(result.map(this::toUserItem));
    }

    @Transactional
    public AdminDto.UserActionResponse updateUserRole(Long adminId, Long targetUserId, AdminDto.RoleUpdateRequest request) {
        String nextRole = normalizeRoleValue(request.getRole());
        String reasonDetail = validateActionRequest(request);
        User target = getUser(targetUserId);

        if (!"ADMIN".equals(nextRole) && Objects.equals(adminId, targetUserId)) {
            throw new IllegalStateException("본인 계정의 ADMIN 권한은 해제할 수 없습니다.");
        }
        if ("ADMIN".equals(target.getRole()) && !"ADMIN".equals(nextRole) && userRepository.countByRole("ADMIN") <= 1) {
            throw new IllegalStateException("마지막 ADMIN 계정은 USER로 변경할 수 없습니다.");
        }

        Map<String, Object> beforeSnapshot = snapshotUser(target);
        target.setRole(nextRole);
        AdminActionLog actionLog = saveActionLog(adminId, AdminTargetType.USER, target.getId(), AdminActionType.USER_ROLE_CHANGE,
                request.getReasonCode(), reasonDetail, beforeSnapshot, snapshotUser(target));

        return toUserActionResponse(target, actionLog.getId());
    }

    @Transactional
    public AdminDto.UserActionResponse adjustUserPoints(Long adminId, Long targetUserId, AdminDto.PointAdjustRequest request) {
        String reasonDetail = validateActionRequest(request);
        Integer delta = request.getDelta();
        if (delta == null || delta == 0) {
            throw new AdminRequestException("입력값을 확인해 주세요.", Map.of("delta", "포인트 변경 값은 0이 아니어야 합니다."));
        }

        User target = getUser(targetUserId);
        int nextPoint = target.getPoint() + delta;
        if (nextPoint < 0) {
            throw new AdminRequestException("입력값을 확인해 주세요.", Map.of("delta", "변경 후 포인트는 0보다 작을 수 없습니다."));
        }

        Map<String, Object> beforeSnapshot = snapshotUser(target);
        target.setPoint(nextPoint);
        Map<String, Object> afterSnapshot = snapshotUser(target);
        afterSnapshot.put("delta", delta);
        AdminActionLog actionLog = saveActionLog(adminId, AdminTargetType.USER, target.getId(), AdminActionType.USER_POINT_ADJUST,
                request.getReasonCode(), reasonDetail, beforeSnapshot, afterSnapshot);

        return toUserActionResponse(target, actionLog.getId());
    }

    @Transactional
    public AdminDto.UserActionResponse updateUserAccountStatus(Long adminId, Long targetUserId,
                                                               AdminDto.AccountStatusUpdateRequest request) {
        String reasonDetail = validateActionRequest(request);
        User target = getUser(targetUserId);

        Map<String, Object> beforeSnapshot = snapshotUser(target);
        target.setAccountStatus(request.getAccountStatus());
        AdminActionType actionType = request.getAccountStatus() == AccountStatus.SUSPENDED
                ? AdminActionType.USER_SUSPEND
                : AdminActionType.USER_ACTIVATE;
        AdminActionLog actionLog = saveActionLog(adminId, AdminTargetType.USER, target.getId(), actionType,
                request.getReasonCode(), reasonDetail, beforeSnapshot, snapshotUser(target));

        return toUserActionResponse(target, actionLog.getId());
    }

    @Transactional(readOnly = true)
    public AdminDto.PageResponse<AdminDto.PostItem> getPosts(int page, int size, String keyword, Long categoryId,
                                                             Long authorId, String status, Boolean purgeReady,
                                                             LocalDate dateFrom, LocalDate dateTo) {
        Page<Post> result = postRepository.searchForAdmin(
                normalizeKeyword(keyword),
                categoryId,
                authorId,
                normalizeStatus(status),
                toDateFrom(dateFrom),
                toDateTo(dateTo),
                Boolean.TRUE.equals(purgeReady),
                purgeThreshold(),
                pageRequest(page, size, "createdAt")
        );
        return toPageResponse(result.map(this::toPostItem));
    }

    @Transactional
    public AdminDto.PostActionResponse hidePost(Long adminId, Long postId, AdminDto.PostActionRequest request) {
        String reasonDetail = validateActionRequest(request);
        Post post = getAdminPost(postId);
        Map<String, Object> beforeSnapshot = snapshotPost(post);
        if (post.getDeletedAt() == null) {
            post.setDeletedAt(LocalDateTime.now());
        }
        AdminActionLog actionLog = saveActionLog(adminId, AdminTargetType.POST, post.getId(), AdminActionType.POST_HIDE,
                request.getReasonCode(), reasonDetail, beforeSnapshot, snapshotPost(post));
        return toPostActionResponse(post, actionLog.getId());
    }

    @Transactional
    public AdminDto.PostActionResponse restorePost(Long adminId, Long postId, AdminDto.PostActionRequest request) {
        String reasonDetail = validateActionRequest(request);
        Post post = getAdminPost(postId);
        Map<String, Object> beforeSnapshot = snapshotPost(post);
        if (post.getDeletedAt() != null) {
            post.setDeletedAt(null);
        }
        AdminActionLog actionLog = saveActionLog(adminId, AdminTargetType.POST, post.getId(), AdminActionType.POST_RESTORE,
                request.getReasonCode(), reasonDetail, beforeSnapshot, snapshotPost(post));
        return toPostActionResponse(post, actionLog.getId());
    }

    @Transactional
    public AdminDto.PostActionResponse purgePost(Long adminId, Long postId, AdminDto.PostActionRequest request) {
        String reasonDetail = validateActionRequest(request);
        Post post = getAdminPost(postId);
        ensurePurgeAllowed(post.getDeletedAt(), "게시글");

        Map<String, Object> beforeSnapshot = snapshotPost(post);
        List<Comment> comments = sortCommentsForDelete(commentRepository.findAllByPostId(postId));
        List<Long> commentIds = comments.stream().map(Comment::getId).toList();

        if (!commentIds.isEmpty()) {
            contentReportRepository.deleteByTargetTypeAndTargetIdIn(ReportTargetType.COMMENT, commentIds);
            comments.forEach(commentRepository::delete);
        }
        contentReportRepository.deleteByTargetTypeAndTargetId(ReportTargetType.POST, postId);
        postRepository.delete(post);

        Map<String, Object> afterSnapshot = new LinkedHashMap<>();
        afterSnapshot.put("purged", true);
        afterSnapshot.put("purgedAt", LocalDateTime.now());
        afterSnapshot.put("deletedCommentCount", commentIds.size());

        AdminActionLog actionLog = saveActionLog(adminId, AdminTargetType.POST, postId, AdminActionType.POST_PURGE,
                request.getReasonCode(), reasonDetail, beforeSnapshot, afterSnapshot);

        return AdminDto.PostActionResponse.builder()
                .id(postId)
                .status("purged")
                .deletedAt(post.getDeletedAt())
                .purgeEligible(true)
                .latestLogId(actionLog.getId())
                .build();
    }

    @Transactional(readOnly = true)
    public AdminDto.PageResponse<AdminDto.CommentItem> getComments(int page, int size, String keyword, Long postId,
                                                                   Long authorId, String status, Boolean purgeReady,
                                                                   LocalDate dateFrom, LocalDate dateTo) {
        Page<Comment> result = commentRepository.searchForAdmin(
                normalizeKeyword(keyword),
                postId,
                authorId,
                normalizeStatus(status),
                toDateFrom(dateFrom),
                toDateTo(dateTo),
                Boolean.TRUE.equals(purgeReady),
                purgeThreshold(),
                pageRequest(page, size, "createdAt")
        );
        return toPageResponse(result.map(this::toCommentItem));
    }

    @Transactional
    public AdminDto.CommentActionResponse hideComment(Long adminId, Long commentId, AdminDto.CommentActionRequest request) {
        String reasonDetail = validateActionRequest(request);
        Comment comment = getAdminComment(commentId);
        Map<String, Object> beforeSnapshot = snapshotComment(comment);
        int changedCount = 0;
        if (comment.getDeletedAt() == null) {
            LocalDateTime deletedAt = LocalDateTime.now();
            changedCount = 1;
            comment.setDeletedAt(deletedAt);
            if (comment.getParent() == null) {
                for (Comment child : comment.getChildren()) {
                    if (child.getDeletedAt() == null) {
                        child.setDeletedAt(deletedAt);
                        changedCount++;
                    }
                }
            } else {
                List<Comment> siblings = commentRepository.findByParentIdOrderByCreatedAtAsc(comment.getParent().getId());
                changedCount += cascadeSoftDeleteByReplyToId(comment.getId(), siblings, deletedAt);
            }
            changeCommentCount(comment.getPost(), -changedCount);
        }

        Map<String, Object> afterSnapshot = snapshotComment(comment);
        afterSnapshot.put("changedCount", changedCount);
        AdminActionLog actionLog = saveActionLog(adminId, AdminTargetType.COMMENT, comment.getId(), AdminActionType.COMMENT_HIDE,
                request.getReasonCode(), reasonDetail, beforeSnapshot, afterSnapshot);
        return toCommentActionResponse(comment, actionLog.getId());
    }

    @Transactional
    public AdminDto.CommentActionResponse restoreComment(Long adminId, Long commentId, AdminDto.CommentActionRequest request) {
        String reasonDetail = validateActionRequest(request);
        Comment comment = getAdminComment(commentId);
        Map<String, Object> beforeSnapshot = snapshotComment(comment);
        int restoredCount = 0;
        if (comment.getDeletedAt() != null) {
            restoredCount = 1;
            LocalDateTime parentDeletedAt = comment.getDeletedAt();
            comment.setDeletedAt(null);
            if (comment.getParent() == null) {
                for (Comment child : comment.getChildren()) {
                    // 부모가 숨겨진 시점과 같거나 이후에 삭제된 자식만 복구
                    // (그 이전에 삭제된 자식은 사용자가 직접 지운 것)
                    if (child.getDeletedAt() != null && !child.getDeletedAt().isBefore(parentDeletedAt)) {
                        child.setDeletedAt(null);
                        restoredCount++;
                    }
                }
            } else {
                if (comment.getParent().getDeletedAt() != null) {
                    throw new IllegalStateException("부모 댓글이 삭제 상태이면 대댓글만 복구할 수 없습니다.");
                }
                List<Comment> siblings = commentRepository.findByParentIdOrderByCreatedAtAsc(comment.getParent().getId());
                restoredCount += cascadeRestoreByReplyToId(comment.getId(), siblings);
            }
            changeCommentCount(comment.getPost(), restoredCount);
        }

        Map<String, Object> afterSnapshot = snapshotComment(comment);
        afterSnapshot.put("restoredCount", restoredCount);
        AdminActionLog actionLog = saveActionLog(adminId, AdminTargetType.COMMENT, comment.getId(), AdminActionType.COMMENT_RESTORE,
                request.getReasonCode(), reasonDetail, beforeSnapshot, afterSnapshot);
        return toCommentActionResponse(comment, actionLog.getId());
    }

    @Transactional
    public AdminDto.CommentActionResponse purgeComment(Long adminId, Long commentId, AdminDto.CommentActionRequest request) {
        String reasonDetail = validateActionRequest(request);
        Comment root = getAdminComment(commentId);
        ensurePurgeAllowed(root.getDeletedAt(), "댓글");

        List<Comment> purgeCandidates = getPurgeCommentCandidates(root);
        if (purgeCandidates.stream().anyMatch(candidate -> candidate.getDeletedAt() == null)) {
            throw new IllegalStateException("삭제 상태가 아닌 댓글이 포함되어 있어 영구 삭제할 수 없습니다.");
        }

        Map<String, Object> beforeSnapshot = snapshotComment(root);
        List<Long> purgeIds = purgeCandidates.stream().map(Comment::getId).toList();
        contentReportRepository.deleteByTargetTypeAndTargetIdIn(ReportTargetType.COMMENT, purgeIds);

        if (root.getParent() == null) {
            purgeCandidates.stream()
                    .filter(candidate -> !Objects.equals(candidate.getId(), root.getId()))
                    .forEach(commentRepository::delete);
            commentRepository.delete(root);
        } else {
            purgeCandidates.forEach(commentRepository::delete);
        }

        Map<String, Object> afterSnapshot = new LinkedHashMap<>();
        afterSnapshot.put("purged", true);
        afterSnapshot.put("purgedAt", LocalDateTime.now());
        afterSnapshot.put("purgedCount", purgeIds.size());

        AdminActionLog actionLog = saveActionLog(adminId, AdminTargetType.COMMENT, commentId, AdminActionType.COMMENT_PURGE,
                request.getReasonCode(), reasonDetail, beforeSnapshot, afterSnapshot);

        return AdminDto.CommentActionResponse.builder()
                .id(commentId)
                .status("purged")
                .deletedAt(root.getDeletedAt())
                .purgeEligible(true)
                .latestLogId(actionLog.getId())
                .build();
    }

    @Transactional(readOnly = true)
    public List<AdminDto.PostCategoryOption> getPostCategories() {
        return categoryRepository.findAllByOrderBySortOrderAsc().stream().map(this::toPostCategoryOption).toList();
    }


    @Transactional(readOnly = true)
    public AdminDto.PageResponse<AdminDto.ReportItem> getReports(int page, int size, Long reporterUserId,
                                                                 ReportTargetType targetType, ContentReportStatus status,
                                                                 ContentReportReason reasonCode,
                                                                 LocalDate dateFrom, LocalDate dateTo) {
        Page<ContentReport> result = contentReportRepository.searchForAdmin(
                reporterUserId,
                targetType,
                status,
                reasonCode,
                toDateFrom(dateFrom),
                toDateTo(dateTo),
                pageRequest(page, size, "createdAt")
        );
        List<AdminDto.ReportItem> items = toReportItems(result.getContent());
        return AdminDto.PageResponse.<AdminDto.ReportItem>builder()
                .items(items)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Transactional
    public AdminDto.ReportActionResponse updateReportStatus(Long adminId, Long reportId,
                                                            AdminDto.ReportStatusUpdateRequest request) {
        String reasonDetail = validateActionRequest(request);
        if (request.getStatus() == ContentReportStatus.OPEN) {
            throw new IllegalArgumentException("신고 상태를 OPEN으로 되돌릴 수 없습니다.");
        }

        ContentReport report = contentReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("해당 신고를 찾을 수 없습니다."));
        Map<String, Object> beforeSnapshot = snapshotReport(report, resolveReportTargetSummary(report));

        report.setStatus(request.getStatus());
        report.setReviewedByAdminId(adminId);
        report.setReviewedAt(LocalDateTime.now());
        report.setReviewNote(reasonDetail);

        AdminActionType actionType = switch (request.getStatus()) {
            case REVIEWING -> AdminActionType.REPORT_REVIEWING;
            case RESOLVED -> AdminActionType.REPORT_RESOLVE;
            case REJECTED -> AdminActionType.REPORT_REJECT;
            case OPEN -> throw new IllegalArgumentException("신고 상태를 OPEN으로 되돌릴 수 없습니다.");
        };

        AdminActionLog actionLog = saveActionLog(adminId, AdminTargetType.REPORT, report.getId(), actionType,
                request.getReasonCode(), reasonDetail, beforeSnapshot, snapshotReport(report, resolveReportTargetSummary(report)));

        return AdminDto.ReportActionResponse.builder()
                .id(report.getId())
                .status(report.getStatus())
                .reviewNote(report.getReviewNote())
                .reviewedAt(report.getReviewedAt())
                .latestLogId(actionLog.getId())
                .build();
    }

    @Transactional(readOnly = true)
    public AdminDto.AdminLogPageResponse getLogs(int page, int size, Long adminId, AdminTargetType targetType,
                                                 AdminActionType actionType, AdminReasonCode reasonCode,
                                                 LocalDate dateFrom, LocalDate dateTo) {
        Page<AdminActionLog> result = adminActionLogRepository.searchForAdmin(
                adminId,
                targetType,
                actionType,
                reasonCode,
                toDateFrom(dateFrom),
                toDateTo(dateTo),
                pageRequest(page, size, "createdAt")
        );
        return AdminDto.AdminLogPageResponse.builder()
                .items(toAdminLogItems(result.getContent()))
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 사용자를 찾을 수 없습니다."));
    }


    private Post getAdminPost(Long postId) {
        return postRepository.findAdminPostById(postId)
                .orElseThrow(() -> new IllegalArgumentException("해당 게시글을 찾을 수 없습니다."));
    }

    private Comment getAdminComment(Long commentId) {
        return commentRepository.findAdminCommentById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("해당 댓글을 찾을 수 없습니다."));
    }

    private int cascadeSoftDeleteByReplyToId(Long deletedId, List<Comment> pool, LocalDateTime deletedAt) {
        return cascadeSoftDeleteByReplyToId(deletedId, pool, deletedAt, 0);
    }

    private int cascadeSoftDeleteByReplyToId(Long deletedId, List<Comment> pool, LocalDateTime deletedAt, int depth) {
        if (depth > 100) {
            return 0;
        }
        int count = 0;
        for (Comment candidate : pool) {
            if (candidate.getDeletedAt() == null && Objects.equals(candidate.getReplyToId(), deletedId)) {
                candidate.setDeletedAt(deletedAt);
                count++;
                count += cascadeSoftDeleteByReplyToId(candidate.getId(), pool, deletedAt, depth + 1);
            }
        }
        return count;
    }

    private int cascadeRestoreByReplyToId(Long restoredId, List<Comment> pool) {
        return cascadeRestoreByReplyToId(restoredId, pool, 0);
    }

    private int cascadeRestoreByReplyToId(Long restoredId, List<Comment> pool, int depth) {
        if (depth > 100) {
            return 0;
        }
        int count = 0;
        for (Comment candidate : pool) {
            if (candidate.getDeletedAt() != null && Objects.equals(candidate.getReplyToId(), restoredId)) {
                candidate.setDeletedAt(null);
                count++;
                count += cascadeRestoreByReplyToId(candidate.getId(), pool, depth + 1);
            }
        }
        return count;
    }

    private void changeCommentCount(Post post, int delta) {
        post.setCommentCount(Math.max(0, post.getCommentCount() + delta));
    }


    private String validateActionRequest(AdminDto.AdminActionRequest request) {
        if (request == null || request.getReasonCode() == null) {
            throw new AdminRequestException("입력값을 확인해 주세요.", Map.of("reasonCode", "사유 코드를 선택해 주세요."));
        }
        String normalizedDetail = normalizeNullable(request.getReasonDetail());
        if (request.getReasonCode() == AdminReasonCode.OTHER && normalizedDetail == null) {
            throw new AdminRequestException("입력값을 확인해 주세요.", Map.of("reasonDetail", "기타 사유를 직접 입력해 주세요."));
        }
        return normalizedDetail;
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String normalized = keyword.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeProvider(String provider) {
        if (provider == null) {
            return null;
        }
        String normalized = provider.trim().toLowerCase();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeRoleFilter(String role) {
        if (role == null) {
            return null;
        }
        String normalized = role.trim().toUpperCase();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeRoleValue(String role) {
        String normalized = normalizeRoleFilter(role);
        if (!"USER".equals(normalized) && !"ADMIN".equals(normalized)) {
            throw new AdminRequestException("입력값을 확인해 주세요.", Map.of("role", "USER 또는 ADMIN 중 하나를 선택해 주세요."));
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "active";
        }
        String normalized = status.trim().toLowerCase();
        if (!List.of("active", "deleted", "all").contains(normalized)) {
            throw new IllegalArgumentException("status는 active, deleted, all 중 하나여야 합니다.");
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private LocalDateTime toDateFrom(LocalDate date) {
        return date != null ? date.atStartOfDay() : null;
    }

    private LocalDateTime toDateTo(LocalDate date) {
        return date != null ? date.plusDays(1).atStartOfDay() : null;
    }

    private PageRequest pageRequest(int page, int size, String sortProperty) {
        int safePage = Math.max(page, 0);
        int safeSize = size > 0 ? size : DEFAULT_PAGE_SIZE;
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, sortProperty));
    }

    private LocalDateTime purgeThreshold() {
        return LocalDateTime.now().minusDays(PURGE_RETENTION_DAYS);
    }

    private boolean isPurgeEligible(LocalDateTime deletedAt) {
        return deletedAt != null && !deletedAt.isAfter(purgeThreshold());
    }

    private void ensurePurgeAllowed(LocalDateTime deletedAt, String targetLabel) {
        if (deletedAt == null) {
            throw new IllegalStateException(targetLabel + "은(는) 삭제 상태가 아니어서 영구 삭제할 수 없습니다.");
        }
        if (!isPurgeEligible(deletedAt)) {
            throw new IllegalStateException(targetLabel + "은(는) 삭제 후 30일이 지나야 영구 삭제할 수 있습니다.");
        }
    }

    private <T> AdminDto.PageResponse<T> toPageResponse(Page<T> page) {
        return AdminDto.PageResponse.<T>builder()
                .items(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }

    private AdminDto.UserActionResponse toUserActionResponse(User user, Long latestLogId) {
        return AdminDto.UserActionResponse.builder()
                .id(user.getId())
                .role(user.getRole())
                .accountStatus(user.getAccountStatus())
                .point(user.getPoint())
                .latestLogId(latestLogId)
                .build();
    }

    private List<AdminDto.AdminLogItem> toAdminLogItems(List<AdminActionLog> logs) {
        Map<Long, String> adminNames = resolveUserNames(logs.stream()
                .map(AdminActionLog::getAdminUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        return logs.stream().map(logItem -> toAdminLogItem(logItem, adminNames)).toList();
    }

    private List<AdminDto.ReportItem> toReportItems(List<ContentReport> reports) {
        if (reports.isEmpty()) {
            return List.of();
        }

        Set<Long> userIds = new LinkedHashSet<>();
        reports.stream().map(ContentReport::getReporterUserId).filter(Objects::nonNull).forEach(userIds::add);
        reports.stream().map(ContentReport::getReviewedByAdminId).filter(Objects::nonNull).forEach(userIds::add);

        Map<Long, String> userNames = resolveUserNames(userIds);
        Map<String, String> targetSummaries = resolveReportTargetSummaries(reports);

        return reports.stream().map(report -> AdminDto.ReportItem.builder()
                .id(report.getId())
                .reporterUserId(report.getReporterUserId())
                .reporterUserName(userNames.getOrDefault(report.getReporterUserId(), "?????#" + report.getReporterUserId()))
                .targetType(report.getTargetType())
                .targetId(report.getTargetId())
                .targetSummary(targetSummaries.getOrDefault(reportKey(report.getTargetType(), report.getTargetId()),
                        defaultReportTargetSummary(report.getTargetType(), report.getTargetId())))
                .reasonCode(report.getReasonCode())
                .reasonDetail(report.getReasonDetail())
                .status(report.getStatus())
                .reviewNote(report.getReviewNote())
                .reviewedByAdminId(report.getReviewedByAdminId())
                .reviewedByAdminName(report.getReviewedByAdminId() != null
                        ? userNames.getOrDefault(report.getReviewedByAdminId(), "관리자 #" + report.getReviewedByAdminId())
                        : null)
                .reviewedAt(report.getReviewedAt())
                .createdAt(report.getCreatedAt())
                .openCountForTarget(contentReportRepository.countByTargetTypeAndTargetIdAndStatusIn(
                        report.getTargetType(), report.getTargetId(), OPEN_REPORT_STATUSES))
                .build()).toList();
    }

    private Map<Long, String> resolveUserNames(Collection<Long> userIds) {
        Map<Long, String> names = new HashMap<>();
        if (userIds.isEmpty()) {
            return names;
        }
        userRepository.findAllById(userIds).forEach(user -> names.put(user.getId(), user.getName()));
        return names;
    }

    private Map<String, String> resolveReportTargetSummaries(List<ContentReport> reports) {
        Set<Long> postIds = reports.stream()
                .filter(report -> report.getTargetType() == ReportTargetType.POST)
                .map(ContentReport::getTargetId)
                .collect(Collectors.toSet());
        Set<Long> commentIds = reports.stream()
                .filter(report -> report.getTargetType() == ReportTargetType.COMMENT)
                .map(ContentReport::getTargetId)
                .collect(Collectors.toSet());

        Map<String, String> summaries = new HashMap<>();
        if (!postIds.isEmpty()) {
            postRepository.findAllById(postIds)
                    .forEach(post -> summaries.put(reportKey(ReportTargetType.POST, post.getId()), post.getTitle()));
        }
        if (!commentIds.isEmpty()) {
            commentRepository.findAllById(commentIds)
                    .forEach(comment -> summaries.put(reportKey(ReportTargetType.COMMENT, comment.getId()), buildPreview(comment.getContent())));
        }
        return summaries;
    }

    private AdminDto.UserItem toUserItem(User user) {
        return AdminDto.UserItem.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .provider(user.getProvider())
                .role(user.getRole())
                .accountStatus(user.getAccountStatus())
                .point(user.getPoint())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    private AdminDto.PostItem toPostItem(Post post) {
        return AdminDto.PostItem.builder()
                .id(post.getId())
                .categoryId(post.getCategory() != null ? post.getCategory().getId() : null)
                .categoryName(post.getCategory() != null ? post.getCategory().getName() : null)
                .authorId(post.getUser().getId())
                .authorName(post.getUser().getName())
                .title(post.getTitle())
                .viewCount(post.getViewCount())
                .commentCount(post.getCommentCount())
                .createdAt(post.getCreatedAt())
                .deletedAt(post.getDeletedAt())
                .purgeEligible(isPurgeEligible(post.getDeletedAt()))
                .build();
    }

    private AdminDto.PostActionResponse toPostActionResponse(Post post, Long latestLogId) {
        return AdminDto.PostActionResponse.builder()
                .id(post.getId())
                .status(post.getDeletedAt() == null ? "active" : "deleted")
                .deletedAt(post.getDeletedAt())
                .purgeEligible(isPurgeEligible(post.getDeletedAt()))
                .latestLogId(latestLogId)
                .build();
    }

    private AdminDto.CommentItem toCommentItem(Comment comment) {
        return AdminDto.CommentItem.builder()
                .id(comment.getId())
                .postId(comment.getPost().getId())
                .postTitle(comment.getPost().getTitle())
                .authorId(comment.getUser().getId())
                .authorName(comment.getUser().getName())
                .contentPreview(buildPreview(comment.getContent()))
                .parentId(comment.getParent() != null ? comment.getParent().getId() : null)
                .replyToId(comment.getReplyToId())
                .createdAt(comment.getCreatedAt())
                .deletedAt(comment.getDeletedAt())
                .purgeEligible(isPurgeEligible(comment.getDeletedAt()))
                .build();
    }

    private AdminDto.CommentActionResponse toCommentActionResponse(Comment comment, Long latestLogId) {
        return AdminDto.CommentActionResponse.builder()
                .id(comment.getId())
                .status(comment.getDeletedAt() == null ? "active" : "deleted")
                .deletedAt(comment.getDeletedAt())
                .purgeEligible(isPurgeEligible(comment.getDeletedAt()))
                .latestLogId(latestLogId)
                .build();
    }

    private AdminDto.PostCategoryOption toPostCategoryOption(Category category) {
        return AdminDto.PostCategoryOption.builder()
                .id(category.getId())
                .name(category.getName())
                .emoji(category.getEmoji())
                .build();
    }

    private AdminDto.AdminLogItem toAdminLogItem(AdminActionLog logItem, Map<Long, String> adminNames) {
        return AdminDto.AdminLogItem.builder()
                .id(logItem.getId())
                .adminUserId(logItem.getAdminUserId())
                .adminUserName(adminNames.getOrDefault(logItem.getAdminUserId(), "관리자 #" + logItem.getAdminUserId()))
                .targetType(logItem.getTargetType())
                .targetId(logItem.getTargetId())
                .actionType(logItem.getActionType())
                .reasonCode(logItem.getReasonCode())
                .reasonDetail(logItem.getReasonDetail())
                .targetSummary(extractTargetSummary(logItem))
                .beforeSnapshotJson(logItem.getBeforeSnapshotJson())
                .afterSnapshotJson(logItem.getAfterSnapshotJson())
                .createdAt(logItem.getCreatedAt())
                .build();
    }

    private String buildPreview(String html) {
        if (html == null) {
            return "";
        }
        String plain = html.replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").strip();
        return plain.length() > 80 ? plain.substring(0, 80) + "..." : plain;
    }

    private Map<String, Object> snapshotUser(User user) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", user.getId());
        snapshot.put("name", user.getName());
        snapshot.put("email", user.getEmail());
        snapshot.put("role", user.getRole());
        snapshot.put("accountStatus", user.getAccountStatus());
        snapshot.put("point", user.getPoint());
        return snapshot;
    }

    private Map<String, Object> snapshotPost(Post post) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", post.getId());
        snapshot.put("title", post.getTitle());
        snapshot.put("authorId", post.getUser().getId());
        snapshot.put("authorName", post.getUser().getName());
        snapshot.put("categoryName", post.getCategory() != null ? post.getCategory().getName() : null);
        snapshot.put("commentCount", post.getCommentCount());
        snapshot.put("status", post.getDeletedAt() == null ? "active" : "deleted");
        snapshot.put("deletedAt", post.getDeletedAt());
        snapshot.put("purgeEligible", isPurgeEligible(post.getDeletedAt()));
        return snapshot;
    }

    private Map<String, Object> snapshotComment(Comment comment) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", comment.getId());
        snapshot.put("postId", comment.getPost().getId());
        snapshot.put("postTitle", comment.getPost().getTitle());
        snapshot.put("authorId", comment.getUser().getId());
        snapshot.put("authorName", comment.getUser().getName());
        snapshot.put("contentPreview", buildPreview(comment.getContent()));
        snapshot.put("parentId", comment.getParent() != null ? comment.getParent().getId() : null);
        snapshot.put("replyToId", comment.getReplyToId());
        snapshot.put("status", comment.getDeletedAt() == null ? "active" : "deleted");
        snapshot.put("deletedAt", comment.getDeletedAt());
        snapshot.put("childCount", comment.getChildren() != null ? comment.getChildren().size() : 0);
        snapshot.put("purgeEligible", isPurgeEligible(comment.getDeletedAt()));
        return snapshot;
    }


    private Map<String, Object> snapshotReport(ContentReport report, String targetSummary) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", report.getId());
        snapshot.put("reporterUserId", report.getReporterUserId());
        snapshot.put("targetType", report.getTargetType());
        snapshot.put("targetId", report.getTargetId());
        snapshot.put("targetSummary", targetSummary);
        snapshot.put("reasonCode", report.getReasonCode());
        snapshot.put("reasonDetail", report.getReasonDetail());
        snapshot.put("status", report.getStatus());
        snapshot.put("reviewedByAdminId", report.getReviewedByAdminId());
        snapshot.put("reviewedAt", report.getReviewedAt());
        snapshot.put("reviewNote", report.getReviewNote());
        return snapshot;
    }

    private AdminActionLog saveActionLog(Long adminId, AdminTargetType targetType, Long targetId, AdminActionType actionType,
                                         AdminReasonCode reasonCode, String reasonDetail, Object beforeSnapshot, Object afterSnapshot) {
        return adminActionLogRepository.save(AdminActionLog.builder()
                .adminUserId(adminId)
                .targetType(targetType)
                .targetId(targetId)
                .actionType(actionType)
                .reasonCode(reasonCode)
                .reasonDetail(reasonDetail)
                .beforeSnapshotJson(writeSnapshot(beforeSnapshot))
                .afterSnapshotJson(writeSnapshot(afterSnapshot))
                .build());
    }

    private String writeSnapshot(Object snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot != null ? snapshot : Map.of("empty", true));
        } catch (JsonProcessingException exception) {
            throw new RuntimeException("관리자 로그 스냅샷 직렬화에 실패했습니다.", exception);
        }
    }

    private String extractTargetSummary(AdminActionLog logItem) {
        Map<String, Object> snapshot = readSnapshot(logItem.getAfterSnapshotJson());
        if (snapshot.isEmpty()) {
            snapshot = readSnapshot(logItem.getBeforeSnapshotJson());
        }
        return switch (logItem.getTargetType()) {
            case USER -> buildUserSummary(logItem.getTargetId(), snapshot);
            case POST -> buildPostSummary(logItem.getTargetId(), snapshot);
            case COMMENT -> buildCommentSummary(logItem.getTargetId(), snapshot);
            case CATEGORY -> buildCategorySummary(logItem.getActionType(), logItem.getTargetId(), snapshot);
            case REPORT -> buildReportSummary(logItem.getTargetId(), snapshot);
        };
    }

    private Map<String, Object> readSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            log.warn("[AdminService] unable to read admin log snapshot | message={}", exception.getMessage());
            return Map.of();
        }
    }

    private String buildUserSummary(Long targetId, Map<String, Object> snapshot) {
        String name = asString(snapshot.get("name"));
        String email = asString(snapshot.get("email"));
        if (name != null && email != null) {
            return name + " (" + email + ")";
        }
        if (name != null) {
            return name;
        }
        return "사용자 #" + targetId;
    }

    private String buildPostSummary(Long targetId, Map<String, Object> snapshot) {
        String title = asString(snapshot.get("title"));
        return title != null ? title : "게시글 #" + targetId;
    }

    private String buildCommentSummary(Long targetId, Map<String, Object> snapshot) {
        String preview = asString(snapshot.get("contentPreview"));
        return preview != null ? preview : "댓글 #" + targetId;
    }

    private String buildCategorySummary(AdminActionType actionType, Long targetId, Map<String, Object> snapshot) {
        if (actionType == AdminActionType.CATEGORY_REORDER) {
            return "카테고리 순서 변경 (" + snapshot.getOrDefault("categoryCount", "-") + "개)";
        }
        String name = asString(snapshot.get("name"));
        if (name != null) {
            return name;
        }
        return targetId != null ? "카테고리 #" + targetId : "카테고리";
    }

    private String buildReportSummary(Long targetId, Map<String, Object> snapshot) {
        String targetType = asString(snapshot.get("targetType"));
        String targetSummary = asString(snapshot.get("targetSummary"));
        if (targetSummary != null && targetType != null) {
            return targetType + " 신고: " + targetSummary;
        }
        if (targetSummary != null) {
            return "신고: " + targetSummary;
        }
        return "신고 #" + targetId;
    }

    private String resolveReportTargetSummary(ContentReport report) {
        return switch (report.getTargetType()) {
            case POST -> postRepository.findById(report.getTargetId())
                    .map(Post::getTitle)
                    .orElse(defaultReportTargetSummary(report.getTargetType(), report.getTargetId()));
            case COMMENT -> commentRepository.findById(report.getTargetId())
                    .map(comment -> buildPreview(comment.getContent()))
                    .orElse(defaultReportTargetSummary(report.getTargetType(), report.getTargetId()));
        };
    }

    private String defaultReportTargetSummary(ReportTargetType targetType, Long targetId) {
        String label = targetType == ReportTargetType.POST ? "?롪퍓???룸Ь?" : "?癰?";
        return label + " #" + targetId;
    }

    private String reportKey(ReportTargetType targetType, Long targetId) {
        return targetType.name() + ":" + targetId;
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private List<Comment> sortCommentsForDelete(List<Comment> comments) {
        return comments.stream()
                .sorted(Comparator.comparingInt((Comment comment) -> comment.getParent() != null ? 1 : 0).reversed())
                .toList();
    }

    private List<Comment> getPurgeCommentCandidates(Comment root) {
        if (root.getParent() == null) {
            List<Comment> candidates = new ArrayList<>();
            candidates.add(root);
            candidates.addAll(commentRepository.findByParentIdOrderByCreatedAtAsc(root.getId()));
            return candidates;
        }

        List<Comment> siblings = commentRepository.findByParentIdOrderByCreatedAtAsc(root.getParent().getId());
        List<Comment> candidates = new ArrayList<>();
        candidates.add(root);
        collectRepliesForPurge(root.getId(), siblings, candidates, 0);
        return candidates;
    }

    private void collectRepliesForPurge(Long rootId, List<Comment> pool, List<Comment> candidates, int depth) {
        if (depth > 100) {
            return;
        }
        for (Comment candidate : pool) {
            if (Objects.equals(candidate.getReplyToId(), rootId)) {
                candidates.add(candidate);
                collectRepliesForPurge(candidate.getId(), pool, candidates, depth + 1);
            }
        }
    }
}
