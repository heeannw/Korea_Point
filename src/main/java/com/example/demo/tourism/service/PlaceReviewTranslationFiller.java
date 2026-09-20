package com.example.demo.tourism.service;

import com.example.demo.community.service.PlaceNameGlossary;
import com.example.demo.tourism.entity.PlaceReview;
import com.example.demo.tourism.entity.PlaceReviewTranslation;
import com.example.demo.tourism.repository.PlaceReviewRepository;
import com.example.demo.tourism.repository.PlaceReviewTranslationRepository;
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
import java.util.regex.Pattern;

/**
 * 방문 리뷰를 다섯 언어로 옮겨 쌓는다.
 *
 * <p>리뷰는 쓴 사람의 언어 그대로 저장한다. 일본어로 쓴 리뷰가 한국어 화면에 일본어로
 * 남아 있으면 읽을 수 없어서, 보는 언어로 옮긴 문장을 미리 만들어 둔다.
 *
 * <p>화면을 그리는 동안 번역기를 기다리지 않는다. 아직 안 옮긴 리뷰는 원문 그대로 보이고
 * 잠시 뒤 이 작업이 채운다. 한 장소에 리뷰가 열 개씩 달리므로, 화면에서 번역기를 부르면
 * 번역기가 느린 날 리뷰 칸이 통째로 늦게 뜬다.
 *
 * <p>커뮤니티 글({@code CommunityTranslationFiller})과 같은 방식이되, 원문 언어가 한국어로
 * 정해져 있지 않다는 점이 다르다. 한글이 섞인 리뷰는 국문으로 보고 지명 용어집을 함께
 * 쓰고, 그 밖의 리뷰는 번역기가 원문 언어를 알아서 판별하게 둔다.
 */
@Service
@RequiredArgsConstructor
public class PlaceReviewTranslationFiller {

    private static final Logger log = LoggerFactory.getLogger(PlaceReviewTranslationFiller.class);

    /** 사이트가 지원하는 언어. 리뷰는 이 중 한 언어로 쓰여 있고, 나머지로 옮긴다. */
    private static final List<String> LANGUAGES = List.of("ko", "en", "ja", "zh", "fr");

    /** 한 번에 옮기는 리뷰 수. 리뷰는 평균 40자 안팎이라 글보다 넉넉히 잡는다. */
    private static final int BATCH = 25;

    /** 이보다 긴 리뷰는 건너뛴다. 리뷰 칸 자체가 1000자 제한이라 사실상 걸리지 않는다. */
    private static final int MAX_CHARS = 1_000;

    private static final Pattern HANGUL = Pattern.compile("[가-힣]");

    /** 정기 작업 스레드는 앱이 하나를 같이 쓴다. 번역기가 답이 없어도 다른 일이 멈추면 안 된다. */
    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final int READ_TIMEOUT_MS = 20_000;

    private final PlaceReviewRepository reviewRepository;
    private final PlaceReviewTranslationRepository translationRepository;
    /** 지명을 늘 같은 이름으로 옮기게 하는 용어집. 국문 리뷰에만 쓴다. */
    private final PlaceNameGlossary placeNames;

    @Value("${translation.api.key:}")
    private String apiKey;

    /**
     * 배포 서버에서만 켠다. 팀원 각자의 컴퓨터에서 켜져 있으면 같은 리뷰를 사람 수만큼
     * 반복해서 옮겨 번역기 한도를 헛되이 쓴다. 꺼져 있으면 리뷰가 원문 그대로 보인다.
     */
    @Value("${translation.fill.enabled:false}")
    private boolean enabled;

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
     * 아직 안 옮긴 리뷰를 조금씩 채운다.
     *
     * <p>한 차례에 한 언어만 다룬다. 지명 용어집을 무료 계정에서는 하나만 둘 수 있어
     * 언어를 바꿀 때마다 갈아 끼워야 하기 때문이다. 실패하면 아무것도 남기지 않아서
     * 다음 차례에 같은 리뷰를 다시 만난다.
     */
    @Scheduled(fixedDelay = 15_000, initialDelay = 40_000)
    public void fill() {
        if (!enabled || apiKey == null || apiKey.isBlank()) return;
        for (int i = 0; i < LANGUAGES.size(); i++) {
            String lang = LANGUAGES.get(Math.floorMod(turn, LANGUAGES.size()));
            try {
                if (fillReviews(lang) > 0) return;
            } catch (Exception e) {
                log.warn("리뷰 번역 보충 실패 ({}): {}", lang, e.getMessage());
                return;
            }
            turn++;   // 이 언어는 다 됐다
        }
    }

