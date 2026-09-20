package com.example.demo.community.service;

import com.example.demo.config.MessageArgumentException;
import com.example.demo.config.MessageStateException;
import com.example.demo.community.dto.ContentReportDto;
import com.example.demo.community.entity.Comment;
import com.example.demo.community.entity.ContentReport;
import com.example.demo.community.entity.ContentReportReason;
import com.example.demo.community.entity.ContentReportStatus;
import com.example.demo.community.entity.Post;
import com.example.demo.community.entity.ReportTargetType;
import com.example.demo.community.repository.CommentRepository;
import com.example.demo.community.repository.ContentReportRepository;
import com.example.demo.community.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ContentReportService {

    private final ContentReportRepository contentReportRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    @Transactional
    public ContentReportDto.Response reportPost(Long reporterUserId, Long postId, ContentReportDto.CreateRequest request) {
        validateRequest(request);
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new MessageArgumentException("community.error.postNotFound", "존재하지 않는 게시글입니다."));
        validateNotOwnContent(reporterUserId, post.getUser() == null ? null : post.getUser().getId());
        validateDuplicateReport(reporterUserId, ReportTargetType.POST, post.getId());

        ContentReport saved = contentReportRepository.save(ContentReport.builder()
                .reporterUserId(reporterUserId)
                .targetType(ReportTargetType.POST)
                .targetId(post.getId())
                .reasonCode(request.getReasonCode())
                .reasonDetail(normalizeNullable(request.getReasonDetail()))
                .build());

        return toResponse(saved);
    }

    @Transactional
    public ContentReportDto.Response reportComment(Long reporterUserId, Long commentId, ContentReportDto.CreateRequest request) {
        validateRequest(request);
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new MessageArgumentException("community.error.commentNotFound", "존재하지 않는 댓글입니다."));
        if (comment.getDeletedAt() != null) {
            throw new MessageStateException("community.error.deletedCommentReport", "삭제된 댓글은 신고할 수 없습니다.");
        }
        validateNotOwnContent(reporterUserId, comment.getUser() == null ? null : comment.getUser().getId());
        validateDuplicateReport(reporterUserId, ReportTargetType.COMMENT, comment.getId());

        ContentReport saved = contentReportRepository.save(ContentReport.builder()
                .reporterUserId(reporterUserId)
                .targetType(ReportTargetType.COMMENT)
                .targetId(comment.getId())
                .reasonCode(request.getReasonCode())
                .reasonDetail(normalizeNullable(request.getReasonDetail()))
                .build());

        return toResponse(saved);
    }

    private void validateRequest(ContentReportDto.CreateRequest request) {
        if (request == null || request.getReasonCode() == null) {
            throw new MessageArgumentException("community.alert.reportReasonRequired", "신고 사유를 선택해 주세요.");
        }
        if (request.getReasonCode() == ContentReportReason.OTHER && normalizeNullable(request.getReasonDetail()) == null) {
            throw new MessageArgumentException("community.error.reportDetailRequired", "기타 사유를 선택한 경우 상세 내용을 입력해 주세요.");
        }
    }

    /**
     * 자기 글·댓글은 신고할 수 없다.
     *
     * <p>막지 않으면 관리자 신고함에 자기 글을 신고한 건이 쌓인다. 지우고 싶으면 삭제하면
     * 되는 일이라 신고로 올 이유가 없고, 장난이나 실수로 들어오면 검토할 사람의 시간만 쓴다.
     */
    private void validateNotOwnContent(Long reporterUserId, Long authorUserId) {
        if (reporterUserId != null && reporterUserId.equals(authorUserId)) {
            throw new MessageStateException("community.error.selfReport", "자기 글은 신고할 수 없습니다.");
        }
    }

    private void validateDuplicateReport(Long reporterUserId, ReportTargetType targetType, Long targetId) {
        boolean exists = contentReportRepository.existsByReporterUserIdAndTargetTypeAndTargetIdAndStatusIn(
                reporterUserId,
                targetType,
                targetId,
                List.of(ContentReportStatus.OPEN, ContentReportStatus.REVIEWING)
        );
        if (exists) {
            throw new MessageStateException("community.error.alreadyReported", "이미 접수된 신고가 있습니다. 검토 결과를 기다려 주세요.");
        }
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private ContentReportDto.Response toResponse(ContentReport report) {
        return ContentReportDto.Response.builder()
                .id(report.getId())
                .targetType(report.getTargetType())
                .targetId(report.getTargetId())
                .status(report.getStatus())
                .createdAt(report.getCreatedAt())
                .build();
    }
}
