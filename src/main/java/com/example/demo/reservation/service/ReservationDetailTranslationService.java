package com.example.demo.reservation.service;

import com.example.demo.reservation.entity.ReservationPlaceTranslation;
import com.example.demo.reservation.entity.ReservationTranslationStatus;
import com.example.demo.reservation.repository.ReservationPlaceTranslationRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationDetailTranslationService {

    private static final TypeReference<LinkedHashMap<String, String>> STRING_MAP = new TypeReference<>() { };
    private final ReservationPlaceTranslationRepository repository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = deepLRestTemplate();

    @Value("${translation.api.key:}")
    private String apiKey;

    public TranslationResult translate(String contentId, String lang, String overview,
                                       Map<String, String> operatingInformation) {
        if (isKorean(lang) || allBlank(overview, operatingInformation)) {
            return new TranslationResult(overview, operatingInformation);
        }

        String language = normalizeLanguage(lang);
        String sourceHash = sourceHash(overview, operatingInformation);
        Optional<ReservationPlaceTranslation> cached = repository.findByContentIdAndLanguage(contentId, language);
        if (cached.isPresent() && cached.get().getStatus() == ReservationTranslationStatus.COMPLETED
                && sourceHash.equals(cached.get().getSourceHash())) {
            return resultOf(cached.get(), overview, operatingInformation);
        }

        ReservationPlaceTranslation entry = cached.orElseGet(ReservationPlaceTranslation::new);
        entry.setContentId(contentId);
        entry.setLanguage(language);
        entry.setSourceHash(sourceHash);
        entry.setStatus(ReservationTranslationStatus.PENDING);
        entry.setOverview(overview);
        writeOperatingInformation(entry, operatingInformation);
        repository.save(entry);

        if (tryTranslate(entry)) {
            return resultOf(entry, overview, operatingInformation);
        }
        return new TranslationResult(overview, operatingInformation);
    }

    @Scheduled(fixedDelayString = "${reservation.translation.retry-delay-ms:300000}")
    public void retryPendingTranslations() {
        repository.findTop20ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                        ReservationTranslationStatus.PENDING, LocalDateTime.now())
                .forEach(this::tryTranslate);
    }

    private synchronized boolean tryTranslate(ReservationPlaceTranslation entry) {
        if (apiKey == null || apiKey.isBlank()) {
            markForRetry(entry);
            return false;
        }
        try {
            Map<String, String> sourceDetails = readOperatingInformation(entry.getOperatingInformation());
            List<String> source = new ArrayList<>();
            boolean hasOverview = entry.getOverview() != null && !entry.getOverview().isBlank();
            if (hasOverview) source.add(entry.getOverview());
            source.addAll(sourceDetails.values());

            List<String> translated = callDeepL(source, entry.getLanguage());
            if (translated.size() != source.size()) throw new IllegalStateException("DeepL translation count mismatch");

            int index = 0;
            if (hasOverview) entry.setOverview(translated.get(index++));
            Map<String, String> translatedDetails = new LinkedHashMap<>();
            for (String key : sourceDetails.keySet()) translatedDetails.put(key, translated.get(index++));
            writeOperatingInformation(entry, translatedDetails);
            entry.setStatus(ReservationTranslationStatus.COMPLETED);
            entry.setRetryCount(0);
            entry.setNextRetryAt(null);
            repository.save(entry);
            return true;
        } catch (Exception e) {
            log.warn("Reservation detail translation failed: contentId={}, lang={}",
                    entry.getContentId(), entry.getLanguage(), e);
            markForRetry(entry);
            return false;
        }
    }

    private List<String> callDeepL(List<String> texts, String lang) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "DeepL-Auth-Key " + apiKey);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        texts.forEach(text -> body.add("text", text));
        body.add("source_lang", "KO");
        body.add("target_lang", deepLTarget(lang));
        body.add("tag_handling", "html");

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(
                "https://api-free.deepl.com/v2/translate",
                new HttpEntity<>(body, headers), Map.class);
        if (response == null || !(response.get("translations") instanceof List<?> translations)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object value : translations) {
            if (value instanceof Map<?, ?> item && item.get("text") != null) {
                result.add(item.get("text").toString());
            }
        }
        return result;
    }

    private static RestTemplate deepLRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(15_000);
        return new RestTemplate(factory);
    }

    private void markForRetry(ReservationPlaceTranslation entry) {
        int retries = entry.getRetryCount() + 1;
        entry.setRetryCount(retries);
        entry.setStatus(ReservationTranslationStatus.PENDING);
        entry.setNextRetryAt(LocalDateTime.now().plusMinutes(Math.min(60, 1L << Math.min(retries, 6))));
        repository.save(entry);
    }

    private TranslationResult resultOf(ReservationPlaceTranslation entry, String fallbackOverview,
                                       Map<String, String> fallbackDetails) {
        Map<String, String> details = readOperatingInformation(entry.getOperatingInformation());
        return new TranslationResult(blankToFallback(entry.getOverview(), fallbackOverview),
                details.isEmpty() ? fallbackDetails : details);
    }

    private void writeOperatingInformation(ReservationPlaceTranslation entry, Map<String, String> details) {
        try {
            entry.setOperatingInformation(objectMapper.writeValueAsString(details == null ? Map.of() : details));
        } catch (Exception e) {
            entry.setOperatingInformation("{}");
        }
    }

    private Map<String, String> readOperatingInformation(String json) {
        try {
            return json == null || json.isBlank() ? new LinkedHashMap<>() : objectMapper.readValue(json, STRING_MAP);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private String sourceHash(String overview, Map<String, String> details) {
        try {
            String source = Objects.toString(overview, "") + "\n" + objectMapper.writeValueAsString(details);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not hash reservation translation source", e);
        }
    }

    private boolean allBlank(String overview, Map<String, String> details) {
        return (overview == null || overview.isBlank()) && (details == null || details.isEmpty());
    }

    private boolean isKorean(String lang) { return lang == null || lang.toLowerCase().startsWith("ko"); }
    private String normalizeLanguage(String lang) { return lang.toLowerCase().startsWith("zh") ? "zh" : lang.toLowerCase(); }
    private String deepLTarget(String lang) { return "zh".equals(lang) ? "ZH" : lang.toUpperCase(); }
    private String blankToFallback(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }

    public record TranslationResult(String overview, Map<String, String> operatingInformation) { }
}
