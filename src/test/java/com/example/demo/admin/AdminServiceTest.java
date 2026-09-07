package com.example.demo.admin;

import com.example.demo.admin.dto.AdminDto;
import com.example.demo.admin.entity.AdminActionLog;
import com.example.demo.admin.entity.AdminActionType;
import com.example.demo.admin.entity.AdminReasonCode;
import com.example.demo.admin.exception.AdminRequestException;
import com.example.demo.admin.repository.AdminActionLogRepository;
import com.example.demo.admin.service.AdminService;
import com.example.demo.community.entity.Comment;
import com.example.demo.community.entity.Post;
import com.example.demo.community.repository.CategoryRepository;
import com.example.demo.community.repository.CommentRepository;
import com.example.demo.community.repository.ContentReportRepository;
import com.example.demo.community.repository.PostRepository;
import com.example.demo.user.entity.AccountStatus;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ContentReportRepository contentReportRepository;

    @Mock
    private AdminActionLogRepository adminActionLogRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks
    private AdminService adminService;

    private User adminUser;

    @BeforeEach
    void setUp() {
        adminUser = User.builder()
                .id(1L)
                .provider("google")
                .providerId("admin-1")
                .name("Admin")
                .email("admin@example.com")
                .role("ADMIN")
                .point(1000)
                .createdAt(LocalDateTime.now())
                .build();

        AtomicLong sequence = new AtomicLong(1000L);
        lenient().when(adminActionLogRepository.save(any(AdminActionLog.class))).thenAnswer(invocation -> {
            AdminActionLog log = invocation.getArgument(0);
            if (log.getId() == null) {
                log.setId(sequence.getAndIncrement());
            }
            return log;
        });
    }

    @Test
    void getUsers_withoutFiltersIncludesOtherProvidersAndRoles() {
        User member = User.builder().id(2L).provider("kakao").providerId("member-2")
                .name("Member").email("member@example.com").role("USER").build();
        User owner = User.builder().id(3L).provider("local").providerId("owner-3")
                .name("Owner").email("owner@example.com").role("OWNER").build();
        when(userRepository.searchForAdmin(
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(adminUser, member, owner)));

        var result = adminService.getUsers(0, 10, "", "", "", null, null, null);

        assertEquals(3, result.getTotalElements());
        assertEquals(List.of(1L, 2L, 3L), result.getItems().stream().map(AdminDto.UserItem::getId).toList());
        assertEquals(List.of("ADMIN", "USER", "OWNER"), result.getItems().stream().map(AdminDto.UserItem::getRole).toList());
    }

    @Test
    void updateUserRole_blocksLastAdminDemotion() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));
        when(userRepository.countByRole("ADMIN")).thenReturn(1L);

        AdminDto.RoleUpdateRequest request = new AdminDto.RoleUpdateRequest();
        request.setRole("USER");
        request.setReasonCode(AdminReasonCode.ROLE_POLICY);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> adminService.updateUserRole(2L, 1L, request));

        assertEquals("마지막 ADMIN 계정은 USER로 변경할 수 없습니다.", exception.getMessage());
    }

    @Test
    void updateUserRole_blocksSelfDemotion() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));

        AdminDto.RoleUpdateRequest request = new AdminDto.RoleUpdateRequest();
        request.setRole("USER");
        request.setReasonCode(AdminReasonCode.ROLE_POLICY);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> adminService.updateUserRole(1L, 1L, request));

        assertEquals("본인 계정의 ADMIN 권한은 해제할 수 없습니다.", exception.getMessage());
        verify(userRepository, never()).countByRole("ADMIN");
    }

    @Test
    void updateUserRole_createsAdminLog() {
        User target = User.builder()
                .id(2L)
                .provider("google")
                .providerId("user-2")
                .name("User")
                .email("user@example.com")
                .role("USER")
                .point(150)
                .build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        AdminDto.RoleUpdateRequest request = new AdminDto.RoleUpdateRequest();
        request.setRole("ADMIN");
        request.setReasonCode(AdminReasonCode.ROLE_POLICY);
        request.setReasonDetail("운영상 관리자 승격");

        AdminDto.UserActionResponse response = adminService.updateUserRole(1L, 2L, request);

        ArgumentCaptor<AdminActionLog> captor = ArgumentCaptor.forClass(AdminActionLog.class);
        verify(adminActionLogRepository).save(captor.capture());
        AdminActionLog savedLog = captor.getValue();

        assertEquals("ADMIN", target.getRole());
        assertEquals(AdminActionType.USER_ROLE_CHANGE, savedLog.getActionType());
        assertEquals(AdminReasonCode.ROLE_POLICY, savedLog.getReasonCode());
        assertNotNull(savedLog.getBeforeSnapshotJson());
        assertNotNull(savedLog.getAfterSnapshotJson());
        assertEquals(1000L, response.getLatestLogId());
    }

    @Test
    void adjustUserPoints_rejectsOtherWithoutDetail() {
        AdminDto.PointAdjustRequest request = new AdminDto.PointAdjustRequest();
        request.setDelta(20);
        request.setReasonCode(AdminReasonCode.OTHER);

        AdminRequestException exception = assertThrows(AdminRequestException.class,
                () -> adminService.adjustUserPoints(1L, 2L, request));

        assertEquals("기타 사유를 직접 입력해 주세요.", exception.getErrors().get("reasonDetail"));
    }

    @Test
    void hideComment_hidesTopLevelCommentAndChildren() {
        Post post = Post.builder()
                .id(10L)
                .user(adminUser)
                .title("post")
                .content("content")
                .commentCount(3)
                .createdAt(LocalDateTime.now())
                .build();

        Comment topLevel = Comment.builder()
                .id(100L)
                .post(post)
                .user(adminUser)
                .content("top")
                .createdAt(LocalDateTime.now())
                .children(new ArrayList<>())
                .build();

        Comment childOne = Comment.builder()
                .id(101L)
                .post(post)
                .user(adminUser)
                .parent(topLevel)
                .replyToId(topLevel.getId())
                .content("child-1")
                .createdAt(LocalDateTime.now())
                .build();

        Comment childTwo = Comment.builder()
                .id(102L)
                .post(post)
                .user(adminUser)
                .parent(topLevel)
                .replyToId(childOne.getId())
                .content("child-2")
                .createdAt(LocalDateTime.now())
                .build();

        topLevel.setChildren(new ArrayList<>(List.of(childOne, childTwo)));
        when(commentRepository.findAdminCommentById(100L)).thenReturn(Optional.of(topLevel));

        AdminDto.CommentActionRequest request = new AdminDto.CommentActionRequest();
        request.setReasonCode(AdminReasonCode.SPAM);

        AdminDto.CommentActionResponse response = adminService.hideComment(1L, 100L, request);

        assertNotNull(topLevel.getDeletedAt());
        assertNotNull(childOne.getDeletedAt());
        assertNotNull(childTwo.getDeletedAt());
        assertEquals(0, post.getCommentCount());
        assertEquals("deleted", response.getStatus());
        assertNotNull(response.getLatestLogId());
    }

    @Test
    void restoreComment_blocksReplyWhenParentIsHidden() {
        Post post = Post.builder()
                .id(20L)
                .user(adminUser)
                .title("post")
                .content("content")
                .commentCount(0)
                .createdAt(LocalDateTime.now())
                .build();

        Comment parent = Comment.builder()
                .id(200L)
                .post(post)
                .user(adminUser)
                .content("parent")
                .createdAt(LocalDateTime.now())
                .deletedAt(LocalDateTime.now())
                .children(new ArrayList<>())
                .build();

        Comment reply = Comment.builder()
                .id(201L)
                .post(post)
                .user(adminUser)
                .parent(parent)
                .replyToId(parent.getId())
                .content("reply")
                .createdAt(LocalDateTime.now())
                .deletedAt(LocalDateTime.now())
                .build();

        when(commentRepository.findAdminCommentById(201L)).thenReturn(Optional.of(reply));

        AdminDto.CommentActionRequest request = new AdminDto.CommentActionRequest();
        request.setReasonCode(AdminReasonCode.MANUAL_RESTORE);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> adminService.restoreComment(1L, 201L, request));

        assertEquals("부모 댓글이 삭제 상태이면 대댓글만 복구할 수 없습니다.", exception.getMessage());
    }

    @Test
    void getDashboard_includesRecentLogs() {
        AdminActionLog log = AdminActionLog.builder()
                .id(99L)
                .adminUserId(1L)
                .actionType(AdminActionType.POST_HIDE)
                .targetType(com.example.demo.admin.entity.AdminTargetType.POST)
                .targetId(15L)
                .reasonCode(AdminReasonCode.SPAM)
                .beforeSnapshotJson("{\"title\":\"before\"}")
                .afterSnapshotJson("{\"title\":\"after\"}")
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepository.count()).thenReturn(2L);
        when(userRepository.countByRole("ADMIN")).thenReturn(1L);
        when(userRepository.countByAccountStatus(AccountStatus.SUSPENDED)).thenReturn(0L);
        when(postRepository.countByDeletedAtIsNull()).thenReturn(3L);
        when(commentRepository.countByDeletedAtIsNull()).thenReturn(4L);
        when(contentReportRepository.countByStatusIn(any())).thenReturn(0L);
        when(userRepository.findTop5ByOrderByCreatedAtDesc()).thenReturn(List.of(adminUser));
        when(postRepository.findTop5ByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of());
        when(commentRepository.findTop5ByDeletedAtIsNullOrderByCreatedAtDesc()).thenReturn(List.of());
        when(contentReportRepository.findTop5ByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(adminActionLogRepository.findTop5ByOrderByCreatedAtDesc()).thenReturn(List.of(log));
        when(adminActionLogRepository.countByActionTypeInAndCreatedAtGreaterThanEqual(any(), any())).thenReturn(0L);
        when(userRepository.findAllById(any())).thenReturn(List.of(adminUser));

        AdminDto.DashboardResponse response = adminService.getDashboard();

        assertEquals(1, response.getRecentLogs().size());
        assertTrue(response.getRecentLogs().get(0).getTargetSummary().contains("after"));
    }
}
