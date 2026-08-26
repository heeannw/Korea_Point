package com.example.demo.transport.service;

import com.example.demo.transport.dto.TransportRouteDto;
import com.example.demo.transport.dto.TransportStepDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
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

@Service
public class TransportRouteService {

    @Value("${TRANSPORT_KAKAO_API_KEY}")
    private String kakaoKey;

    @Value("${TRANSPORT_ODSAY_API_KEY}")
    private String odsayKey;

    @Autowired
    private TransportMlService transportMlService;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private static final Duration ROUTE_CACHE_TTL = Duration.ofMinutes(30);
    private static final int ROUTE_CACHE_MAX_SIZE = 500;
    private final Map<String, CachedRoute> transportRouteStore = new ConcurrentHashMap<>();

    private static final Map<Integer, String> SUBWAY_COLORS = new HashMap<>() {{
        put(1, "#0052A4"); put(2, "#00A84D"); put(3, "#EF7C1C");
        put(4, "#00A5DE"); put(5, "#996CAC"); put(6, "#CD7C2F");
        put(7, "#747F00"); put(8, "#E6186C"); put(9, "#BDB092");
        put(101, "#F5A200"); put(104, "#6789CA");
    }};

    private static final Map<Integer, String> BUS_COLORS = new HashMap<>() {{
        put(1, "#3D5BAB"); put(2, "#5BB025");
        put(3, "#F99D1C"); put(4, "#F72F08");
    }};

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

        // 출발지 좌표 저장 → detail 페이지 지도에서 사용
        final double startLng = startCoords[0];
        final double startLat = startCoords[1];
        result.forEach(dto -> {
            dto.setStartLat(startLat);
            dto.setStartLng(startLng);
        });

