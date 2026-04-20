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

@Service
@RequiredArgsConstructor
public class TranslationService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    @Value("${translation.api.key:}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 텍스트를 영어로 번역합니다 (DeepL Free API).
     * API 키가 없거나 이미 영어이거나 번역 실패 시 null 반환 (원문 표시).
     */
    public String translateToEnglish(String text) {
        if (apiKey == null || apiKey.isBlank() || text == null || text.isBlank()) {
            return null;
        }

        // <img> 태그 제거 — Base64 이미지 데이터가 API 글자수를 폭발적으로 소모하기 때문
        String cleaned = text.replaceAll("<img[^>]*>", "").trim();
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

        String cleaned = text.replaceAll("<img[^>]*>", "").trim();
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
