package com.example.demo.admin.controller;

import com.example.demo.admin.dto.AdminDto;
import com.example.demo.admin.entity.AdminActionType;
import com.example.demo.admin.entity.AdminReasonCode;
import com.example.demo.admin.entity.AdminTargetType;
import com.example.demo.admin.exception.AdminRequestException;
import com.example.demo.admin.service.AdminService;
import com.example.demo.community.entity.ContentReportReason;
import com.example.demo.community.entity.ContentReportStatus;
import com.example.demo.community.entity.ReportTargetType;
import com.example.demo.user.entity.AccountStatus;
import com.example.demo.user.entity.OwnerRequest;
import com.example.demo.user.entity.OwnerRequestStatus;
import com.example.demo.user.service.OwnerRequestService;
import com.example.demo.user.util.SecurityUtil;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/admin/api")
@RequiredArgsConstructor
public class AdminApiController {

    private final AdminService adminService;
    private final OwnerRequestService ownerRequestService;

    @GetMapping("/dashboard")
    public AdminDto.DashboardResponse dashboard() {
        return adminService.getDashboard();
    }

    @GetMapping("/users")
    public AdminDto.PageResponse<AdminDto.UserItem> users(@RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "10") int size,
                                                          @RequestParam(required = false) String keyword,
                                                          @RequestParam(required = false) String role,
                                                          @RequestParam(required = false) String provider,
                                                          @RequestParam(required = false) AccountStatus accountStatus,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return adminService.getUsers(page, size, keyword, role, provider, accountStatus, dateFrom, dateTo);
    }

    @PatchMapping("/users/{id}/role")
    public AdminDto.UserActionResponse updateRole(@PathVariable Long id,
                                                  @Valid @RequestBody AdminDto.RoleUpdateRequest request) {
        return adminService.updateUserRole(SecurityUtil.getCurrentUserId(), id, request);
    }

    @PostMapping("/users/{id}/points")
    public AdminDto.UserActionResponse adjustPoints(@PathVariable Long id,
                                                    @Valid @RequestBody AdminDto.PointAdjustRequest request) {
        return adminService.adjustUserPoints(SecurityUtil.getCurrentUserId(), id, request);
    }

    @PatchMapping("/users/{id}/account-status")
    public AdminDto.UserActionResponse updateAccountStatus(@PathVariable Long id,
                                                           @Valid @RequestBody AdminDto.AccountStatusUpdateRequest request) {
        return adminService.updateUserAccountStatus(SecurityUtil.getCurrentUserId(), id, request);
    }

    @GetMapping("/owner-applications/pending")
    public List<Map<String, Object>> pendingOwnerRequests() {
        return ownerRequestService.findPendingRequests().stream()
                .map(this::toOwnerRequestItem)
                .toList();
    }

    @GetMapping("/owner-applications")
    public List<Map<String, Object>> ownerRequests(@RequestParam(defaultValue = "PENDING") OwnerRequestStatus status) {
        return ownerRequestService.findRequestsByStatus(status).stream()
                .map(this::toOwnerRequestItem)
                .toList();
    }

    @GetMapping("/owner-applications/{id}/documents/{type}")
    public ResponseEntity<Resource> ownerRequestDocument(@PathVariable Long id,
                                                         @PathVariable String type) {
        Path documentPath = ownerRequestService.findDocumentPath(id, type);
        Resource resource = new FileSystemResource(documentPath);
        return ResponseEntity.ok()
                .contentType(documentContentType(documentPath))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + documentPath.getFileName() + "\"")
                .body(resource);
    }

    /**
     * 서류를 무슨 종류로 내려줄지 확장자로 정한다.
     *
     * <p>전부 {@code application/octet-stream} 으로 보내면 {@code inline} 을 붙여도
     * 브라우저가 그냥 내려받는다. 사업자등록증 한 장 보려고 파일을 받아서 다시 여는
     * 일이 신청 한 건에 세 번씩 반복된다. 사진과 PDF만 제 종류로 내려주면
     * 새 탭에서 바로 열린다.
     *
     * <p>목록에 없는 것은 그대로 내려받게 둔다. 특히 <b>html 과 svg 는 일부러 뺐다</b> -
     * 그 둘은 스크립트를 품을 수 있어서, 화면에 띄우면 신청자가 올린 파일이
     * 관리자 브라우저에서 실행된다.
     */
    private MediaType documentContentType(Path documentPath) {
        String fileName = documentPath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".pdf")) {
            return MediaType.APPLICATION_PDF;
        }
        if (fileName.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (fileName.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        }
        if (fileName.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        if (fileName.endsWith(".heic")) {
            return MediaType.parseMediaType("image/heic");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    @PostMapping("/owner-applications/{id}/approve")
    public Map<String, Object> approveOwnerRequest(@PathVariable Long id,
                                                       @RequestBody(required = false) Map<String, String> request) {
        ownerRequestService.approve(id, request == null ? null : request.get("reviewComment"));
        return Map.of("id", id, "status", "APPROVED", "message", "사업자 신청을 승인했습니다.");
    }

    @PostMapping("/owner-applications/{id}/reject")
    public Map<String, Object> rejectOwnerRequest(@PathVariable Long id,
                                                      @RequestBody(required = false) Map<String, String> request) {
        ownerRequestService.reject(id, request == null ? null : request.get("reviewComment"));
        return Map.of("id", id, "status", "REJECTED", "message", "사업자 신청을 반려했습니다.");
    }

    @GetMapping("/posts")
    public AdminDto.PageResponse<AdminDto.PostItem> posts(@RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "10") int size,
                                                          @RequestParam(required = false) String keyword,
                                                          @RequestParam(required = false) Long categoryId,
                                                          @RequestParam(required = false) Long authorId,
                                                          @RequestParam(required = false) String status,
                                                          @RequestParam(required = false) Boolean purgeReady,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return adminService.getPosts(page, size, keyword, categoryId, authorId, status, purgeReady, dateFrom, dateTo);
    }

    @PostMapping("/posts/{id}/hide")
    public AdminDto.PostActionResponse hidePost(@PathVariable Long id,
                                                @Valid @RequestBody AdminDto.PostActionRequest request) {
        return adminService.hidePost(SecurityUtil.getCurrentUserId(), id, request);
    }

    @PostMapping("/posts/{id}/restore")
    public AdminDto.PostActionResponse restorePost(@PathVariable Long id,
                                                   @Valid @RequestBody AdminDto.PostActionRequest request) {
        return adminService.restorePost(SecurityUtil.getCurrentUserId(), id, request);
    }

    @PostMapping("/posts/{id}/purge")
    public AdminDto.PostActionResponse purgePost(@PathVariable Long id,
                                                 @Valid @RequestBody AdminDto.PostActionRequest request) {
        return adminService.purgePost(SecurityUtil.getCurrentUserId(), id, request);
    }

    @GetMapping("/comments")
    public AdminDto.PageResponse<AdminDto.CommentItem> comments(@RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "10") int size,
                                                                @RequestParam(required = false) String keyword,
                                                                @RequestParam(required = false) Long postId,
                                                                @RequestParam(required = false) Long authorId,
                                                                @RequestParam(required = false) String status,
                                                                @RequestParam(required = false) Boolean purgeReady,
                                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return adminService.getComments(page, size, keyword, postId, authorId, status, purgeReady, dateFrom, dateTo);
    }

    @PostMapping("/comments/{id}/hide")
    public AdminDto.CommentActionResponse hideComment(@PathVariable Long id,
                                                      @Valid @RequestBody AdminDto.CommentActionRequest request) {
        return adminService.hideComment(SecurityUtil.getCurrentUserId(), id, request);
    }

    @PostMapping("/comments/{id}/restore")
    public AdminDto.CommentActionResponse restoreComment(@PathVariable Long id,
                                                         @Valid @RequestBody AdminDto.CommentActionRequest request) {
        return adminService.restoreComment(SecurityUtil.getCurrentUserId(), id, request);
    }

    @PostMapping("/comments/{id}/purge")
    public AdminDto.CommentActionResponse purgeComment(@PathVariable Long id,
                                                       @Valid @RequestBody AdminDto.CommentActionRequest request) {
        return adminService.purgeComment(SecurityUtil.getCurrentUserId(), id, request);
    }

    @GetMapping("/post-categories")
    public List<AdminDto.PostCategoryOption> postCategories() {
        return adminService.getPostCategories();
    }

    @GetMapping("/reports")
    public AdminDto.PageResponse<AdminDto.ReportItem> reports(@RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "10") int size,
                                                              @RequestParam(required = false) Long reporterUserId,
                                                              @RequestParam(required = false) ReportTargetType targetType,
                                                              @RequestParam(required = false) ContentReportStatus status,
                                                              @RequestParam(required = false) ContentReportReason reasonCode,
                                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return adminService.getReports(page, size, reporterUserId, targetType, status, reasonCode, dateFrom, dateTo);
    }

    @PatchMapping("/reports/{id}/status")
    public AdminDto.ReportActionResponse updateReportStatus(@PathVariable Long id,
                                                            @Valid @RequestBody AdminDto.ReportStatusUpdateRequest request) {
        return adminService.updateReportStatus(SecurityUtil.getCurrentUserId(), id, request);
    }

    @GetMapping("/logs")
    public AdminDto.AdminLogPageResponse logs(@RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "10") int size,
                                              @RequestParam(required = false) Long adminId,
                                              @RequestParam(required = false) AdminTargetType targetType,
                                              @RequestParam(required = false) AdminActionType actionType,
                                              @RequestParam(required = false) AdminReasonCode reasonCode,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return adminService.getLogs(page, size, adminId, targetType, actionType, reasonCode, dateFrom, dateTo);
    }

    @ExceptionHandler(AdminRequestException.class)
    public ResponseEntity<Map<String, Object>> handleAdminRequest(AdminRequestException exception) {
        Map<String, Object> body = new HashMap<>();
        body.put("message", exception.getMessage());
        if (!exception.getErrors().isEmpty()) {
            body.put("errors", exception.getErrors());
        }
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> handleBadRequest(RuntimeException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(Map.of(
                "message", "입력값을 확인해 주세요.",
                "errors", errors
        ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException exception) {
        String message = "입력값을 확인해 주세요.";
        if (exception.getCause() instanceof InvalidFormatException) {
            message = "지원하지 않는 값이 포함되어 있습니다.";
        }
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    private Map<String, Object> toOwnerRequestItem(OwnerRequest request) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", request.getId());
        item.put("userId", request.getUser().getId());
        item.put("userName", request.getUser().getName());
        item.put("userEmail", request.getUser().getEmail());
        item.put("userRole", request.getUser().getRole());
        item.put("userPoint", request.getUser().getPoint());
        item.put("storeName", request.getStoreName());
        item.put("businessRegistrationNumber", request.getBusinessRegistrationNumber());
        item.put("businessRegistrationFilePath", request.getBusinessRegistrationFilePath());
        item.put("businessLicenseFilePath", request.getBusinessLicenseFilePath());
        item.put("menuFilePath", request.getMenuFilePath());
        item.put("status", request.getStatus().name());
        item.put("reviewComment", request.getReviewComment());
        item.put("reviewedAt", request.getReviewedAt());
        item.put("createdAt", request.getCreatedAt());
        return item;
    }
}
