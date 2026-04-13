package com.example.demo.community.service;

import com.example.demo.community.dto.PostDto;
import com.example.demo.community.entity.Category;
import com.example.demo.community.entity.Post;
import com.example.demo.community.repository.CategoryRepository;
import com.example.demo.community.repository.PostRepository;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final TranslationService translationService;

    // 전체 게시글 목록 (페이징)
    @Transactional(readOnly = true)
    public Page<PostDto> getPosts(Pageable pageable) {
        return postRepository.findByDeletedAtIsNull(pageable).map(this::toDto);
    }

    // 카테고리별 게시글 목록 (페이징)
    @Transactional(readOnly = true)
    public Page<PostDto> getPostsByCategory(Long categoryId, Pageable pageable) {
        return postRepository.findByCategoryIdAndDeletedAtIsNull(categoryId, pageable).map(this::toDto);
    }

    // 제목 검색 (전체 카테고리, 페이징)
    @Transactional(readOnly = true)
    public Page<PostDto> searchPosts(String keyword, Pageable pageable) {
        return postRepository.findByTitleContainingIgnoreCaseAndDeletedAtIsNull(keyword, pageable).map(this::toDto);
    }

    // 카테고리 + 키워드 동시 필터링 (페이징)
    @Transactional(readOnly = true)
    public Page<PostDto> searchPostsByCategory(Long categoryId, String keyword, Pageable pageable) {
        return postRepository.findByCategoryIdAndTitleContainingIgnoreCaseAndDeletedAtIsNull(categoryId, keyword, pageable).map(this::toDto);
    }

    // 인기글 상위 5개
    @Transactional(readOnly = true)
    public List<PostDto> getTrendingPosts() {
        return postRepository.findTop5ByDeletedAtIsNullOrderByViewCountDesc()
                .stream()
                .map(this::toDto)
                .toList();
    }

    // 게시글 상세 조회 + 조회수 +1 (같은 세션에서 이미 본 글은 증가 안 함)
    @Transactional
    public PostDto getPostDetail(Long postId, HttpSession session) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));

        @SuppressWarnings("unchecked")
        Set<Long> viewed = (Set<Long>) session.getAttribute("viewedPosts");
        if (viewed == null) {
            viewed = new HashSet<>();
            session.setAttribute("viewedPosts", viewed);
        }

        if (!viewed.contains(postId)) {
            postRepository.incrementViewCount(postId);
            post.setViewCount(post.getViewCount() + 1);
            viewed.add(postId);
        }

        return toDto(post);
    }

    // 게시글 작성
    @Transactional
    public Long createPost(PostDto.PostCreateRequest req, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
        Category category = categoryRepository.findById(req.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 카테고리입니다."));

        // 홍보 카테고리는 500P 차감 (부족 시 예외)
        if ("홍보".equals(category.getName())) {
            if (user.getPoint() < 500) {
                throw new IllegalStateException(
                        "포인트가 부족합니다. (필요: 500P, 보유: " + user.getPoint() + "P)");
            }
            userRepository.updatePoint(userId, -500);
        }

        Post post = Post.builder()
                .user(user)
                .category(category)
                .title(req.getTitle())
                .content(req.getContent())
                .build();

        Post saved = postRepository.save(post);

        // 번역 (API 키 없으면 null 반환하여 원문 표시)
        saved.setTitleEn(translationService.translateToEnglish(req.getTitle()));
        saved.setContentEn(translationService.translateToEnglish(req.getContent()));

        return saved.getId();
    }

    // 게시글 소프트 딜리트
    @Transactional
    public void deletePost(Long postId, Long userId) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));
        if (!post.getUser().getId().equals(userId)) {
            throw new IllegalStateException("삭제 권한이 없습니다.");
        }
        post.setDeletedAt(LocalDateTime.now());
    }

    // Post → PostDto 변환
    private PostDto toDto(Post post) {
        return PostDto.builder()
                .postId(post.getId())
                .userId(post.getUser().getId())
                .categoryId(post.getCategory() != null ? post.getCategory().getId() : null)
                .categoryName(post.getCategory() != null ? post.getCategory().getName() : null)
                .categoryNameEn(post.getCategory() != null ? post.getCategory().getNameEn() : null)
                .categoryEmoji(post.getCategory() != null ? post.getCategory().getEmoji() : null)
                .title(post.getTitle())
                .content(post.getContent())
                .contentPreview(buildPreview(post.getContent()))
                .titleEn(post.getTitleEn())
                .contentEn(post.getContentEn())
                .contentEnPreview(post.getContentEn() != null ? buildPreview(post.getContentEn()) : null)
                .authorName(post.getUser().getName())
                .authorProfileImage(post.getUser().getProfileImage())
                .viewCount(post.getViewCount())
                .commentCount(post.getCommentCount())
                .createdAt(post.getCreatedAt())
                .build();
    }

    // HTML 태그 제거 후 120자 요약 (목록 미리보기용)
    private String buildPreview(String html) {
        if (html == null) return "";
        //replace vs replaceAll 차이 replaceAll: 정규식 패턴으로 찾아서 바꾸는 함수 \\s+ 이거 정규식 문법 공백 문자
        String plain = html.replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").strip();
        return plain.length() > 120 ? plain.substring(0, 120) + "..." : plain;
    }
}
