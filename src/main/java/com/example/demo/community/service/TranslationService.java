package com.example.demo.community.service;

import com.example.demo.community.repository.CommentRepository;
import com.example.demo.community.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.HtmlUtils;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class TranslationService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    @Value("${translation.api.key:}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 본문에 박힌 Base64 이미지. {@code <img src="data:image/png;base64,...">} 형태다.
     * Base64 문자에는 {@code >} 가 없어서 태그 끝까지 안전하게 잡힌다.
     */
    private static final Pattern INLINE_IMAGE = Pattern.compile(
            "<img[^>]*src\\s*=\\s*[\"']?\\s*data:[^>]*>", Pattern.CASE_INSENSITIVE);

    /**
     * 번역기에 보낼 본문을 다듬는다.
     *
     * <p>예전에는 {@code <img>} 를 전부 떼고 보냈다. 그런데 번역 결과를 그대로
     * 번역본 칸에 저장하기 때문에, 영어로 보는 사람에게만 사진이 사라졌다.
     * 국문으로 보면 멀쩡해서 오래 눈에 띄지 않았다.
     *
     * <p>실제로 문제가 되는 건 본문에 박히는 Base64 이미지뿐이다. 한 장이 수십만
     * 자라 번역 요청이 통째로 막힌다. 주소로 걸린 사진은 태그가 짧고, DeepL 은
     * {@code tag_handling=html} 에서 태그 안을 건드리지 않으므로 그대로 두면
     * 번역된 본문에도 남는다.
     */
    static String forTranslation(String text) {
        return INLINE_IMAGE.matcher(text).replaceAll("").trim();
    }

    /**
     * 텍스트를 영어로 번역합니다 (DeepL Free API).
     * API 키가 없거나 이미 영어이거나 번역 실패 시 null 반환 (원문 표시).
     */
    public String translateToEnglish(String text) {
        if (apiKey == null || apiKey.isBlank() || text == null || text.isBlank()) {
            return null;
        }

        String cleaned = forTranslation(text);
        if (cleaned.isBlank()) return null;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "DeepL-Auth-Key " + apiKey);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("text", cleaned);
        body.add("target_lang", "EN");
        body.add("tag_handling", "html");  // Quill 에디터 HTML 태그 보존 (img 외)

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    "https://api-free.deepl.com/v2/translate",
                    request,
                    Map.class
            );
            if (response == null) return null;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> translations = (List<Map<String, Object>>) response.get("translations");
            Map<String, Object> first = translations.get(0);

            // 이미 영어로 작성된 게시글은 번역 불필요 → null 반환
            String detectedLang = (String) first.get("detected_source_language");
            if ("EN".equals(detectedLang)) return null;

            return HtmlUtils.htmlUnescape((String) first.get("text"));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 텍스트를 한국어로 번역합니다 (DeepL Free API).
     * API 키가 없거나 이미 한국어이거나 번역 실패 시 null 반환 (원문 표시).
     */
    public String translateToKorean(String text) {
        if (apiKey == null || apiKey.isBlank() || text == null || text.isBlank()) {
            return null;
        }

        String cleaned = forTranslation(text);
        if (cleaned.isBlank()) return null;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "DeepL-Auth-Key " + apiKey);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("text", cleaned);
        body.add("target_lang", "KO");
        body.add("tag_handling", "html");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    "https://api-free.deepl.com/v2/translate",
                    request,
                    Map.class
            );
            if (response == null) return null;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> translations = (List<Map<String, Object>>) response.get("translations");
            Map<String, Object> first = translations.get(0);

            // 이미 한국어로 작성된 경우 번역 불필요 → null 반환
            String detectedLang = (String) first.get("detected_source_language");
            if ("KO".equals(detectedLang)) return null;

            return HtmlUtils.htmlUnescape((String) first.get("text"));
        } catch (Exception e) {
            return null;
        }
    }

    // 게시글 번역 비동기 처리 — EN↔KO 양방향
    @Async
    @Transactional
    public void translatePostAsync(Long postId, String title, String content) {
        String titleEn = translateToEnglish(title);
        String contentEn = translateToEnglish(content);
        String titleKo = translateToKorean(title);
        String contentKo = translateToKorean(content);
        postRepository.findById(postId).ifPresent(p -> {
            p.setTitleEn(titleEn);
            p.setContentEn(contentEn);
            p.setTitleKo(titleKo);
            p.setContentKo(contentKo);
        });
    }

    // 댓글 번역 비동기 처리 — EN↔KO 양방향
    @Async
    @Transactional
    public void translateCommentAsync(Long commentId, String content) {
        String contentEn = translateToEnglish(content);
        String contentKo = translateToKorean(content);
        commentRepository.findById(commentId).ifPresent(c -> {
            c.setContentEn(contentEn);
            c.setContentKo(contentKo);
        });
    }
}
