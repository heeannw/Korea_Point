package com.example.demo.tourism.service;

import com.example.demo.tourism.entity.TourPlaceTranslation;
import com.example.demo.tourism.repository.TourPlaceTranslationRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 관광 번역표에 없는 이름·주소를 뒤에서 채운다.
 *
 * <p>번역표는 어느 시점의 스냅숏이라, 관광공사에 그 뒤로 올라온 축제·장소는 표에 없다.
 * 그래서 영어 화면에도 이름과 주소가 국문으로 나왔다. 축제는 날짜에 따라 목록이 계속
 * 바뀌니 표를 다시 뽑아도 같은 일이 반복된다.
 *
 * <p>화면이 표에 없는 항목을 만나면 {@link #remember} 로 적어만 두고 국문을 그대로
 * 보여준다. 번역은 몇 초 뒤 이 클래스가 혼자 한다. 화면을 그리는 동안 번역기를 기다리지
 * 않는 게 중요하다. 홈은 이미 관광공사 API 를 여러 번 부르고 있어서, 여기에 번역기까지
 * 걸면 번역기가 느린 날 홈이 통째로 느려진다. 대신 같은 항목을 맨 처음 연 사람 한 명은
 * 국문을 본다.
 *
 * <p>한 번 채운 것은 표에 쌓이므로 다시 번역하지 않는다. 서버를 다시 띄워도 남는다.
 */
@Service
public class TourPlaceTranslationFiller {

    private static final Logger log = LoggerFactory.getLogger(TourPlaceTranslationFiller.class);
    private static final Set<String> SUPPORTED = Set.of("en", "ja", "zh", "fr");

    /** 한 번에 번역기로 보내는 항목 수. 이름과 주소가 따로라 문장 수는 이 값의 두 배가 된다. */
    private static final int BATCH = 20;

    /** 스케줄러 스레드는 하나다. 번역기가 답이 없을 때 예약 쪽 정기 작업까지 멈추면 안 된다. */
    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    /** 채워 둔 이름과 주소. 둘 중 하나만 있을 수도 있다. */
    public record Text(String title, String address) { }

    /** 아직 번역 못 한 것. 이름만 먼저 물어보고 주소는 뒤에 물어봐서 둘을 합쳐 둔다. */
    private record Pending(String contentId, String lang, String koTitle, String koAddress) {
        Pending merge(String title, String address) {
            return new Pending(contentId, lang,
                    koTitle != null ? koTitle : title,
                    koAddress != null ? koAddress : address);
        }
    }

    private final TourPlaceTranslationRepository repository;
    private final RestClient client;

    /** "lang|contentId" 로 찾는다. 화면이 매번 DB 를 두드리지 않게 메모리에 올려 둔다. */
    private final Map<String, Text> cache = new ConcurrentHashMap<>();
    private final Map<String, Pending> queue = new ConcurrentHashMap<>();

    @Value("${translation.api.key:}")
    private String apiKey;

    public TourPlaceTranslationFiller(TourPlaceTranslationRepository repository) {
        this.repository = repository;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    @PostConstruct
    void warmUp() {
        for (TourPlaceTranslation row : repository.findAll()) {
            cache.put(key(row.getLanguage(), row.getContentId()),
                    new Text(row.getTitle(), row.getAddress()));
        }
        if (!cache.isEmpty()) {
            log.info("관광 번역 보충분 {}건 로드", cache.size());
        }
    }

    private static String key(String lang, String contentId) {
        return lang + "|" + contentId;
    }

    /** 채워 둔 번역. 없으면 null 이고, 부르는 쪽은 국문을 그대로 쓴다. */
    public Text find(String lang, String contentId) {
        if (lang == null || contentId == null) return null;
        return cache.get(key(lang.toLowerCase(), contentId));
    }

    /**
     * 표에도 없고 여기에도 없는 항목을 적어 둔다. 이름과 주소가 따로 들어와도 한 줄로 합친다.
     * 화면을 그리는 쪽에서 부르므로 아무것도 기다리지 않는다.
     */
    public void remember(String lang, String contentId, String koTitle, String koAddress) {
        if (lang == null || contentId == null) return;
        String normalized = lang.toLowerCase();
        if (!SUPPORTED.contains(normalized)) return;
        if (koTitle == null && koAddress == null) return;

        String k = key(normalized, contentId);
        if (cache.containsKey(k)) return;
        queue.merge(k, new Pending(contentId, normalized, koTitle, koAddress),
                (old, add) -> old.merge(add.koTitle(), add.koAddress()));
    }

    /**
     * 쌓인 것을 번역해 표에 넣는다.
     *
     * <p>실패하면 큐에 그대로 두고 다음 차례에 다시 해 본다. 한도를 넘겼거나 키가 없으면
     * 계속 실패할 텐데, 그래도 화면은 국문으로 멀쩡히 나오므로 조용히 둔다.
     */
    @Scheduled(fixedDelay = 15_000, initialDelay = 20_000)
    public void drain() {
        if (queue.isEmpty() || apiKey == null || apiKey.isBlank()) return;

        Map<String, List<Pending>> byLang = new HashMap<>();
        for (Pending p : queue.values()) {
            byLang.computeIfAbsent(p.lang(), l -> new ArrayList<>()).add(p);
        }
        byLang.forEach(this::drainOneLanguage);
    }

    private void drainOneLanguage(String lang, List<Pending> pendings) {
        List<Pending> batch = pendings.size() > BATCH ? pendings.subList(0, BATCH) : pendings;

        List<String> texts = new ArrayList<>();
        for (Pending p : batch) {
            texts.add(p.koTitle() == null ? "" : p.koTitle());
            texts.add(p.koAddress() == null ? "" : p.koAddress());
        }

        List<String> translated = translate(texts, lang);
        if (translated == null || translated.size() != texts.size()) return;

        int filled = 0;
        for (int i = 0; i < batch.size(); i++) {
            String title = blankToNull(translated.get(i * 2));
            String address = blankToNull(translated.get(i * 2 + 1));
            if (title == null && address == null) continue;
            save(batch.get(i), title, address);
            filled++;
        }
        if (filled > 0) {
            log.info("관광 번역 보충: {} {}건", lang, filled);
        }
    }

    private void save(Pending p, String title, String address) {
        String k = key(p.lang(), p.contentId());
        try {
            // 같은 항목이 동시에 두 번 떴을 수 있다. 먼저 들어간 게 있으면 그대로 둔다.
            if (!repository.existsByContentIdAndLanguage(p.contentId(), p.lang())) {
                repository.save(new TourPlaceTranslation(p.contentId(), p.lang(), title, address));
            }
            cache.put(k, new Text(title, address));
            queue.remove(k);
        } catch (Exception e) {
            log.warn("관광 번역 보충분을 넣지 못했다 ({} {}): {}", p.lang(), p.contentId(), e.getMessage());
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /** 여러 문장을 한 번에 보낸다. 순서는 보낸 대로 돌아온다. 실패하면 null. */
    private List<String> translate(List<String> texts, String lang) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        texts.forEach(t -> form.add("text", t));
        form.add("source_lang", "KO");
        form.add("target_lang", lang.toUpperCase());
        try {
            Map<?, ?> body = client.post()
                    .uri("https://api-free.deepl.com/v2/translate")
                    .header(HttpHeaders.AUTHORIZATION, "DeepL-Auth-Key " + apiKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            if (body == null || !(body.get("translations") instanceof List<?> list)) return null;
            List<String> out = new ArrayList<>(list.size());
            for (Object o : list) {
                out.add(o instanceof Map<?, ?> m && m.get("text") != null ? m.get("text").toString() : null);
            }
            return out;
        } catch (Exception e) {
            log.warn("관광 번역 보충 실패 ({}): {}", lang, e.getMessage());
            return null;
        }
    }
}
