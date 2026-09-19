package com.example.demo.tourism.config;

import com.example.demo.tourism.dto.TourPlaceDto;
import com.example.demo.tourism.service.TourPlaceTranslationFiller;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 관광 목록·상세는 항상 KorService2(국문)에서 받고, 국문 외 언어는 여기서 이름·주소만 바꿔 끼운다.
 *
 * <p>언어별 공식 서비스(Eng/Jpn/Chs/Fre)를 서버가 직접, 주기적으로 불러 기준표를 만든다.
 * 예전엔 이 표를 사람이 빌드 스크립트로 미리 받아 파일로 커밋해 뒀다
 * ({@code scripts/tourism/fetch_tour_i18n.py}, 삭제됨).</p>
 *
 * <p><b>맞추는 열쇠는 contentId가 아니라 국문 이름이다.</b> 언어별 서비스는 국문과 contentId
 * 체계가 완전히 달라서(부산 1000건씩 맞춰 본 교집합 0건) 번호로는 한 건도 못 찾는다. 대신
 * 외국어 제목 끝에 국문 원문이 괄호로 붙어 온다 — {@code "Haeundae Beach (해운대해수욕장)"}.
 * 그 괄호 안을 열쇠로 쓰면 부산 2,223곳 중 영어 1,036곳·일본어 1,038곳·중국어 990곳이
 * 공사 공식 표기로 맞아떨어진다(프랑스어는 공사가 101곳만 제공해 78곳).</p>
 *
 * <p>표에 없는 나머지는 {@link TourPlaceTranslationFiller}가 DeepL로 채운다(이미 실시간이고
 * DB에 영속돼 있다). 공사 표로 채운 쪽은 DeepL에 다시 묻지 않는다.</p>
 */
@Component
public class TourPlaceI18n {

    private static final Logger log = LoggerFactory.getLogger(TourPlaceI18n.class);
    private static final long REFRESH_MS = 24 * 60 * 60_000L; // 하루
    /** ko 는 기준 언어라 오버레이가 없다. */
    private static final List<String> LANGS = List.of("en", "ja", "zh", "fr");
    private static final int MAX_PAGES = 10;

    /** {@code "KOLON SEACLOUD HOTEL / 코오롱씨클라우드호텔"} 처럼 빗금으로 붙여 온 것. */
    private static final Pattern KO_AFTER_SLASH =
            Pattern.compile("/\\s*([^/]*[가-힣][^/]*)\\s*$");
    /**
     * {@code "广安里海水浴场광안리해수욕장"} 처럼 구분자 없이 국문을 그냥 뒤에 붙여 온 것.
     * 중국어 목록에 열 줄쯤 있는데 광안리해수욕장·자갈치시장 같은 주요 장소가 끼어 있다.
     */
    private static final Pattern KO_AT_END = Pattern.compile("([가-힣][가-힣\\s]*)\\s*$");
    private static final Pattern HANGUL = Pattern.compile("[가-힣]");
    /**
     * 이름에 붙은 대괄호 표시. 영어는 이름 뒤에({@code "... [Tax Refund Shop]"}),
     * 일본어·중국어는 이름 앞에({@code "[事後免税店]..."}) 붙여 온다. 부산 영어 목록
     * 1130곳 중 895곳이 사후면세점 표시를 달고 있어 그대로 두면 화면이 지저분해진다.
     */
    private static final Pattern LEADING_TAG = Pattern.compile("^\\s*\\[[^\\[\\]]*\\]\\s*");
    private static final Pattern TRAILING_TAG = Pattern.compile("\\s*\\[[^\\[\\]]*\\]\\s*$");
    /** 띄어쓰기·괄호·가운뎃점 차이로 같은 이름을 못 알아보는 일을 막는다. */
    private static final Pattern NORM_STRIP = Pattern.compile("[\\s()\\[\\]/·・\\-_,'’.]");

