package com.example.demo.community.service;

import com.example.demo.community.config.CommunityProperties;
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
    private final CommunityProperties communityProperties;

    /** 홍보 글 1건 게시 비용. */
    private static final int PROMO_COST = 500;

    /*
     * 전체 목록에는 홍보 글도 함께 나온다.
     * 홍보 글은 500P 를 내고 올리는 유료 글이라 목록에서 빼면 산 것이 사라진다.
     * (오른쪽 레일은 1400px 미만에서 숨으므로 레일만으로는 노출을 보장하지 못한다.)
     */
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
    public Page<PostDto> getPostsByUser(Long userId, Pageable pageable) {
        return postRepository.findByUser_IdAndDeletedAtIsNull(userId, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<PostDto> searchPostsByUser(Long userId, String keyword, Pageable pageable) {
        return postRepository.findByUser_IdAndTitleContainingIgnoreCaseAndDeletedAtIsNull(userId, keyword, pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<PostDto> getPostsByUserAndCategory(Long userId, Long categoryId, Pageable pageable) {
        return postRepository.findByUser_IdAndCategoryIdAndDeletedAtIsNull(userId, categoryId, pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<PostDto> searchPostsByUserAndCategory(Long userId, Long categoryId, String keyword, Pageable pageable) {
        return postRepository.findByUser_IdAndCategoryIdAndTitleContainingIgnoreCaseAndDeletedAtIsNull(userId, categoryId, keyword, pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<PostDto> searchPostsByCategory(Long categoryId, String keyword, Pageable pageable) {
        return postRepository.findByCategoryIdAndTitleContainingIgnoreCaseAndDeletedAtIsNull(categoryId, keyword, pageable)
                .map(this::toDto);
    }

    /** 인기 글. 여기에도 홍보 글이 오르면 목록 맨 위가 광고가 된다. */
    @Transactional(readOnly = true)
    public List<PostDto> getTrendingPosts() {
        return postRepository
                .findTrendingExcludingCategory(communityProperties.getPromoCategoryId(),
                        org.springframework.data.domain.PageRequest.of(0, 5))
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
                .placeContentId(blankToNull(req.getPlaceContentId()))
                .placeContentTypeId(blankToNull(req.getPlaceContentTypeId()))
                .placeTitle(blankToNull(req.getPlaceTitle()))
                .build();

        // 홍보 글은 저장 전에 자격과 포인트를 확인한다. 통과해야 노출 기간을 붙여 저장한다.
        boolean promo = communityProperties.getPromoCategoryId() == category.getId();
        if (promo) {
            if (!"OWNER".equals(user.getRole())) {
                throw new IllegalStateException("홍보 글은 사업자 회원만 작성할 수 있습니다.");
            }
            if (user.getPoint() < PROMO_COST) {
                throw new IllegalStateException(
                        "포인트가 부족합니다. (필요: " + PROMO_COST + "P, 보유: " + user.getPoint() + "P)");
            }
            // 500P 로 사는 것은 '사이드바 7일 노출'이다. 기간이 지나면 레일에서만 내려온다.
            post.setPromoUntil(LocalDateTime.now().plusDays(communityProperties.getPromoExposureDays()));
        }

        Post saved = postRepository.save(post);

        if (promo) {
            userRepository.updatePoint(userId, -PROMO_COST);
        }

        translationService.translatePostAsync(saved.getId(), req.getTitle(), req.getContent());
        return saved.getId();
    }

    @Transactional(readOnly = true)
    public List<PostDto> getPostsByPlace(String contentId) {
        if (contentId == null || contentId.isBlank()) return List.of();
        return postRepository.findTop10ByPlaceContentIdAndDeletedAtIsNullOrderByCreatedAtDesc(contentId)
                .stream().map(this::toDto).toList();
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

    /**
     * 본문에서 첫 이미지 주소를 꺼낸다. 목록 썸네일로 쓴다.
     *
     * <p>글 목록이 글자만 나열되면 게시판처럼 보인다. 사진이 한 장이라도 있으면
     * 목록이 훨씬 읽힌다. 본문은 에디터가 만든 HTML 이라 첫 img 태그를 찾으면 된다.
     * 이미지가 없는 글도 많으므로 없으면 null 이고, 화면은 글자만으로도 서야 한다.
     */
    private String firstImageUrl(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        java.util.regex.Matcher m = FIRST_IMAGE.matcher(html);
        if (!m.find()) {
            return null;
        }
        String src = m.group(1).trim();
        // data: 로 박힌 이미지는 목록에 싣기엔 너무 무겁다. 그런 글은 글자만 보여 준다.
        return src.startsWith("data:") ? null : src;
    }

    private static final java.util.regex.Pattern FIRST_IMAGE =
            java.util.regex.Pattern.compile("<img[^>]+src\\s*=\\s*[\"']([^\"']+)[\"']",
                    java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * 목록용 DTO 변환. 사이드바 등 다른 화면도 목록과 같은 표현을 써야 해서 공개한다.
     * (제목 다국어·썸네일 추출 규칙이 화면마다 갈리면 안 된다.)
     */
    public PostDto toListDto(Post post) {
        return toDto(post);
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
                .thumbnailUrl(firstImageUrl(post.getContent()))
                .titleEn(post.getTitleEn())
                .contentEn(post.getContentEn())
                .contentEnPreview(post.getContentEn() != null ? buildPreview(post.getContentEn()) : null)
                .titleKo(post.getTitleKo())
                .contentKo(post.getContentKo())
                .contentKoPreview(post.getContentKo() != null ? buildPreview(post.getContentKo()) : null)
                .placeContentId(post.getPlaceContentId())
                .placeContentTypeId(post.getPlaceContentTypeId())
                .placeTitle(post.getPlaceTitle())
                .authorName(post.getUser().getName())
                .authorProfileImage(post.getUser().getProfileImage())
                .viewCount(post.getViewCount())
                .commentCount(post.getCommentCount())
                .createdAt(post.getCreatedAt())
                .build();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
