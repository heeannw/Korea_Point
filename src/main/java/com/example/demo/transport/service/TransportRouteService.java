package com.example.demo.transport.service;

import com.example.demo.transport.dto.TransportRouteDto;
import com.example.demo.transport.dto.TransportStepDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.Instant;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class TransportRouteService {

    @Value("${TRANSPORT_KAKAO_API_KEY}")
    private String kakaoKey;

    // ODsay는 서버 호출마다 IP를 화이트리스트에 등록해야 하는 번거로움 때문에
    // TMAP 대중교통 API(SK Open API, appKey 헤더 인증 — IP 등록 불필요)로 교체했다.
    @Value("${TRANSPORT_TMAP_API_KEY}")
    private String tmapKey;

    private static final String TMAP_TRANSIT_URL = "https://apis.openapi.sk.com/transit/routes";
    // TMAP 대중교통 API가 한 번에 줄 수 있는 최대 경로 후보 수(문서 기준 1~10, count 파라미터).
    private static final int MAX_RAW_ROUTES = 10;
    // 버스/지하철 탭 하나당 보여줄 최대 경로 개수. 전체 후보(MAX_RAW_ROUTES)를 버스/지하철로
    // 나눠 담기 때문에, 상황에 따라 이 개수를 못 채울 수도 있다(그 수단 경로가 그만큼 없을 때).
    private static final int MAX_PER_MODE = 6;
    // 도보 탭 전용 — 대중교통 API와 별개로 항상 조회해서, 버스/지하철 탭과 나란히 보여준다.
    private static final String TMAP_PEDESTRIAN_URL = "https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1";
    // 택시 탭 전용 — 실제 호출 API는 없어서(카카오T는 비즈니스 파트너 계약이 필요), 자동차
    // 경로안내 API가 같이 계산해주는 예상 택시요금(taxiFare)만 가져와서 보여준다.
    private static final String TMAP_CAR_ROUTE_URL = "https://apis.openapi.sk.com/tmap/routes?version=1";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private static final Duration ROUTE_CACHE_TTL = Duration.ofMinutes(30);
    private static final int ROUTE_CACHE_MAX_SIZE = 500;
    private final Map<String, CachedRoute> transportRouteStore = new ConcurrentHashMap<>();

    // -----------------------------------------------
    // 카카오 API → 좌표 조회
    // -----------------------------------------------
    public double[] getTransportCoords(String place) throws Exception {
        String encoded = URLEncoder.encode(place, StandardCharsets.UTF_8);
        String url = "https://dapi.kakao.com/v2/local/search/keyword.json?query=" + encoded;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "KakaoAK " + kakaoKey)
                .GET().build();

        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode docs = mapper.readTree(res.body()).get("documents");

        if (docs == null || docs.isEmpty()) return null;

        double x = docs.get(0).get("x").asDouble();
        double y = docs.get(0).get("y").asDouble();
        return new double[]{x, y};
    }

    public String searchKakaoPlaces(String query, String category, int requestedSize) throws Exception {
        int size = Math.max(1, Math.min(requestedSize, 15));
        StringBuilder url = new StringBuilder("https://dapi.kakao.com/v2/local/search/keyword.json?query=")
                .append(URLEncoder.encode(query, StandardCharsets.UTF_8))
                .append("&size=").append(size);
        if (category != null && !category.isBlank()) {
            url.append("&category_group_code=")
                    .append(URLEncoder.encode(category, StandardCharsets.UTF_8));
        }
        return callKakao(url.toString());
    }

    public String reverseGeocode(double x, double y) throws Exception {
        return callKakao("https://dapi.kakao.com/v2/local/geo/coord2address.json?x=" + x + "&y=" + y);
    }

    private String callKakao(String url) throws Exception {
        if (kakaoKey == null || kakaoKey.isBlank()) {
            return "{\"documents\":[],\"message\":\"Kakao API key is not configured\"}";
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "KakaoAK " + kakaoKey)
                .GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    // -----------------------------------------------
    // 텍스트 입력 → 경로 조회
    // -----------------------------------------------
    public List<TransportRouteDto> getTransportRoutes(String start, String end) throws Exception {
        // 역 이름만 추출 (예: "수원시청역 수인분당선" → "수원시청역")
        String cleanStart = start.split("\\s+")[0];
        String cleanEnd   = end.split("\\s+")[0];

        System.out.println("검색: " + cleanStart + " → " + cleanEnd);

        double[] startCoords = getTransportCoords(cleanStart);
        double[] endCoords   = getTransportCoords(cleanEnd);

        if (startCoords == null || endCoords == null) return List.of();

        List<TransportRouteDto> result = searchAndClassify(
                startCoords[0], startCoords[1],
                endCoords[0], endCoords[1],
                start, end
        );

        // 출발지/도착지 좌표 저장 → detail 페이지 지도, 택시 탭의 "카카오맵에서 보기" 링크에서 사용
        final double startLng = startCoords[0];
        final double startLat = startCoords[1];
        final double endLng2  = endCoords[0];
        final double endLat2  = endCoords[1];
        result.forEach(dto -> {
            dto.setStartLat(startLat);
            dto.setStartLng(startLng);
            dto.setEndLat(endLat2);
            dto.setEndLng(endLng2);
        });

        return result;
    }

    // -----------------------------------------------
    // TMAP 대중교통 검색 + ML 분류 (공통 로직)
    // -----------------------------------------------
    private List<TransportRouteDto> searchAndClassify(
            double startX, double startY,
            double endX, double endY,
            String startLabel, String end) throws Exception {

        // 화면을 버스/지하철/도보/택시 탭으로 나누기로 해서, 이제 "빠른/편한/추천" 식으로 여러 개를
        // 섞어 보여주는 대신 탭(수단)마다 가장 좋은 경로 딱 하나만 고른다. 택시는 아직 연동한
        // API가 없어서 이 메서드에서는 안 만든다(프론트에서 "준비 중"으로 표시).
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("startX", String.valueOf(startX));
        body.put("startY", String.valueOf(startY));
        body.put("endX", String.valueOf(endX));
        body.put("endY", String.valueOf(endY));
        body.put("count", MAX_RAW_ROUTES);
        body.put("lang", 0);
        body.put("format", "json");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(TMAP_TRANSIT_URL))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("appKey", tmapKey)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode root = mapper.readTree(res.body());

        String departTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        List<TransportRouteDto> result = new ArrayList<>();

        // 실패 응답은 {"result":{"message":...,"status":...}} 형태로 오고, 성공은 metaData.plan.itineraries.
        // (예: 출발/도착이 너무 가까우면 "출발지와 도착지가 너무 가까움" 에러가 온다.) 버스/지하철 탭은
        // 이 경우 그냥 비게 두고, 도보 탭은 아래에서 대중교통 결과와 무관하게 항상 따로 채운다.
        boolean hasTransit = root.has("metaData") && !root.has("result");
        if (!hasTransit) {
            System.out.println("TMAP 대중교통 응답 오류: " + res.body());
        } else {
            JsonNode itineraries = root.path("metaData").path("plan").path("itineraries");
            if (itineraries.isArray() && !itineraries.isEmpty()) {
                System.out.println("TMAP 경로 개수: " + itineraries.size());

                List<Map<String, Object>> busCandidates = new ArrayList<>();
                List<Map<String, Object>> subwayCandidates = new ArrayList<>();

                for (int i = 0; i < Math.min(itineraries.size(), MAX_RAW_ROUTES); i++) {
                    JsonNode itin = itineraries.get(i);
                    if (itin.get("totalTime") == null || itin.get("legs") == null) {
                        System.out.println("경로 " + i + ": 필드 누락 → 스킵");
                        continue;
                    }

                    // TMAP은 초 단위로 준다 — 화면/저장은 전부 "분" 단위를 전제로 하므로 여기서 변환한다.
                    float time     = itin.get("totalTime").asInt() / 60f;
                    float transfer = itin.path("transferCount").asInt();
                    float walk     = itin.path("totalWalkTime").asInt() / 60f;
                    List<TransportStepDto> steps = parseTmapLegs(itin.get("legs"));

                    boolean hasSubway = steps.stream().anyMatch(s -> "subway".equals(s.getType()));
                    boolean hasBus    = steps.stream().anyMatch(s -> "bus".equals(s.getType()));
                    if (!hasSubway && !hasBus) continue; // 대중교통 구간이 없는 경로는 여기서 다룰 게 아니다.

                    Map<String, Object> raw = new HashMap<>();
                    raw.put("steps", steps);
                    raw.put("time", time);
                    raw.put("transfer", transfer);
                    raw.put("walk", walk);

                    // 지하철 구간이 하나라도 있으면 지하철 탭, 없으면(버스만) 버스 탭.
                    if (hasSubway) subwayCandidates.add(raw); else busCandidates.add(raw);
                }

                for (Map<String, Object> r : pickTopByTime(busCandidates, MAX_PER_MODE)) {
                    TransportRouteDto dto = buildDto("bus", r, startLabel, end, departTime);
                    cacheRoute(dto);
                    result.add(dto);
                }
                for (Map<String, Object> r : pickTopByTime(subwayCandidates, MAX_PER_MODE)) {
                    TransportRouteDto dto = buildDto("subway", r, startLabel, end, departTime);
                    cacheRoute(dto);
                    result.add(dto);
                }
            }
        }

        // 도보 탭은 대중교통 결과가 있든 없든 항상 따로 채운다(요청 좌표만 있으면 되는 별개 API라서).
        result.addAll(searchPedestrianRoute(startX, startY, endX, endY, startLabel, end));
        // 택시 탭도 마찬가지로 항상 따로 채운다 — 실제 호출은 안 되고 예상 요금/시간만 보여준다.
        result.addAll(searchTaxiEstimate(startX, startY, endX, endY, startLabel, end));

        return result;
    }

    /** 소요시간이 짧은(같으면 환승이 적은) 순으로 그 수단(버스/지하철) 탭에 최대 MAX_PER_MODE개까지 보여준다. */
    private List<Map<String, Object>> pickTopByTime(List<Map<String, Object>> candidates, int max) {
        return candidates.stream()
                .sorted(Comparator
                        .comparingDouble((Map<String, Object> r) -> (float) r.get("time"))
                        .thenComparingDouble(r -> (float) r.get("transfer")))
                .limit(max)
                .collect(Collectors.toList());
    }

    // -----------------------------------------------
    // 도보 탭 전용 경로 — 대중교통 결과와 무관하게 항상 따로 조회한다.
    // -----------------------------------------------
    private List<TransportRouteDto> searchPedestrianRoute(
            double startX, double startY, double endX, double endY,
            String startLabel, String end) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("startX", String.valueOf(startX));
            body.put("startY", String.valueOf(startY));
            body.put("endX", String.valueOf(endX));
            body.put("endY", String.valueOf(endY));
            body.put("startName", (startLabel == null || startLabel.isBlank()) ? "출발지" : startLabel);
            body.put("endName", (end == null || end.isBlank()) ? "도착지" : end);
            body.put("reqCoordType", "WGS84GEO");
            body.put("resCoordType", "WGS84GEO");
            body.put("searchOption", "0");
            body.put("sort", "index");

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(TMAP_PEDESTRIAN_URL))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("appKey", tmapKey)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(res.body());
            JsonNode features = root.get("features");
            if (features == null || !features.isArray() || features.isEmpty()) return List.of();

            // 맨 처음(출발점, pointType=SP) feature의 properties에 도보 경로 전체의 총 시간/거리가 들어있다.
            int totalTimeSec = features.get(0).path("properties").path("totalTime").asInt(0);
            if (totalTimeSec <= 0) return List.of();
            float timeMin = totalTimeSec / 60f;

            // LineString 구간들을 이어 붙여서 지도에 그릴 좌표열을 만든다.
            List<Map<String, Double>> path = new ArrayList<>();
            for (JsonNode feature : features) {
                JsonNode geometry = feature.path("geometry");
                if (!"LineString".equals(geometry.path("type").asText(""))) continue;
                for (JsonNode coord : geometry.path("coordinates")) {
                    if (coord.size() < 2) continue;
                    Map<String, Double> p = new HashMap<>();
                    p.put("lng", coord.get(0).asDouble());
                    p.put("lat", coord.get(1).asDouble());
                    path.add(p);
                }
            }

            TransportStepDto step = new TransportStepDto();
            step.setType("walk");
            step.setColor("#f59e0b");
            step.setFromStation(startLabel);
            step.setToStation(end);
            step.setDuration(Math.round(timeMin));
            step.setPath(path);

            TransportRouteDto dto = new TransportRouteDto();
            dto.setRouteId(UUID.randomUUID().toString());
            dto.setType("walk");
            dto.setStart(startLabel);
            dto.setEnd(end);
            dto.setTotalTime(Math.round(timeMin));
            dto.setTransferCount(0);
            dto.setWalkTime(timeMin);
            dto.setSteps(List.of(step));
            dto.setDepartTime(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
            cacheRoute(dto);

            System.out.println("도보 경로: " + Math.round(timeMin) + "분");
            return List.of(dto);
        } catch (Exception e) {
            System.out.println("보행자 경로 조회 실패: " + e.getMessage());
            return List.of();
        }
    }

    // -----------------------------------------------
    // 택시 탭 전용 예상 요금 — 실제 호출 API가 없어(카카오T는 비즈니스 계약 필요) 대신
    // TMAP 자동차 경로안내가 같이 계산해주는 taxiFare로 "예상 요금/시간"만 안내한다.
    // -----------------------------------------------
    private List<TransportRouteDto> searchTaxiEstimate(
            double startX, double startY, double endX, double endY,
            String startLabel, String end) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("startX", String.valueOf(startX));
            body.put("startY", String.valueOf(startY));
            body.put("endX", String.valueOf(endX));
            body.put("endY", String.valueOf(endY));
            body.put("reqCoordType", "WGS84GEO");
            body.put("resCoordType", "WGS84GEO");
            body.put("carType", "0");
            body.put("searchOption", "0");
            body.put("totalValue", "2"); // 요약 정보(총 거리/시간/요금)만 받는다.

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(TMAP_CAR_ROUTE_URL))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("appKey", tmapKey)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(res.body());
            JsonNode features = root.get("features");
            if (features == null || !features.isArray() || features.isEmpty()) return List.of();

            JsonNode props = features.get(0).path("properties");
            int totalTimeSec = props.path("totalTime").asInt(0);
            int taxiFare = props.path("taxiFare").asInt(0);
            if (totalTimeSec <= 0 || taxiFare <= 0) return List.of();
            float timeMin = totalTimeSec / 60f;

            TransportStepDto step = new TransportStepDto();
            step.setType("taxi");
            step.setColor("#facc15");
            step.setFromStation(startLabel);
            step.setToStation(end);
            step.setDuration(Math.round(timeMin));

            TransportRouteDto dto = new TransportRouteDto();
            dto.setRouteId(UUID.randomUUID().toString());
            dto.setType("taxi");
            dto.setStart(startLabel);
            dto.setEnd(end);
            dto.setTotalTime(Math.round(timeMin));
            dto.setTransferCount(0);
            dto.setWalkTime(0);
            dto.setFareWon(taxiFare);
            dto.setSteps(List.of(step));
            dto.setDepartTime(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
            cacheRoute(dto);

            System.out.println("택시 예상 요금: " + taxiFare + "원, " + Math.round(timeMin) + "분");
            return List.of(dto);
        } catch (Exception e) {
            System.out.println("택시 요금 조회 실패: " + e.getMessage());
            return List.of();
        }
    }

    // -----------------------------------------------
    // DTO 생성 헬퍼
    // -----------------------------------------------
    private TransportRouteDto buildDto(String type, Map<String, Object> r,
                                       String start, String end, String departTime) {
        TransportRouteDto dto = new TransportRouteDto();
        dto.setRouteId(UUID.randomUUID().toString());
        dto.setType(type);
        dto.setStart(start);
        dto.setEnd(end);
        dto.setTotalTime(((Float) r.get("time")).intValue());
        dto.setTransferCount(((Float) r.get("transfer")).intValue());
        dto.setWalkTime((float) r.get("walk"));
        dto.setSteps((List<TransportStepDto>) r.get("steps"));
        dto.setDepartTime(departTime);
        return dto;
    }

    // -----------------------------------------------
    // TMAP legs 파싱 (ODsay의 subPath 파싱을 대체)
    // -----------------------------------------------
    private List<TransportStepDto> parseTmapLegs(JsonNode legs) {
        List<TransportStepDto> steps = new ArrayList<>();
        if (legs == null) return steps;

        for (JsonNode leg : legs) {
            String mode = leg.path("mode").asText("");
            if (mode.isBlank() || leg.get("sectionTime") == null) continue;
            // TMAP은 초 단위 — 기존(ODsay, 분 단위) 로직과 맞추려고 분으로 반올림한다.
            int sectionTime = Math.round(leg.get("sectionTime").asInt() / 60f);

            JsonNode stations = leg.has("passStopList")
                    ? leg.get("passStopList").get("stations") : null;

            List<Map<String, Double>> pathCoords = new ArrayList<>();
            if (stations != null) {
                for (JsonNode s : stations) {
                    if (s.get("lat") == null || s.get("lon") == null) continue;
                    Map<String, Double> point = new HashMap<>();
                    point.put("lat", s.get("lat").asDouble());
                    point.put("lng", s.get("lon").asDouble());
                    pathCoords.add(point);
                }
            }

            if ("SUBWAY".equals(mode) || "BUS".equals(mode) || "EXPRESSBUS".equals(mode)) {
                String type    = "SUBWAY".equals(mode) ? "subway" : "bus";
                String line    = leg.path("route").asText("");
                String color   = tmapLegColor(leg);
                String fromSt  = leg.path("start").path("name").asText("");
                String toSt    = leg.path("end").path("name").asText("");
                Integer fromStationId = stations != null && !stations.isEmpty() ? readStationId(stations.get(0)) : null;
                Integer toStationId   = stations != null && !stations.isEmpty() ? readStationId(stations.get(stations.size() - 1)) : null;

                TransportStepDto step = new TransportStepDto();
                step.setType(type);
                step.setLine(line);
                step.setColor(color);
                step.setFromStation(fromSt);
                step.setToStation(toSt);
                step.setFromStationId(fromStationId);
                step.setToStationId(toStationId);
                step.setDuration(sectionTime);
                step.setPath(pathCoords);
                steps.add(step);

            } else if ("WALK".equals(mode) && sectionTime > 0) {
                List<Map<String, Double>> walkPath = new ArrayList<>();
                JsonNode startPt = leg.get("start");
                JsonNode endPt   = leg.get("end");
                if (startPt != null && startPt.get("lat") != null) {
                    Map<String, Double> p = new HashMap<>();
                    p.put("lat", startPt.get("lat").asDouble());
                    p.put("lng", startPt.get("lon").asDouble());
                    walkPath.add(p);
                }
                if (endPt != null && endPt.get("lat") != null) {
                    Map<String, Double> p = new HashMap<>();
                    p.put("lat", endPt.get("lat").asDouble());
                    p.put("lng", endPt.get("lon").asDouble());
                    walkPath.add(p);
                }

                TransportStepDto step = new TransportStepDto();
                step.setType("walk");
                step.setColor("#f59e0b");
                step.setFromStation(startPt != null ? startPt.path("name").asText("") : "");
                step.setToStation(endPt != null ? endPt.path("name").asText("") : "");
                step.setDuration(sectionTime);
                step.setPath(walkPath);
                steps.add(step);
            }
            // TRAIN/AIRPLANE/FERRY 등 그 외 모드는 부산 시내 검색에서는 거의 안 나오는 구간이라 스킵한다.
        }
        return steps;
    }

    /** TMAP은 노선 색상(routeColor, "#" 없는 hex)을 직접 내려주므로 별도 색상표가 필요 없다. */
    private String tmapLegColor(JsonNode leg) {
        String hex = leg.path("routeColor").asText("");
        if (hex.isBlank()) return "#888888";
        return hex.startsWith("#") ? hex : "#" + hex;
    }

    private Integer readStationId(JsonNode station) {
        if (station == null) {
            return null;
        }

        for (String field : List.of("stationID", "stationId", "stationCode", "localStationID")) {
            JsonNode value = station.get(field);
            if (value != null && value.canConvertToInt()) {
                return value.asInt();
            }
            if (value != null && value.isTextual()) {
                try {
                    return Integer.parseInt(value.asText().trim());
                } catch (NumberFormatException ignored) {
                    // Try the next station id field.
                }
            }
        }

        return null;
    }

    public TransportRouteDto getTransportRouteDetail(String routeId) {
        CachedRoute cached = transportRouteStore.get(routeId);
        if (cached == null) return null;
        if (cached.createdAt().plus(ROUTE_CACHE_TTL).isBefore(Instant.now())) {
            transportRouteStore.remove(routeId, cached);
            return null;
        }
        return cached.route();
    }
    private void cacheRoute(TransportRouteDto route) {
        Instant now = Instant.now();
        transportRouteStore.entrySet().removeIf(entry ->
                entry.getValue().createdAt().plus(ROUTE_CACHE_TTL).isBefore(now));
        if (transportRouteStore.size() >= ROUTE_CACHE_MAX_SIZE) {
            transportRouteStore.entrySet().stream()
                    .min(Comparator.comparing(entry -> entry.getValue().createdAt()))
                    .ifPresent(entry -> transportRouteStore.remove(entry.getKey(), entry.getValue()));
        }
        transportRouteStore.put(route.getRouteId(), new CachedRoute(route, now));
    }

    private record CachedRoute(TransportRouteDto route, Instant createdAt) {}
}
