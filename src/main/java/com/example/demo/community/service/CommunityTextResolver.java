package com.example.demo.community.service;

import com.example.demo.community.dto.CommentDto;
import com.example.demo.community.dto.PostDto;
import com.example.demo.community.entity.CommunityTranslation;
import com.example.demo.community.entity.CommunityTranslationTarget;
import com.example.demo.community.repository.CommunityTranslationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 화면에 찍을 글·댓글 문구를 언어에 맞게 하나로 정리한다.
 *
 * <p>전에는 화면마다 이런 식이 붙어 있었다.
 * {@code locale == 'en' and titleEn != null ? titleEn : (titleKo != null ? titleKo : title)}
 * 일곱 군데에 같은 식이 흩어져 있어서 언어를 더할 때마다 일곱 군데를 고쳐야 했고,
 * 실제로 일본어·중국어·불어는 이 식에 자리가 없어 국문으로 떨어졌다.
 *
 * <p>고르는 규칙은 한 곳에 둔다.
 * <ul>
 *   <li>국문 — 영어로 쓴 글이면 국문 번역, 아니면 원문</li>
 *   <li>영문 — 글 표의 영문 칸, 없으면 원문</li>
 *   <li>그 밖 — {@code community_translation} 표, 아직 없으면 국문</li>
 * </ul>
 *
 * <p>아직 안 옮긴 글은 국문으로 보인다. {@link CommunityTranslationFiller} 가 곧 채운다.
 */
@Service
@RequiredArgsConstructor
public class CommunityTextResolver {

    private final CommunityTranslationRepository translationRepository;

    public record Text(String title, String content) { }

    /** 글 하나만 필요한 경우(상세 화면). */
    public Text forPost(PostDto post, String lang) {
        return forPosts(List.of(post), lang).get(post.getPostId());
    }

    /** 글 여러 개. 번역은 한 번에 가져온다. */
    public Map<Long, Text> forPosts(Collection<PostDto> posts, String lang) {
        Map<Long, Text> result = new HashMap<>();
        if (posts == null || posts.isEmpty()) return result;

        Map<Long, CommunityTranslation> rows = load(CommunityTranslationTarget.POST, lang,
                posts.stream().map(PostDto::getPostId).toList());

        for (PostDto p : posts) {
            CommunityTranslation t = rows.get(p.getPostId());
            result.put(p.getPostId(), new Text(
                    pick(lang, t == null ? null : t.getTitle(), p.getTitleEn(), p.getTitleKo(), p.getTitle()),
                    pick(lang, t == null ? null : t.getContent(), p.getContentEn(), p.getContentKo(), p.getContent())));
        }
        return result;
    }

    /** 댓글. 대댓글까지 한 표에 담아 화면이 깊이를 신경 쓰지 않게 한다. */
    public Map<Long, String> forComments(Collection<CommentDto> comments, String lang) {
        List<CommentDto> flat = new ArrayList<>();
        flatten(comments, flat);

        Map<Long, String> result = new HashMap<>();
        if (flat.isEmpty()) return result;

        Map<Long, CommunityTranslation> rows = load(CommunityTranslationTarget.COMMENT, lang,
                flat.stream().map(CommentDto::getId).toList());

        for (CommentDto c : flat) {
            CommunityTranslation t = rows.get(c.getId());
            result.put(c.getId(), pick(lang, t == null ? null : t.getContent(),
                    c.getContentEn(), c.getContentKo(), c.getContent()));
        }
        return result;
    }

    private static void flatten(Collection<CommentDto> comments, List<CommentDto> into) {
        if (comments == null) return;
        for (CommentDto c : comments) {
            into.add(c);
            flatten(c.getChildren(), into);
        }
    }

    private Map<Long, CommunityTranslation> load(CommunityTranslationTarget type, String lang,
                                                 List<Long> ids) {
        if (!needsTable(lang) || ids.isEmpty()) return Map.of();
        Map<Long, CommunityTranslation> byId = new HashMap<>();
        for (CommunityTranslation t : translationRepository
                .findByTargetTypeAndLanguageAndTargetIdIn(type, lang, ids)) {
            byId.put(t.getTargetId(), t);
        }
        return byId;
    }

    /** 국문과 영문은 글 표 안에서 끝난다. 별도 표가 필요한 건 나머지 언어뿐이다. */
    private static boolean needsTable(String lang) {
        return lang != null && !"ko".equals(lang) && !"en".equals(lang);
    }

    private static String pick(String lang, String translated, String en, String ko, String original) {
        if ("en".equals(lang)) {
            return notBlank(en) ? en : original;
        }
        if (!"ko".equals(lang) && notBlank(translated)) {
            return translated;
        }
        // 국문, 또는 아직 안 옮긴 다른 언어. 영어로 쓴 글이면 국문 번역이 원문 노릇을 한다.
        return notBlank(ko) ? ko : original;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
