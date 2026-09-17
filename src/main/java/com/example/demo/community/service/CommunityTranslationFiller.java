package com.example.demo.community.service;

import com.example.demo.community.entity.Comment;
import com.example.demo.community.entity.CommunityTranslation;
import com.example.demo.community.entity.CommunityTranslationTarget;
import com.example.demo.community.entity.Post;
import com.example.demo.community.repository.CommentRepository;
import com.example.demo.community.repository.CommunityTranslationRepository;
import com.example.demo.community.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 글과 댓글을 일본어·중국어·불어로 옮겨 쌓는다.
 *
 * <p>화면을 그리는 동안 번역기를 기다리지 않는다. 아직 안 옮긴 글은 국문 그대로 보이고,
 * 몇 초 뒤 이 작업이 채운다. 목록 한 장에 글이 열 개라 화면에서 부르면 번역기를 열 번
 * 기다리게 되고, 번역기가 느린 날 커뮤니티가 통째로 느려진다.
 *
 * <p>할 일 목록을 따로 두지 않고 표를 그대로 쓴다. "번역 줄이 없는 글"을 찾아 채우는
 * 식이라 서버를 다시 띄워도 이어지고, 새 글이 올라오면 다음 차례에 저절로 잡힌다.
 * 처음 올린 뒤에는 쌓여 있는 글을 차례로 훑어 채운다.
 *
 * <p>영문은 여기서 다루지 않는다. 글 표에 칸이 있고 글을 올리는 순간
 * {@link TranslationService} 가 채운다.
 */
@Service
@RequiredArgsConstructor
public class CommunityTranslationFiller {

    private static final Logger log = LoggerFactory.getLogger(CommunityTranslationFiller.class);

    /** 국문에서 이 세 언어로 옮긴다. */
    private static final List<String> LANGUAGES = List.of("ja", "zh", "fr");

    /** 한 번에 옮기는 글 수. 글은 제목과 본문이 따로라 보내는 문장은 이 값의 두 배가 된다. */
    private static final int POST_BATCH = 15;
    private static final int COMMENT_BATCH = 25;

    /**
     * 이보다 긴 글은 건너뛴다. 번역기 요청 크기에 한도가 있고, 이런 글은 대개
     * 본문에 사진이 통째로 박힌 경우다. 건너뛴 것도 빈 줄을 남겨 다시 붙잡지 않는다.
     */
    private static final int MAX_CHARS = 20_000;

    /** 정기 작업 스레드는 앱이 하나를 같이 쓴다. 번역기가 답이 없어도 다른 일이 멈추면 안 된다. */
    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final int READ_TIMEOUT_MS = 20_000;

    private final CommunityTranslationRepository translationRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    /** 지명을 늘 같은 이름으로 옮기게 하는 용어집. */
    private final PlaceNameGlossary placeNames;

    @Value("${translation.api.key:}")
    private String apiKey;

    private RestClient client;