        return result;
    }

    // -----------------------------------------------
    // 현재위치(좌표) → 경로 조회
    // -----------------------------------------------
    public List<TransportRouteDto> getTransportRoutesByCoords(
            double lat, double lng, String end) throws Exception {

        String cleanEnd = end.split("\\s+")[0];
        double[] endCoords = getTransportCoords(cleanEnd);
        if (endCoords == null) return List.of();

        List<TransportRouteDto> result = searchAndClassify(
                lng, lat,
                endCoords[0], endCoords[1],
                "현재위치", end
        );

        //GPS 좌표 저장
        result.forEach(dto -> {
            dto.setStartLat(lat);
            dto.setStartLng(lng);
        });

        return result;
    }

    // -----------------------------------------------
    // BUG3 FIX: 출발지·도착지 모두 좌표 직접 사용 (지오코딩 없음)
    //   음식점·카페처럼 정확한 좌표를 이미 알고 있을 때 사용.
    //   기존 getTransportRoutesByCoords()는 end 이름으로 카카오 지오코딩을 해서
    //   동명 장소가 있으면 엉뚱한 곳으로 경로가 안내되는 문제가 있었음.
    //   이 메서드는 그 지오코딩 단계를 완전히 건너뜀.
    // -----------------------------------------------
    public List<TransportRouteDto> getTransportRoutesByCoordsToCoords(
            double startLat, double startLng,
            double endLat,   double endLng,
            String endName) throws Exception {

        // searchAndClassify 는 (X=경도, Y=위도) 순서로 받음 (카카오/ODsay 공통 규칙)
        List<TransportRouteDto> result = searchAndClassify(
                startLng, startLat,   // 출발 X(경도), Y(위도)
                endLng,   endLat,     // 도착 X(경도), Y(위도)
                "현재위치", endName
        );

        result.forEach(dto -> {
            dto.setStartLat(startLat);
            dto.setStartLng(startLng);
        });

        return result;
    }

    // -----------------------------------------------
    // ODsay 검색 + ML 분류 (공통 로직)
    // -----------------------------------------------
    private List<TransportRouteDto> searchAndClassify(
            double startX, double startY,
            double endX, double endY,
            String startLabel, String end) throws Exception {

        String url = "https://api.odsay.com/v1/api/searchPubTransPathT"
                + "?SX=" + startX
                + "&SY=" + startY
                + "&EX=" + endX
                + "&EY=" + endY
                + "&apiKey=" + URLEncoder.encode(odsayKey, StandardCharsets.UTF_8);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET().build();

        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode root = mapper.readTree(res.body());

        if (root.has("error") || !root.has("result")) return List.of();

        JsonNode paths = root.get("result").get("path");
        if (paths == null || paths.isEmpty()) return List.of();

        System.out.println("ODsay 경로 개수: " + paths.size());

        List<Map<String, Object>> rawRoutes = new ArrayList<>();
        float minTime = Float.MAX_VALUE;

        for (int i = 0; i < Math.min(paths.size(), 5); i++) {
            JsonNode path = paths.get(i);
            JsonNode info = path.get("info");

            // info 또는 필수 필드가 없는 경로는 스킵
            if (info == null || info.get("totalTime") == null
                    || info.get("busTransitCount") == null
                    || info.get("subwayTransitCount") == null
                    || info.get("totalWalk") == null) {
                System.out.println("경로 " + i + ": info 필드 누락 → 스킵");
                continue;
            }

            float time     = info.get("totalTime").asInt();
            float transfer = info.get("busTransitCount").asInt()
                    + info.get("subwayTransitCount").asInt();
            float walk     = (float)(info.get("totalWalk").asDouble() / 60.0);

            System.out.println("경로 " + i + ": " + time + "분, 환승 " + (int)transfer + "회");

            List<TransportStepDto> steps = parseTransportSteps(path.get("subPath"));

            float transferTime = calcAvgTransferTime(steps);
            float subwayRatio  = calcSubwayRatio(steps, time);
            float busRatio     = calcBusRatio(steps, time);
            float nSegments    = calcNSegments(steps);
            float avgStopCount = calcAvgStopCount(steps);

            float mlProb = transportMlService.getMlProb(
                    time, transfer, walk, transferTime,
                    subwayRatio, busRatio, nSegments, avgStopCount
            );

            if (time < minTime) minTime = time;

            Map<String, Object> raw = new HashMap<>();
            raw.put("steps",       steps);
            raw.put("time",        time);
            raw.put("transfer",    transfer);
            raw.put("walk",        walk);
            raw.put("subwayRatio", subwayRatio);
            raw.put("mlProb",      mlProb);
            rawRoutes.add(raw);
        }

        String departTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        String[] typeLabels = {"fast", "comfort", "recommend"};
        List<TransportRouteDto> result = new ArrayList<>();

        if (rawRoutes.size() >= 3) {
            Set<Integer> usedIndexes = new HashSet<>();

            for (String routeType : List.of("fast", "comfort", "recommend")) {
                float bestScore = -1f;
                int bestIdx = 0;

                for (int i = 0; i < rawRoutes.size(); i++) {
                    if (usedIndexes.contains(i)) continue;

                    Map<String, Object> r = rawRoutes.get(i);
                    float score = transportMlService.calcScore(
                            (float) r.get("time"),
                            (float) r.get("transfer"),
                            (float) r.get("walk"),
                            (float) r.get("subwayRatio"),
                            (float) r.get("mlProb"),
                            minTime,
                            routeType
                    );
                    if (score > bestScore) {
                        bestScore = score;
                        bestIdx = i;
                    }
                }

                usedIndexes.add(bestIdx);
                final int finalBestIdx = bestIdx;

                boolean isDuplicate = result.stream()
                        .anyMatch(r -> r.getTotalTime() == ((Float) rawRoutes.get(finalBestIdx).get("time")).intValue()
                                && r.getTransferCount() == ((Float) rawRoutes.get(finalBestIdx).get("transfer")).intValue()
                                && r.getSteps().size() == ((List<?>) rawRoutes.get(finalBestIdx).get("steps")).size());

                if (!isDuplicate) {
                    TransportRouteDto dto = buildDto(routeType, rawRoutes.get(finalBestIdx), startLabel, end, departTime);
                    cacheRoute(dto);
                    result.add(dto);
                }
            }

        } else {
            for (int i = 0; i < rawRoutes.size(); i++) {
                TransportRouteDto dto = buildDto(typeLabels[i], rawRoutes.get(i), startLabel, end, departTime);
                cacheRoute(dto);
                result.add(dto);
            }
        }

        return result;
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
    // subPath 파싱
    // -----------------------------------------------
    private List<TransportStepDto> parseTransportSteps(JsonNode subPaths) {
        List<TransportStepDto> steps = new ArrayList<>();
        if (subPaths == null) return steps;

        for (JsonNode sp : subPaths) {
            if (sp.get("trafficType") == null || sp.get("sectionTime") == null) continue;
            int trafficType = sp.get("trafficType").asInt();
            int sectionTime = sp.get("sectionTime").asInt();
            JsonNode stations = sp.has("passStopList")
                    ? sp.get("passStopList").get("stations") : null;

            List<Map<String, Double>> pathCoords = new ArrayList<>();
            if (stations != null) {
                for (JsonNode s : stations) {
                    if (s.get("y") == null || s.get("x") == null) continue;
                    Map<String, Double> point = new HashMap<>();
                    point.put("lat", s.get("y").asDouble());
                    point.put("lng", s.get("x").asDouble());
                    pathCoords.add(point);
                }
            }

            if (trafficType == 1) {
                JsonNode laneArr  = sp.get("lane");
                if (laneArr == null || laneArr.isEmpty()) continue;
                JsonNode lane     = laneArr.get(0);
                if (lane.get("subwayCode") == null || lane.get("name") == null) continue;
                int    subwayCode = lane.get("subwayCode").asInt();
                String color      = SUBWAY_COLORS.getOrDefault(subwayCode, "#888888");
                String lineName   = lane.get("name").asText();
                String fromSt     = stations != null ? stations.get(0).get("stationName").asText() : "";
                String toSt       = stations != null ? stations.get(stations.size()-1).get("stationName").asText() : "";
                Integer fromStationId = stations != null ? readStationId(stations.get(0)) : null;
                Integer toStationId = stations != null ? readStationId(stations.get(stations.size()-1)) : null;

                TransportStepDto step = new TransportStepDto();
                step.setType("subway");
                step.setLine(lineName);
                step.setSubwayCode(subwayCode);
                step.setColor(color);
                step.setFromStation(fromSt);
                step.setToStation(toSt);
                step.setFromStationId(fromStationId);
                step.setToStationId(toStationId);
                step.setDuration(sectionTime);
                step.setPath(pathCoords);
                steps.add(step);

            } else if (trafficType == 2) {
                JsonNode laneArr = sp.get("lane");
                if (laneArr == null || laneArr.isEmpty()) continue;
                JsonNode lane  = laneArr.get(0);
                if (lane.get("busNo") == null) continue;
                int    busType = lane.has("type") ? lane.get("type").asInt() : 1;
                String color   = BUS_COLORS.getOrDefault(busType, "#3A7BD5");
                String busNo   = lane.get("busNo").asText();
                String fromSt  = stations != null ? stations.get(0).get("stationName").asText() : "";
                String toSt    = stations != null ? stations.get(stations.size()-1).get("stationName").asText() : "";
                Integer fromStationId = stations != null ? readStationId(stations.get(0)) : null;
                Integer toStationId = stations != null ? readStationId(stations.get(stations.size()-1)) : null;

                TransportStepDto step = new TransportStepDto();
                step.setType("bus");
                step.setLine(busNo);
                step.setColor(color);
                step.setFromStation(fromSt);
                step.setToStation(toSt);
                step.setFromStationId(fromStationId);
                step.setToStationId(toStationId);
                step.setDuration(sectionTime);
                step.setPath(pathCoords);
                steps.add(step);

            } else if (trafficType == 3 && sectionTime > 0) {
                List<Map<String, Double>> walkPath = new ArrayList<>();

                if (sp.has("startExitX") && !sp.get("startExitX").isNull()
                        && sp.get("startExitX").asDouble() != 0) {
                    Map<String, Double> p = new HashMap<>();
                    p.put("lat", sp.get("startExitY").asDouble());
                    p.put("lng", sp.get("startExitX").asDouble());
                    walkPath.add(p);
                }
                if (sp.has("endExitX") && !sp.get("endExitX").isNull()
                        && sp.get("endExitX").asDouble() != 0) {
                    Map<String, Double> p = new HashMap<>();
                    p.put("lat", sp.get("endExitY").asDouble());
                    p.put("lng", sp.get("endExitX").asDouble());
                    walkPath.add(p);
                }

                if (walkPath.isEmpty()) {
                    if (sp.has("startX") && !sp.get("startX").isNull()
                            && sp.get("startX").asDouble() != 0) {
                        Map<String, Double> p = new HashMap<>();
                        p.put("lat", sp.get("startY").asDouble());
                        p.put("lng", sp.get("startX").asDouble());
                        walkPath.add(p);
                    }
                    if (sp.has("endX") && !sp.get("endX").isNull()
                            && sp.get("endX").asDouble() != 0) {
                        Map<String, Double> p = new HashMap<>();
                        p.put("lat", sp.get("endY").asDouble());
                        p.put("lng", sp.get("endX").asDouble());
                        walkPath.add(p);
                    }
                }

                TransportStepDto step = new TransportStepDto();
                step.setType("walk");
                step.setColor("#f59e0b");
                step.setFromStation(sp.has("startName") ? sp.get("startName").asText() : "");
                step.setToStation(sp.has("endName")     ? sp.get("endName").asText()   : "");
                step.setDuration(sectionTime);
                step.setPath(walkPath);
                steps.add(step);
            }
        }
        return steps;
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
                    // Try the next ODsay station id field.
                }
            }
        }

        return null;
    }

    // -----------------------------------------------
    // 헬퍼 메서드들
    // -----------------------------------------------
    private float calcAvgTransferTime(List<TransportStepDto> steps) {
        List<Float> times = new ArrayList<>();
        for (TransportStepDto step : steps) {
            if ("subway".equals(step.getType()) && step.getFromStation() != null) {
                times.add(transportMlService.getTransferTime(step.getFromStation()));
            }
        }
        if (times.isEmpty()) return 5.0f;
        return (float) times.stream().mapToDouble(Float::doubleValue).average().orElse(5.0);
    }

    private float calcSubwayRatio(List<TransportStepDto> steps, float totalTime) {
        if (totalTime == 0) return 0f;
        float subwayTime = steps.stream()
                .filter(s -> "subway".equals(s.getType()))
                .mapToInt(TransportStepDto::getDuration)
                .sum();
        return subwayTime / totalTime;
    }

    private float calcBusRatio(List<TransportStepDto> steps, float totalTime) {
        if (totalTime == 0) return 0f;
        float busTime = steps.stream()
                .filter(s -> "bus".equals(s.getType()))
                .mapToInt(TransportStepDto::getDuration)
                .sum();
        return busTime / totalTime;
    }

    private float calcNSegments(List<TransportStepDto> steps) {
        return (float) steps.stream()
                .filter(s -> "subway".equals(s.getType()) || "bus".equals(s.getType()))
                .count();
    }

    private float calcAvgStopCount(List<TransportStepDto> steps) {
        List<TransportStepDto> transitSteps = steps.stream()
                .filter(s -> "subway".equals(s.getType()) || "bus".equals(s.getType()))
                .toList();
        if (transitSteps.isEmpty()) return 0f;
        float totalStops = transitSteps.stream()
                .mapToInt(s -> s.getPath() != null ? s.getPath().size() : 0)
                .sum();
        return totalStops / transitSteps.size();
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