    private int fillReviews(String lang) {
        List<Long> ids = translationRepository.findReviewIdsMissing(lang, PageRequest.of(0, BATCH));
        if (ids.isEmpty()) return 0;

        // 한글이 섞인 리뷰는 국문으로 보고 용어집과 함께 옮긴다. 그 밖은 번역기가 원문 언어를 찾는다.
        List<PlaceReview> korean = new ArrayList<>();
        List<PlaceReview> other = new ArrayList<>();
        for (PlaceReview r : reviewRepository.findAllById(ids)) {
            String text = r.getText();
            if (text == null || text.isBlank() || text.length() > MAX_CHARS) {
                save(r.getId(), lang, null);   // 옮길 것이 없다. 줄만 남겨 다시 붙잡지 않는다.
                continue;
            }
            (HANGUL.matcher(text).find() ? korean : other).add(r);
        }

        int done = 0;
        done += translateGroup(korean, lang, true);
        done += translateGroup(other, lang, false);
        if (done > 0) log.info("리뷰 번역 보충: {} {}건", lang, done);
        return done > 0 ? done : ids.size();
    }

    /**
     * 한 무리를 한 번에 보낸다.
     *
     * @param sourceKorean 원문이 국문임이 분명하면 true. 번역기에 원문 언어를 알려주고
     *                     지명 용어집도 함께 보낸다.
     */
    private int translateGroup(List<PlaceReview> reviews, String lang, boolean sourceKorean) {
        if (reviews.isEmpty()) return 0;

        // 이미 그 언어로 쓰인 리뷰는 옮길 필요가 없다.
        if (sourceKorean && "ko".equals(lang)) {
            reviews.forEach(r -> save(r.getId(), lang, null));
            return reviews.size();
        }

        List<String> texts = reviews.stream().map(PlaceReview::getText).toList();
        List<Translated> out = translate(texts, lang, sourceKorean);
        if (out == null || out.size() != texts.size()) return 0;

        for (int i = 0; i < reviews.size(); i++) {
            Translated t = out.get(i);
            // 원문이 이미 그 언어였으면 옮긴 문장을 담지 않는다. 화면은 원문을 그대로 보여준다.
            boolean same = t.detectedSource() != null && t.detectedSource().equalsIgnoreCase(lang);
            save(reviews.get(i).getId(), lang, same ? null : blankToNull(t.text()));
        }
        return reviews.size();
    }

    /** 번역기 응답 한 줄. 원문 언어를 함께 돌려받아야 "옮길 필요 없음"을 가린다. */
    private record Translated(String text, String detectedSource) { }

    /**
     * 여러 문장을 한 번에 보낸다. 순서는 보낸 대로 돌아온다. 실패하면 null.
     *
     * <p>국문 리뷰에 지명이 들어 있는데 용어집을 못 얻었으면 물러난다. 그대로 옮기면
     * 이기대가 伊基代처럼 나오고, 한 번 저장되면 누가 다시 들여다보기 전까지 남는다.
     */
    private List<Translated> translate(List<String> texts, String lang, boolean sourceKorean) {
        String glossary = null;
        if (sourceKorean) {
            glossary = placeNames.idFor(lang);
            if (glossary == null && texts.stream().anyMatch(placeNames::mentions)) {
                log.warn("지명 용어집을 못 얻어 이번 차례는 건너뛴다 ({})", lang);
                return null;
            }
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        texts.forEach(t -> form.add("text", t));
        if (sourceKorean) form.add("source_lang", "KO");   // 아니면 번역기가 원문 언어를 찾는다
        form.add("target_lang", lang.toUpperCase());
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
            List<Translated> out = new ArrayList<>(list.size());
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m) || m.get("text") == null) {
                    out.add(new Translated(null, null));
                    continue;
                }
                String text = placeNames.tidy(lang, HtmlUtils.htmlUnescape(m.get("text").toString()));
                Object detected = m.get("detected_source_language");
                out.add(new Translated(text, detected == null ? null : detected.toString()));
            }
            return out;
        } catch (Exception e) {
            log.warn("리뷰 번역 요청 실패 ({}): {}", lang, e.getMessage());
            return null;
        }
    }

    private void save(Long reviewId, String lang, String text) {
        try {
            // 같은 것을 동시에 두 번 집었을 수 있다. 먼저 들어간 게 있으면 그대로 둔다.
            if (!translationRepository.existsByReviewIdAndLanguage(reviewId, lang)) {
                translationRepository.save(new PlaceReviewTranslation(reviewId, lang, text));
            }
        } catch (Exception e) {
            log.warn("리뷰 번역을 넣지 못했다 ({} {}): {}", reviewId, lang, e.getMessage());
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