    private RestClient client() {
        if (client == null) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
            factory.setReadTimeout(READ_TIMEOUT_MS);
            client = RestClient.builder().requestFactory(factory).build();
        }
        return client;
    }

    /** 어느 언어 차례인지. 그 언어에 할 일이 없을 때만 다음으로 넘어간다. */
    private int turn = 0;

    /**
     * 아직 안 옮긴 것을 조금씩 채운다.
     *
     * <p>한 차례에 한 언어만 다룬다. 지명 용어집을 무료 계정에서는 하나만 둘 수 있어
     * 언어를 바꿀 때마다 갈아 끼워야 하기 때문이다. 언어를 돌아가며 맡으므로 새 글
     * 하나가 세 언어를 다 갖추는 데 마흔몇 초 걸린다.
     *
     * <p>글이 남아 있으면 글을, 다 됐으면 댓글을 손댄다. 실패하면 아무것도 남기지 않아서
     * 다음 차례에 같은 것을 다시 만난다.
     */
    @Scheduled(fixedDelay = 15_000, initialDelay = 30_000)
    public void fill() {
        if (apiKey == null || apiKey.isBlank()) return;
        for (int i = 0; i < LANGUAGES.size(); i++) {
            String lang = LANGUAGES.get(Math.floorMod(turn, LANGUAGES.size()));
            try {
                // 할 일이 남아 있는 동안은 같은 언어에 머문다. 언어를 바꿀 때마다 용어집을
                // 갈아 끼워야 해서, 오가며 하면 번역기에 용어집 만들기를 수십 번 조른다.
                if (fillPosts(lang) > 0 || fillComments(lang) > 0) return;
            } catch (Exception e) {
                log.warn("커뮤니티 번역 보충 실패 ({}): {}", lang, e.getMessage());
                return;
            }
            turn++;   // 이 언어는 다 됐다
        }
    }

    private int fillPosts(String lang) {
        List<Long> ids = translationRepository.findPostIdsMissing(lang, PageRequest.of(0, POST_BATCH));
        if (ids.isEmpty()) return 0;

        List<Post> posts = postRepository.findAllById(ids);
        List<String> texts = new ArrayList<>();
        List<Post> asked = new ArrayList<>();
        for (Post p : posts) {
            String title = korean(p.getTitleKo(), p.getTitle());
            String content = TranslationService.forTranslation(korean(p.getContentKo(), p.getContent()));
            if (tooLong(title) || tooLong(content)) {
                save(CommunityTranslationTarget.POST, p.getId(), lang, null, null);
                continue;
            }
            asked.add(p);
            texts.add(nullToEmpty(title));
            texts.add(nullToEmpty(content));
        }
        if (asked.isEmpty()) return ids.size();

        List<String> out = translate(texts, lang);
        if (out == null || out.size() != texts.size()) return 0;

        for (int i = 0; i < asked.size(); i++) {
            // 제목은 화면에 글자 그대로 찍힌다. 본문은 HTML 로 찍히니 그대로 둔다.
            save(CommunityTranslationTarget.POST, asked.get(i).getId(), lang,
                    plain(out.get(i * 2)), blankToNull(out.get(i * 2 + 1)));
        }
        log.info("커뮤니티 번역 보충: {} 글 {}건", lang, asked.size());
        return asked.size();
    }

    private int fillComments(String lang) {
        List<Long> ids = translationRepository.findCommentIdsMissing(lang, PageRequest.of(0, COMMENT_BATCH));
        if (ids.isEmpty()) return 0;

        List<Comment> comments = commentRepository.findAllById(ids);
        List<String> texts = new ArrayList<>();
        List<Comment> asked = new ArrayList<>();
        for (Comment c : comments) {
            String content = TranslationService.forTranslation(korean(c.getContentKo(), c.getContent()));
            if (tooLong(content)) {
                save(CommunityTranslationTarget.COMMENT, c.getId(), lang, null, null);
                continue;
            }
            asked.add(c);
            texts.add(nullToEmpty(content));
        }
        if (asked.isEmpty()) return ids.size();

        List<String> out = translate(texts, lang);
        if (out == null || out.size() != texts.size()) return 0;

        for (int i = 0; i < asked.size(); i++) {
            save(CommunityTranslationTarget.COMMENT, asked.get(i).getId(), lang, null, plain(out.get(i)));
        }
        log.info("커뮤니티 번역 보충: {} 댓글 {}건", lang, asked.size());
        return asked.size();
    }

    /**
     * 옮길 원문. 영어로 쓴 글은 국문 번역이 따로 있으니 그걸 원문으로 삼는다.
     * 번역기를 두 번 거치는 셈이지만, 영어 원문에서 다시 옮기는 것보다 국문 하나를
     * 기준으로 두는 편이 세 언어가 서로 어긋나지 않는다.
     */
    private static String korean(String ko, String original) {
        return (ko != null && !ko.isBlank()) ? ko : original;
    }

    private static boolean tooLong(String s) {
        return s != null && s.length() > MAX_CHARS;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /**
     * 화면에 글자 그대로 찍히는 자리(제목, 댓글)를 다듬는다.
     *
     * <p>태그를 살리려고 tag_handling=html 로 보내면 번역기가 따옴표를 {@code &#x27;} 같은
     * 형태로 돌려준다. 본문은 HTML 로 찍혀서 제대로 보이지만, 제목과 댓글은 글자 그대로
     * 찍혀서 불어 제목이 {@code C&#x27;est} 로 보인다.
     */
    private static String plain(String s) {
        String t = blankToNull(s);
        return t == null ? null : HtmlUtils.htmlUnescape(t);
    }

    private void save(CommunityTranslationTarget type, Long id, String lang, String title, String content) {
        try {
            // 같은 것을 동시에 두 번 집었을 수 있다. 먼저 들어간 게 있으면 그대로 둔다.
            if (!translationRepository.existsByTargetTypeAndTargetIdAndLanguage(type, id, lang)) {
                translationRepository.save(new CommunityTranslation(type, id, lang, title, content));
            }
        } catch (Exception e) {
            log.warn("커뮤니티 번역을 넣지 못했다 ({} {} {}): {}", type, id, lang, e.getMessage());
        }
    }

    /**
     * 여러 문장을 한 번에 보낸다. 순서는 보낸 대로 돌아온다. 실패하면 null.
     *
     * <p>지명이 들어 있는데 용어집을 못 얻었으면 아무것도 하지 않고 물러난다. 그대로
     * 옮기면 이기대가 伊基代, 다대포가 大代浦 처럼 나오고, 한 번 저장되면 누가 다시
     * 들여다보기 전까지 그대로 남는다. 늦게 채우는 편이 낫다.
     */
    private List<String> translate(List<String> texts, String lang) {
        String glossary = placeNames.idFor(lang);
        if (glossary == null && texts.stream().anyMatch(placeNames::mentions)) {
            log.warn("지명 용어집을 못 얻어 이번 차례는 건너뛴다 ({})", lang);
            return null;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        texts.forEach(t -> form.add("text", t));
        form.add("source_lang", "KO");
        form.add("target_lang", lang.toUpperCase());
        form.add("tag_handling", "html");   // Quill 에디터가 남긴 태그를 살린다
        if (glossary != null) form.add("glossary_id", glossary);
        try {
            Map<?, ?> body = client().post()
                    .uri("https://api-free.deepl.com/v2/translate")
                    .header(HttpHeaders.AUTHORIZATION, "DeepL-Auth-Key " + apiKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            if (body == null || !(body.get("translations") instanceof List<?> list)) return null;
            List<String> out = new ArrayList<>(list.size());
            for (Object o : list) {
                out.add(o instanceof Map<?, ?> m && m.get("text") != null
                        ? placeNames.tidy(lang, m.get("text").toString()) : null);
            }
            return out;
        } catch (Exception e) {
            log.warn("커뮤니티 번역 요청 실패 ({}): {}", lang, e.getMessage());
            return null;
        }
    }
}