    /** 다듬은 국문 이름 -> {"title": "...", "addr": "..."} */
    private record Entry(String title, String addr) { }

    private final TourApiProperties properties;
    private final ObjectMapper objectMapper;
    private final TourPlaceTranslationFiller filler;
    private final RestClient restClient = RestClient.create();
    private volatile Map<String, Map<String, Entry>> byLang = Map.of();
    /**
     * contentId -> 국문 이름. 목록을 그릴 때 이름과 함께 들어온 것을 적어 둔다.
     * 이름 없이 번호만 들고 오는 호출({@link #findTitle}, {@link #translateAddress})도
     * 공사 표를 쓸 수 있게 하려고 둔다. 부산 관광지 수만큼만 쌓인다.
     */
    private final Map<String, String> koTitleById = new ConcurrentHashMap<>();

    public TourPlaceI18n(TourApiProperties properties, ObjectMapper objectMapper, TourPlaceTranslationFiller filler) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.filler = filler;
    }

    @PostConstruct
    @Scheduled(fixedRate = REFRESH_MS)
    void refresh() {
        if (properties.getServiceKey() == null || properties.getServiceKey().isBlank()) return;
        Map<String, Map<String, Entry>> loaded = new LinkedHashMap<>();
        for (String lang : LANGS) {
            try {
                Map<String, Entry> table = fetchAreaList(properties.baseUrlFor(lang));
                if (!table.isEmpty()) {
                    loaded.put(lang, table);
                    log.info("관광 번역 오버레이 로드: {} ({}건)", lang, table.size());
                }
            } catch (Exception e) {
                log.warn("관광 번역 오버레이 갱신 실패 [{}]: {}", lang, e.getMessage());
            }
        }
        // 전부 실패해도 이전에 성공한 표를 그대로 둔다(비었으면 필러가 국문 채운 채로 물려받는다).
        if (!loaded.isEmpty()) byLang = loaded;
    }

    /** 부산 areaBasedList2 전체를 언어별 서비스에서 받는다. 다듬은 국문 이름 -> {title, addr}. */
    private Map<String, Entry> fetchAreaList(String baseUrl) {
        // 괄호 안 국문을 그대로 쓴 열쇠. 가장 믿을 만하다.
        Map<String, Entry> out = new LinkedHashMap<>();
        // 국문이 달고 있던 부연을 한 겹 벗긴 열쇠("이기대 (부산 국가지질공원)" -> "이기대").
        // 확실한 열쇠가 이미 잡은 자리는 건드리지 않고, 빈 자리만 나중에 채운다.
        Map<String, Entry> stripped = new LinkedHashMap<>();
        // 같은 국문 이름에 서로 다른 번역이 달려 오면 어느 쪽이 맞는지 알 수 없다 — 통째로 버린다.
        Set<String> ambiguous = new HashSet<>();
        Set<String> strippedAmbiguous = new HashSet<>();
        int pageSize = Math.max(1, properties.getDefaultRows());
        for (int page = 1; page <= MAX_PAGES; page++) {
            URI uri = UriComponentsBuilder.fromUriString(baseUrl + "/areaBasedList2")
                    .queryParam("serviceKey", properties.getServiceKey())
                    .queryParam("MobileOS", properties.getMobileOs())
                    .queryParam("MobileApp", properties.getMobileApp())
                    .queryParam("_type", "json")
                    .queryParam("numOfRows", pageSize)
                    .queryParam("pageNo", page)
                    .queryParam("lDongRegnCd", properties.getBusanLegalDongCode())
                    .queryParam("arrange", "Q")
                    .build().encode().toUri();
            JsonNode body = requestBody(uri);
            addItems(out, stripped, ambiguous, strippedAmbiguous, body.path("items").path("item"));
            if ((long) page * pageSize >= body.path("totalCount").asLong(0)) break;
        }
        ambiguous.forEach(out::remove);
        strippedAmbiguous.forEach(stripped::remove);
        stripped.forEach(out::putIfAbsent);
        return out;
    }

    private JsonNode requestBody(URI uri) {
        String raw = restClient.get().uri(uri).retrieve().body(String.class);
        JsonNode root;
        try {
            root = objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new RuntimeException("번역 오버레이 응답 파싱 실패", e);
        }
        String resultCode = root.path("response").path("header").path("resultCode").asText();
        if (!"0000".equals(resultCode)) {
            throw new RuntimeException("TourAPI 오류 " + resultCode + ": "
                    + root.path("response").path("header").path("resultMsg").asText());
        }
        return root.path("response").path("body");
    }

    private void addItems(Map<String, Entry> out, Map<String, Entry> stripped,
                          Set<String> ambiguous, Set<String> strippedAmbiguous, JsonNode items) {
        if (items.isArray()) {
            for (JsonNode item : items) addItem(out, stripped, ambiguous, strippedAmbiguous, item);
        } else if (items.isObject()) {
            addItem(out, stripped, ambiguous, strippedAmbiguous, items);
        }
    }

    private void addItem(Map<String, Entry> out, Map<String, Entry> stripped,
                         Set<String> ambiguous, Set<String> strippedAmbiguous, JsonNode item) {
        String title = text(item, "title");
        if (title == null) return;
        Split split = splitTitle(title);
        if (split == null) return;

        String addr1 = text(item, "addr1");
        String addr2 = text(item, "addr2");
        String addr = addr1 == null ? addr2 : (addr2 == null ? addr1 : addr1 + " " + addr2);
        put(out, ambiguous, norm(split.koreanName()), new Entry(split.foreignTitle(), addr));
        if (split.strippedName() != null) {
            put(stripped, strippedAmbiguous, norm(split.strippedName()),
                    new Entry(split.strippedForeignTitle(), addr));
        }
    }

    /**
     * 외국어 제목을 번역 부분과 국문 원문으로 가른 결과.
     *
     * @param foreignTitle         그 나라 말로 된 이름(대괄호 표시는 떼어낸 것)
     * @param strippedForeignTitle 부연까지 벗긴 짧은 이름. {@code strippedName} 이 있을 때만 쓴다
     * @param koreanName           제목에 붙어 온 국문 원문 그대로
     * @param strippedName         국문이 부연을 달고 있을 때 그걸 벗긴 것. 없으면 null
     */
    record Split(String foreignTitle, String strippedForeignTitle,
                 String koreanName, String strippedName) { }

    /**
     * 관광공사 외국어 제목에서 번역과 국문 원문을 갈라낸다. 국문이 안 붙어 왔으면 null.
     *
     * <p>세 가지 모양으로 온다 — 괄호({@code "Haeundae Beach (해운대해수욕장)"}), 빗금
     * ({@code "KOLON SEACLOUD HOTEL / 코오롱씨클라우드호텔"}), 그리고 구분자 없이 그냥 붙인 것
     * ({@code "广安里海水浴场광안리해수욕장"}).</p>
     */
    static Split splitTitle(String title) {
        if (title == null) return null;

        String[] paren = splitTrailingParen(title);
        if (paren != null) {
            if (paren[0].isEmpty() || !HANGUL.matcher(paren[1]).find()) return null;
            // 국문 쪽이 또 괄호를 달고 있으면 벗긴 것도 열쇠 후보로 둔다.
            // "Igidae Cliff [National Geopark] (이기대 (부산 국가지질공원))" -> "이기대"
            String[] inner = splitTrailingParen(paren[1]);
            String strippedName = (inner != null && !inner[0].isEmpty() && HANGUL.matcher(inner[0]).find())
                    ? inner[0] : null;
            return build(paren[0], paren[1], strippedName);
        }

        Matcher m = KO_AFTER_SLASH.matcher(title);
        if (!m.find()) {
            m = KO_AT_END.matcher(title);
            // 앞쪽에도 한글이 섞여 있으면 어디까지가 번역인지 알 수 없다 — 건드리지 않는다.
            if (!m.find() || HANGUL.matcher(title.substring(0, m.start())).find()) {
                return null;   // 국문 원문이 안 붙어 온 줄은 맞출 방법이 없다
            }
        }
        return build(title.substring(0, m.start()), m.group(1), null);
    }

    /** 이름에서 대괄호 표시를 떼고, 벗긴 열쇠가 있으면 그 짝이 될 짧은 이름도 만들어 둔다. */
    private static Split build(String rawForeign, String koreanName, String strippedName) {
        String foreign = stripTag(rawForeign);
        if (foreign.isEmpty()) return null;   // 표시를 떼고 나니 이름이 안 남는다

        String shortForeign = null;
        if (strippedName != null) {
            // 국문에서 부연을 벗겼으면 이름 쪽에서도 벗긴다 — "二妓台（釜山国家地質公園）" -> "二妓台".
            String[] tail = splitTrailingParen(foreign);
            shortForeign = (tail != null && !tail[0].isEmpty()) ? stripTag(tail[0]) : foreign;
            if (shortForeign.isEmpty()) shortForeign = foreign;
        }
        return new Split(foreign, shortForeign, koreanName, strippedName);
    }

    private static String stripTag(String s) {
        String t = s.trim();
        t = LEADING_TAG.matcher(t).replaceFirst("");
        t = TRAILING_TAG.matcher(t).replaceFirst("");
        return t.trim();
    }

    private static void put(Map<String, Entry> table, Set<String> ambiguous, String key, Entry entry) {
        if (key.isEmpty()) return;
        Entry prev = table.putIfAbsent(key, entry);
        if (prev != null && !entry.title().equals(prev.title())) ambiguous.add(key);
    }

    /**
     * 제목 끝의 짝 맞는 괄호를 갈라 {앞부분, 괄호 안} 을 준다. 괄호가 겹쳐 있어도 바깥 짝을 찾는다
     * — {@code "... (이기대 (부산 국가지질공원))"} 처럼 국문이 또 괄호를 달고 오는 줄이 있다.
     * 끝이 닫는 괄호가 아니거나 짝이 안 맞으면 null.
     */
    private static String[] splitTrailingParen(String title) {
        String t = title.stripTrailing();
        if (t.isEmpty() || !isClose(t.charAt(t.length() - 1))) return null;
        int depth = 0;
        for (int i = t.length() - 1; i >= 0; i--) {
            char c = t.charAt(i);
            if (isClose(c)) {
                depth++;
            } else if (isOpen(c)) {
                depth--;
                if (depth == 0) {
                    return new String[] { t.substring(0, i).trim(), t.substring(i + 1, t.length() - 1).trim() };
                }
            }
        }
        return null;
    }

    // 중국어 두 줄이 대괄호를 국문 구분자로 쓴다 — "松岛云端散步路（天空步道） [송도 구름산책로(스카이워크)]".
    private static boolean isOpen(char c) { return c == '(' || c == '（' || c == '['; }

    private static boolean isClose(char c) { return c == ')' || c == '）' || c == ']'; }

    private static String norm(String s) {
        return s == null ? "" : NORM_STRIP.matcher(s).replaceAll("").toLowerCase();
    }

    private String text(JsonNode node, String field) {
        String v = node.path(field).asText("").trim();
        return v.isEmpty() ? null : v;
    }

    /** 국문 이름으로 공사 표를 찾는다. 이름을 안 받았으면 전에 적어 둔 이름으로 찾아본다. */
    private Entry lookup(String lang, String contentId, String koTitle) {
        if (lang == null) return null;
        Map<String, Entry> table = byLang.get(lang.toLowerCase());
        if (table == null || table.isEmpty()) return null;
        String ko = (koTitle != null && !koTitle.isBlank()) ? koTitle
                : (contentId == null ? null : koTitleById.get(contentId));
        String key = norm(ko);
        return key.isEmpty() ? null : table.get(key);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /**
     * 공사 표에서 찾고, 모자란 쪽만 전에 DeepL로 채워 둔 것에서 찾는다. 그래도 빈 자리가
     * 남으면 그 자리만 보충 대상으로 적어 둔다. 적어 두기만 할 뿐 여기서 번역기를 부르지 않는다.
     *
     * <p>이름은 공사 것으로, 주소는 DeepL로 채우는 식으로 섞일 수 있다. 공사 공식 표기가
     * 기계번역보다 정확하고(예: 서면 → 書面 오역), 채운 만큼 DeepL 한도를 아낀다.</p>
     */
    private Entry resolve(String lang, String contentId, String koTitle, String koAddress) {
        if (contentId != null && koTitle != null && !koTitle.isBlank()) {
            koTitleById.putIfAbsent(contentId, koTitle);
        }

        Entry official = lookup(lang, contentId, koTitle);
        String title = official == null ? null : blankToNull(official.title());
        String addr = official == null ? null : blankToNull(official.addr());

        if (title == null || addr == null) {
            TourPlaceTranslationFiller.Text filled = filler.find(lang, contentId);
            if (filled != null) {
                if (title == null) title = blankToNull(filled.title());
                if (addr == null) addr = blankToNull(filled.address());
            }
        }

        // 아직 빈 자리만 보충 대상으로 넘긴다 — 공사 표로 채운 쪽은 DeepL에 다시 묻지 않는다.
        String needTitle = title == null ? koTitle : null;
        String needAddr = addr == null ? koAddress : null;
        if (needTitle != null || needAddr != null) {
            filler.remember(lang, contentId, needTitle, needAddr);
        }

        return (title == null && addr == null) ? null : new Entry(title, addr);
    }

    /** 국문으로 채워진 DTO의 이름·주소를 요청 언어로 바꿔 끼운다. 번역이 없으면 그대로 둔다. */
    public void apply(String lang, TourPlaceDto place) {
        if (place == null) return;
        Entry e = resolve(lang, place.getId(), place.getTitle(), place.getAddr());
        if (e == null) return;
        if (e.title() != null && !e.title().isBlank()) place.setTitle(e.title());
        if (e.addr() != null && !e.addr().isBlank()) place.setAddr(e.addr());
    }

    /** 상세 패널 제목처럼 문자열 하나만 필요한 경우. 번역이 없으면 국문을 돌려준다. */
    public String translateTitle(String lang, String contentId, String koreanTitle) {
        Entry e = resolve(lang, contentId, koreanTitle, null);
        return (e != null && e.title() != null && !e.title().isBlank()) ? e.title() : koreanTitle;
    }

    /**
     * 번역된 이름을 찾기만 한다. 없으면 null 이고, 보충 대상으로 적어 두지도 않는다.
     *
     * <p>넘겨받은 국문 이름이 그 장소의 이름인지 확신할 수 없을 때 쓴다. 커뮤니티 홍보 글은
     * 가게 번호에 구 이름을 붙여 두는데, 그걸 {@link #translateTitle} 로 넘기면 구 이름이
     * 그 가게의 번역으로 저장된다.
     */
    public String findTitle(String lang, String contentId) {
        Entry e = lookup(lang, contentId, null);
        if (e != null && e.title() != null && !e.title().isBlank()) return e.title();
        if (lang == null || contentId == null) return null;
        TourPlaceTranslationFiller.Text filled = filler.find(lang, contentId);
        return (filled != null && filled.title() != null && !filled.title().isBlank()) ? filled.title() : null;
    }

    /** 주소만 필요한 경우(홈 카드 등). 번역이 없으면 국문을 돌려준다. */
    public String translateAddress(String lang, String contentId, String koreanAddress) {
        Entry e = resolve(lang, contentId, null, koreanAddress);
        return (e != null && e.addr() != null && !e.addr().isBlank()) ? e.addr() : koreanAddress;
    }
}
