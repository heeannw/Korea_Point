package com.example.demo.community.service;

import com.example.demo.community.dto.PostDto;
import com.example.demo.community.entity.Category;
import com.example.demo.community.entity.Post;
import com.example.demo.community.repository.CategoryRepository;
import com.example.demo.community.repository.PostRepository;
import com.example.demo.user.entity.AccountStatus;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(readOnly = true)
    public Page<PostDto> getPosts(Pageable pageable) {
        return postRepository.findByDeletedAtIsNull(pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<PostDto> getPostsByCategory(Long categoryId, Pageable pageable) {
        return postRepository.findByCategoryIdAndDeletedAtIsNull(categoryId, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<PostDto> searchPosts(String keyword, Pageable pageable) {
        return postRepository.findByTitleContainingIgnoreCaseAndDeletedAtIsNull(keyword, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<PostDto> searchPostsByCategory(Long categoryId, String keyword, Pageable pageable) {
        return postRepository.findByCategoryIdAndTitleContainingIgnoreCaseAndDeletedAtIsNull(categoryId, keyword, pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public List<PostDto> getTrendingPosts() {
        return postRepository.findTop5ByDeletedAtIsNullOrderByViewCountDesc()
                .stream()
                .map(this::toDto)
                .toList();
    }

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

    @Transactional
    public Long createPost(PostDto.PostCreateRequest req, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
        validateCommunityAccess(user);

        Category category = categoryRepository.findById(req.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 카테고리입니다."));

        Post post = Post.builder()
                .user(user)
                .category(category)
                .title(req.getTitle())
                .content(req.getContent())
                .build();

        Post saved = postRepository.save(post);

        if ("홍보".equals(category.getName())) {
            if (user.getPoint() < 500) {
                throw new IllegalStateException("포인트가 부족합니다. (필요: 500P, 보유: " + user.getPoint() + "P)");
            }
            userRepository.updatePoint(userId, -500);
        }

        translationService.translatePostAsync(saved.getId(), req.getTitle(), req.getContent());
        return saved.getId();
    }

    @Transactional
    public void deletePost(Long postId, Long userId) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));
        if (!post.getUser().getId().equals(userId)) {
            throw new IllegalStateException("삭제 권한이 없습니다.");
        }
        post.setDeletedAt(LocalDateTime.now());
    }

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
                .titleKo(post.getTitleKo())
                .contentKo(post.getContentKo())
                .contentKoPreview(post.getContentKo() != null ? buildPreview(post.getContentKo()) : null)
                .authorName(post.getUser().getName())
                .authorProfileImage(post.getUser().getProfileImage())
                .viewCount(post.getViewCount())
                .commentCount(post.getCommentCount())
                .createdAt(post.getCreatedAt())
                .build();
    }

    private String buildPreview(String html) {
        if (html == null) {
            return "";
        }
        String plain = html.replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").strip();
        return plain.length() > 120 ? plain.substring(0, 120) + "..." : plain;
    }

    private void validateCommunityAccess(User user) {
        if (user.getAccountStatus() == AccountStatus.SUSPENDED) {
            throw new IllegalStateException("커뮤니티 기능이 정지된 계정입니다.");
        }
    }
}
