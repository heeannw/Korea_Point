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
import java.time.format.DateTimeFormatter;
import java.util.*;

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

    private final Map<String, TransportRouteDto> transportRouteStore = new HashMap<>();

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

    // -----------------------------------------------
    // 텍스트 입력 → 경로 조회
    // -----------------------------------------------
    public List<TransportRouteDto> getTransportRoutes(String start, String end) throws Exception {
        double[] startCoords = getTransportCoords(start);
        double[] endCoords   = getTransportCoords(end);

        if (startCoords == null || endCoords == null) return List.of();

        return searchAndClassify(
                startCoords[0], startCoords[1],
                endCoords[0], endCoords[1],
                start, end
        );
    }

    // -----------------------------------------------
    // 현재위치(좌표) → 경로 조회
    // -----------------------------------------------
    public List<TransportRouteDto> getTransportRoutesByCoords(
            double lat, double lng, String end) throws Exception {

        double[] endCoords = getTransportCoords(end);
        if (endCoords == null) return List.of();

        // 위도=lat(Y), 경도=lng(X)
        return searchAndClassify(
                lng, lat,           // SX=경도, SY=위도
                endCoords[0], endCoords[1],
                "현재위치", end
        );
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

        // 1. 경로 파싱 + ML 확률 계산
        List<Map<String, Object>> rawRoutes = new ArrayList<>();
        float minTime = Float.MAX_VALUE;

        for (int i = 0; i < Math.min(paths.size(), 5); i++) {
            JsonNode path = paths.get(i);
            JsonNode info = path.get("info");

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

        // 2. 경로 개수에 따라 분류
        String departTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        String[] typeLabels = {"fast", "comfort", "recommend"};
        List<TransportRouteDto> result = new ArrayList<>();

        if (rawRoutes.size() >= 3) {
            Set<Integer> usedIndexes = new HashSet<>();

            for (String routeType : List.of("fast", "comfpackage com.example.demo.transport.service;\n" +
                    "\n" +
                    "import com.example.demo.transport.dto.TransportRouteDto;\n" +
                    "import com.example.demo.transport.dto.TransportStepDto;\n" +
                    "import com.fasterxml.jackson.databind.JsonNode;\n" +
                    "import com.fasterxml.jackson.databind.ObjectMapper;\n" +
                    "import org.springframework.beans.factory.annotation.Autowired;\n" +
                    "import org.springframework.beans.factory.annotation.Value;\n" +
                    "import org.springframework.stereotype.Service;\n" +
                    "\n" +
                    "import java.net.URI;\n" +
                    "import java.net.URLEncoder;\n" +
                    "import java.net.http.HttpClient;\n" +
                    "import java.net.http.HttpRequest;\n" +
                    "import java.net.http.HttpResponse;\n" +
                    "import java.nio.charset.StandardCharsets;\n" +
                    "import java.time.LocalTime;\n" +
                    "import java.time.format.DateTimeFormatter;\n" +
                    "import java.util.*;\n" +
                    "\n" +
                    "@Service\n" +
                    "public class TransportRouteService {\n" +
                    "\n" +
                    "    @Value(\"${TRANSPORT_KAKAO_API_KEY}\")\n" +
                    "    private String kakaoKey;\n" +
                    "\n" +
                    "    @Value(\"${TRANSPORT_ODSAY_API_KEY}\")\n" +
                    "    private String odsayKey;\n" +
                    "\n" +
                    "    @Autowired\n" +
                    "    private TransportMlService transportMlService;\n" +
                    "\n" +
                    "    private final HttpClient httpClient = HttpClient.newHttpClient();\n" +
                    "    private final ObjectMapper mapper = new ObjectMapper();\n" +
                    "\n" +
                    "    private final Map<String, TransportRouteDto> transportRouteStore = new HashMap<>();\n" +
                    "\n" +
                    "    private static final Map<Integer, String> SUBWAY_COLORS = new HashMap<>() {{\n" +
                    "        put(1, \"#0052A4\"); put(2, \"#00A84D\"); put(3, \"#EF7C1C\");\n" +
                    "        put(4, \"#00A5DE\"); put(5, \"#996CAC\"); put(6, \"#CD7C2F\");\n" +
                    "        put(7, \"#747F00\"); put(8, \"#E6186C\"); put(9, \"#BDB092\");\n" +
                    "        put(101, \"#F5A200\"); put(104, \"#6789CA\");\n" +
                    "    }};\n" +
                    "\n" +
                    "    private static final Map<Integer, String> BUS_COLORS = new HashMap<>() {{\n" +
                    "        put(1, \"#3D5BAB\"); put(2, \"#5BB025\");\n" +
                    "        put(3, \"#F99D1C\"); put(4, \"#F72F08\");\n" +
                    "    }};\n" +
                    "\n" +
                    "    // -----------------------------------------------\n" +
                    "    // 카카오 API → 좌표 조회\n" +
                    "    // -----------------------------------------------\n" +
                    "    public double[] getTransportCoords(String place) throws Exception {\n" +
                    "        String encoded = URLEncoder.encode(place, StandardCharsets.UTF_8);\n" +
                    "        String url = \"https://dapi.kakao.com/v2/local/search/keyword.json?query=\" + encoded;\n" +
                    "\n" +
                    "        HttpRequest req = HttpRequest.newBuilder()\n" +
                    "                .uri(URI.create(url))\n" +
                    "                .header(\"Authorization\", \"KakaoAK \" + kakaoKey)\n" +
                    "                .GET().build();\n" +
                    "\n" +
                    "        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());\n" +
                    "        JsonNode docs = mapper.readTree(res.body()).get(\"documents\");\n" +
                    "\n" +
                    "        if (docs == null || docs.isEmpty()) return null;\n" +
                    "\n" +
                    "        double x = docs.get(0).get(\"x\").asDouble();\n" +
                    "        double y = docs.get(0).get(\"y\").asDouble();\n" +
                    "        return new double[]{x, y};\n" +
                    "    }\n" +
                    "\n" +
                    "    // -----------------------------------------------\n" +
                    "    // 텍스트 입력 → 경로 조회\n" +
                    "    // -----------------------------------------------\n" +
                    "    public List<TransportRouteDto> getTransportRoutes(String start, String end) throws Exception {\n" +
                    "        double[] startCoords = getTransportCoords(start);\n" +
                    "        double[] endCoords   = getTransportCoords(end);\n" +
                    "\n" +
                    "        if (startCoords == null || endCoords == null) return List.of();\n" +
                    "\n" +
                    "        return searchAndClassify(\n" +
                    "                startCoords[0], startCoords[1],\n" +
                    "                endCoords[0], endCoords[1],\n" +
                    "                start, end\n" +
                    "        );\n" +
                    "    }\n" +
                    "\n" +
                    "    // -----------------------------------------------\n" +
                    "    // 현재위치(좌표) → 경로 조회\n" +
                    "    // -----------------------------------------------\n" +
                    "    public List<TransportRouteDto> getTransportRoutesByCoords(\n" +
                    "            double lat, double lng, String end) throws Exception {\n" +
                    "\n" +
                    "        double[] endCoords = getTransportCoords(end);\n" +
                    "        if (endCoords == null) return List.of();\n" +
                    "\n" +
                    "        // 위도=lat(Y), 경도=lng(X)\n" +
                    "        return searchAndClassify(\n" +
                    "                lng, lat,           // SX=경도, SY=위도\n" +
                    "                endCoords[0], endCoords[1],\n" +
                    "                \"현재위치\", end\n" +
                    "        );\n" +
                    "    }\n" +
                    "\n" +
                    "    // -----------------------------------------------\n" +
                    "    // ODsay 검색 + ML 분류 (공통 로직)\n" +
                    "    // -----------------------------------------------\n" +
                    "    private List<TransportRouteDto> searchAndClassify(\n" +
                    "            double startX, double startY,\n" +
                    "            double endX, double endY,\n" +
                    "            String startLabel, String end) throws Exception {\n" +
                    "\n" +
                    "        String url = \"https://api.odsay.com/v1/api/searchPubTransPathT\"\n" +
                    "                + \"?SX=\" + startX\n" +
                    "                + \"&SY=\" + startY\n" +
                    "                + \"&EX=\" + endX\n" +
                    "                + \"&EY=\" + endY\n" +
                    "                + \"&apiKey=\" + URLEncoder.encode(odsayKey, StandardCharsets.UTF_8);\n" +
                    "\n" +
                    "        HttpRequest req = HttpRequest.newBuilder()\n" +
                    "                .uri(URI.create(url))\n" +
                    "                .GET().build();\n" +
                    "\n" +
                    "        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());\n" +
                    "        JsonNode root = mapper.readTree(res.body());\n" +
                    "\n" +
                    "        if (root.has(\"error\") || !root.has(\"result\")) return List.of();\n" +
                    "\n" +
                    "        JsonNode paths = root.get(\"result\").get(\"path\");\n" +
                    "        if (paths == null || paths.isEmpty()) return List.of();\n" +
                    "\n" +
                    "        System.out.println(\"ODsay 경로 개수: \" + paths.size());\n" +
                    "\n" +
                    "        // 1. 경로 파싱 + ML 확률 계산\n" +
                    "        List<Map<String, Object>> rawRoutes = new ArrayList<>();\n" +
                    "        float minTime = Float.MAX_VALUE;\n" +
                    "\n" +
                    "        for (int i = 0; i < Math.min(paths.size(), 5); i++) {\n" +
                    "            JsonNode path = paths.get(i);\n" +
                    "            JsonNode info = path.get(\"info\");\n" +
                    "\n" +
                    "            float time     = info.get(\"totalTime\").asInt();\n" +
                    "            float transfer = info.get(\"busTransitCount\").asInt()\n" +
                    "                    + info.get(\"subwayTransitCount\").asInt();\n" +
                    "            float walk     = (float)(info.get(\"totalWalk\").asDouble() / 60.0);\n" +
                    "\n" +
                    "            System.out.println(\"경로 \" + i + \": \" + time + \"분, 환승 \" + (int)transfer + \"회\");\n" +
                    "\n" +
                    "            List<TransportStepDto> steps = parseTransportSteps(path.get(\"subPath\"));\n" +
                    "\n" +
                    "            float transferTime = calcAvgTransferTime(steps);\n" +
                    "            float subwayRatio  = calcSubwayRatio(steps, time);\n" +
                    "            float busRatio     = calcBusRatio(steps, time);\n" +
                    "            float nSegments    = calcNSegments(steps);\n" +
                    "            float avgStopCount = calcAvgStopCount(steps);\n" +
                    "\n" +
                    "            float mlProb = transportMlService.getMlProb(\n" +
                    "                    time, transfer, walk, transferTime,\n" +
                    "                    subwayRatio, busRatio, nSegments, avgStopCount\n" +
                    "            );\n" +
                    "\n" +
                    "            if (time < minTime) minTime = time;\n" +
                    "\n" +
                    "            Map<String, Object> raw = new HashMap<>();\n" +
                    "            raw.put(\"steps\",       steps);\n" +
                    "            raw.put(\"time\",        time);\n" +
                    "            raw.put(\"transfer\",    transfer);\n" +
                    "            raw.put(\"walk\",        walk);\n" +
                    "            raw.put(\"subwayRatio\", subwayRatio);\n" +
                    "            raw.put(\"mlProb\",      mlProb);\n" +
                    "            rawRoutes.add(raw);\n" +
                    "        }\n" +
                    "\n" +
                    "        // 2. 경로 개수에 따라 분류\n" +
                    "        String departTime = LocalTime.now().format(DateTimeFormatter.ofPattern(\"HH:mm\"));\n" +
                    "        String[] typeLabels = {\"fast\", \"comfort\", \"recommend\"};\n" +
                    "        List<TransportRouteDto> result = new ArrayList<>();\n" +
                    "\n" +
                    "        if (rawRoutes.size() >= 3) {\n" +
                    "            Set<Integer> usedIndexes = new HashSet<>();\n" +
                    "\n" +
                    "            for (String routeType : List.of(\"fast\", \"comfort\", \"recommend\")) {\n" +
                    "                float bestScore = -1f;\n" +
                    "                int bestIdx = 0;\n" +
                    "\n" +
                    "                for (int i = 0; i < rawRoutes.size(); i++) {\n" +
                    "                    if (usedIndexes.contains(i)) continue;\n" +
                    "\n" +
                    "                    Map<String, Object> r = rawRoutes.get(i);\n" +
                    "                    float score = transportMlService.calcScore(\n" +
                    "                            (float) r.get(\"time\"),\n" +
                    "                            (float) r.get(\"transfer\"),\n" +
                    "                            (float) r.get(\"walk\"),\n" +
                    "                            (float) r.get(\"subwayRatio\"),\n" +
                    "                            (float) r.get(\"mlProb\"),\n" +
                    "                            minTime,\n" +
                    "                            routeType\n" +
                    "                    );\n" +
                    "                    if (score > bestScore) {\n" +
                    "                        bestScore = score;\n" +
                    "                        bestIdx = i;\n" +
                    "                    }\n" +
                    "                }\n" +
                    "\n" +
                    "                usedIndexes.add(bestIdx);\n" +
                    "                final int finalBestIdx = bestIdx;\n" +
                    "\n" +
                    "                boolean isDuplicate = result.stream()\n" +
                    "                        .anyMatch(r -> r.getTotalTime() == ((Float) rawRoutes.get(finalBestIdx).get(\"time\")).intValue()\n" +
                    "                                && r.getTransferCount() == ((Float) rawRoutes.get(finalBestIdx).get(\"transfer\")).intValue()\n" +
                    "                                && r.getSteps().size() == ((List<?>) rawRoutes.get(finalBestIdx).get(\"steps\")).size());\n" +
                    "\n" +
                    "                if (!isDuplicate) {\n" +
                    "                    TransportRouteDto dto = buildDto(routeType, rawRoutes.get(finalBestIdx), startLabel, end, departTime);\n" +
                    "                    transportRouteStore.put(dto.getRouteId(), dto);\n" +
                    "                    result.add(dto);\n" +
                    "                }\n" +
                    "            }\n" +
                    "\n" +
                    "        } else {\n" +
                    "            for (int i = 0; i < rawRoutes.size(); i++) {\n" +
                    "                TransportRouteDto dto = buildDto(typeLabels[i], rawRoutes.get(i), startLabel, end, departTime);\n" +
                    "                transportRouteStore.put(dto.getRouteId(), dto);\n" +
                    "                result.add(dto);\n" +
                    "            }\n" +
                    "        }\n" +
                    "\n" +
                    "        return result;\n" +
                    "    }\n" +
                    "\n" +
                    "    // -----------------------------------------------\n" +
                    "    // DTO 생성 헬퍼\n" +
                    "    // -----------------------------------------------\n" +
                    "    private TransportRouteDto buildDto(String type, Map<String, Object> r,\n" +
                    "                                       String start, String end, String departTime) {\n" +
                    "        TransportRouteDto dto = new TransportRouteDto();\n" +
                    "        dto.setRouteId(UUID.randomUUID().toString());\n" +
                    "        dto.setType(type);\n" +
                    "        dto.setStart(start);\n" +
                    "        dto.setEnd(end);\n" +
                    "        dto.setTotalTime(((Float) r.get(\"time\")).intValue());\n" +
                    "        dto.setTransferCount(((Float) r.get(\"transfer\")).intValue());\n" +
                    "        dto.setWalkTime((float) r.get(\"walk\"));\n" +
                    "        dto.setSteps((List<TransportStepDto>) r.get(\"steps\"));\n" +
                    "        dto.setDepartTime(departTime);\n" +
                    "        return dto;\n" +
                    "    }\n" +
                    "\n" +
                    "    // -----------------------------------------------\n" +
                    "    // subPath 파싱\n" +
                    "    // -----------------------------------------------\n" +
                    "    private List<TransportStepDto> parseTransportSteps(JsonNode subPaths) {\n" +
                    "        List<TransportStepDto> steps = new ArrayList<>();\n" +
                    "\n" +
                    "        for (JsonNode sp : subPaths) {\n" +
                    "            int trafficType = sp.get(\"trafficType\").asInt();\n" +
                    "            int sectionTime = sp.get(\"sectionTime\").asInt();\n" +
                    "            JsonNode stations = sp.has(\"passStopList\")\n" +
                    "                    ? sp.get(\"passStopList\").get(\"stations\") : null;\n" +
                    "\n" +
                    "            List<Map<String, Double>> pathCoords = new ArrayList<>();\n" +
                    "            if (stations != null) {\n" +
                    "                for (JsonNode s : stations) {\n" +
                    "                    Map<String, Double> point = new HashMap<>();\n" +
                    "                    point.put(\"lat\", s.get(\"y\").asDouble());\n" +
                    "                    point.put(\"lng\", s.get(\"x\").asDouble());\n" +
                    "                    pathCoords.add(point);\n" +
                    "                }\n" +
                    "            }\n" +
                    "\n" +
                    "            if (trafficType == 1) {\n" +
                    "                JsonNode lane     = sp.get(\"lane\").get(0);\n" +
                    "                int    subwayCode = lane.get(\"subwayCode\").asInt();\n" +
                    "                String color      = SUBWAY_COLORS.getOrDefault(subwayCode, \"#888888\");\n" +
                    "                String lineName   = lane.get(\"name\").asText();\n" +
                    "                String fromSt     = stations != null ? stations.get(0).get(\"stationName\").asText() : \"\";\n" +
                    "                String toSt       = stations != null ? stations.get(stations.size()-1).get(\"stationName\").asText() : \"\";\n" +
                    "\n" +
                    "                TransportStepDto step = new TransportStepDto();\n" +
                    "                step.setType(\"subway\");\n" +
                    "                step.setLine(lineName);\n" +
                    "                step.setColor(color);\n" +
                    "                step.setFromStation(fromSt);\n" +
                    "                step.setToStation(toSt);\n" +
                    "                step.setDuration(sectionTime);\n" +
                    "                step.setPath(pathCoords);\n" +
                    "                steps.add(step);\n" +
                    "\n" +
                    "            } else if (trafficType == 2) {\n" +
                    "                JsonNode lane  = sp.get(\"lane\").get(0);\n" +
                    "                int    busType = lane.has(\"type\") ? lane.get(\"type\").asInt() : 1;\n" +
                    "                String color   = BUS_COLORS.getOrDefault(busType, \"#3A7BD5\");\n" +
                    "                String busNo   = lane.get(\"busNo\").asText();\n" +
                    "                String fromSt  = stations != null ? stations.get(0).get(\"stationName\").asText() : \"\";\n" +
                    "                String toSt    = stations != null ? stations.get(stations.size()-1).get(\"stationName\").asText() : \"\";\n" +
                    "\n" +
                    "                TransportStepDto step = new TransportStepDto();\n" +
                    "                step.setType(\"bus\");\n" +
                    "                step.setLine(busNo);\n" +
                    "                step.setColor(color);\n" +
                    "                step.setFromStation(fromSt);\n" +
                    "                step.setToStation(toSt);\n" +
                    "                step.setDuration(sectionTime);\n" +
                    "                step.setPath(pathCoords);\n" +
                    "                steps.add(step);\n" +
                    "\n" +
                    "            } else if (trafficType == 3 && sectionTime > 0) {\n" +
                    "                List<Map<String, Double>> walkPath = new ArrayList<>();\n" +
                    "                if (sp.has(\"startExitX\") && !sp.get(\"startExitX\").isNull()) {\n" +
                    "                    Map<String, Double> p = new HashMap<>();\n" +
                    "                    p.put(\"lat\", sp.get(\"startExitY\").asDouble());\n" +
                    "                    p.put(\"lng\", sp.get(\"startExitX\").asDouble());\n" +
                    "                    walkPath.add(p);\n" +
                    "                }\n" +
                    "                if (sp.has(\"endExitX\") && !sp.get(\"endExitX\").isNull()) {\n" +
                    "                    Map<String, Double> p = new HashMap<>();\n" +
                    "                    p.put(\"lat\", sp.get(\"endExitY\").asDouble());\n" +
                    "                    p.put(\"lng\", sp.get(\"endExitX\").asDouble());\n" +
                    "                    walkPath.add(p);\n" +
                    "                }\n" +
                    "\n" +
                    "                TransportStepDto step = new TransportStepDto();\n" +
                    "                step.setType(\"walk\");\n" +
                    "                step.setColor(\"#f59e0b\");\n" +
                    "                step.setFromStation(sp.has(\"startName\") ? sp.get(\"startName\").asText() : \"\");\n" +
                    "                step.setToStation(sp.has(\"endName\")     ? sp.get(\"endName\").asText()   : \"\");\n" +
                    "                step.setDuration(sectionTime);\n" +
                    "                step.setPath(walkPath);\n" +
                    "                steps.add(step);\n" +
                    "            }\n" +
                    "        }\n" +
                    "        return steps;\n" +
                    "    }\n" +
                    "\n" +
                    "    // -----------------------------------------------\n" +
                    "    // 헬퍼 메서드들\n" +
                    "    // -----------------------------------------------\n" +
                    "    private float calcAvgTransferTime(List<TransportStepDto> steps) {\n" +
                    "        List<Float> times = new ArrayList<>();\n" +
                    "        for (TransportStepDto step : steps) {\n" +
                    "            if (\"subway\".equals(step.getType()) && step.getFromStation() != null) {\n" +
                    "                times.add(transportMlService.getTransferTime(step.getFromStation()));\n" +
                    "            }\n" +
                    "        }\n" +
                    "        if (times.isEmpty()) return 5.0f;\n" +
                    "        return (float) times.stream().mapToDouble(Float::doubleValue).average().orElse(5.0);\n" +
                    "    }\n" +
                    "\n" +
                    "    private float calcSubwayRatio(List<TransportStepDto> steps, float totalTime) {\n" +
                    "        if (totalTime == 0) return 0f;\n" +
                    "        float subwayTime = steps.stream()\n" +
                    "                .filter(s -> \"subway\".equals(s.getType()))\n" +
                    "                .mapToInt(TransportStepDto::getDuration)\n" +
                    "                .sum();\n" +
                    "        return subwayTime / totalTime;\n" +
                    "    }\n" +
                    "\n" +
                    "    private float calcBusRatio(List<TransportStepDto> steps, float totalTime) {\n" +
                    "        if (totalTime == 0) return 0f;\n" +
                    "        float busTime = steps.stream()\n" +
                    "                .filter(s -> \"bus\".equals(s.getType()))\n" +
                    "                .mapToInt(TransportStepDto::getDuration)\n" +
                    "                .sum();\n" +
                    "        return busTime / totalTime;\n" +
                    "    }\n" +
                    "\n" +
                    "    private float calcNSegments(List<TransportStepDto> steps) {\n" +
                    "        return (float) steps.stream()\n" +
                    "                .filter(s -> \"subway\".equals(s.getType()) || \"bus\".equals(s.getType()))\n" +
                    "                .count();\n" +
                    "    }\n" +
                    "\n" +
                    "    private float calcAvgStopCount(List<TransportStepDto> steps) {\n" +
                    "        List<TransportStepDto> transitSteps = steps.stream()\n" +
                    "                .filter(s -> \"subway\".equals(s.getType()) || \"bus\".equals(s.getType()))\n" +
                    "                .toList();\n" +
                    "        if (transitSteps.isEmpty()) return 0f;\n" +
                    "        float totalStops = transitSteps.stream()\n" +
                    "                .mapToInt(s -> s.getPath() != null ? s.getPath().size() : 0)\n" +
                    "                .sum();\n" +
                    "        return totalStops / transitSteps.size();\n" +
                    "    }\n" +
                    "\n" +
                    "    // -----------------------------------------------\n" +
                    "    // routeId로 상세 조회\n" +
                    "    // -----------------------------------------------\n" +
                    "    public TransportRouteDto getTransportRouteDetail(String routeId) {\n" +
                    "        return transportRouteStore.get(routeId);\n" +
                    "    }\n" +
                    "}ort", "recommend")) {
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
                    transportRouteStore.put(dto.getRouteId(), dto);
                    result.add(dto);
                }
            }

        } else {
            for (int i = 0; i < rawRoutes.size(); i++) {
                TransportRouteDto dto = buildDto(typeLabels[i], rawRoutes.get(i), startLabel, end, departTime);
                transportRouteStore.put(dto.getRouteId(), dto);
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

        for (JsonNode sp : subPaths) {
            int trafficType = sp.get("trafficType").asInt();
            int sectionTime = sp.get("sectionTime").asInt();
            JsonNode stations = sp.has("passStopList")
                    ? sp.get("passStopList").get("stations") : null;

            List<Map<String, Double>> pathCoords = new ArrayList<>();
            if (stations != null) {
                for (JsonNode s : stations) {
                    Map<String, Double> point = new HashMap<>();
                    point.put("lat", s.get("y").asDouble());
                    point.put("lng", s.get("x").asDouble());
                    pathCoords.add(point);
                }
            }

            if (trafficType == 1) {
                JsonNode lane     = sp.get("lane").get(0);
                int    subwayCode = lane.get("subwayCode").asInt();
                String color      = SUBWAY_COLORS.getOrDefault(subwayCode, "#888888");
                String lineName   = lane.get("name").asText();
                String fromSt     = stations != null ? stations.get(0).get("stationName").asText() : "";
                String toSt       = stations != null ? stations.get(stations.size()-1).get("stationName").asText() : "";

                TransportStepDto step = new TransportStepDto();
                step.setType("subway");
                step.setLine(lineName);
                step.setColor(color);
                step.setFromStation(fromSt);
                step.setToStation(toSt);
                step.setDuration(sectionTime);
                step.setPath(pathCoords);
                steps.add(step);

            } else if (trafficType == 2) {
                JsonNode lane  = sp.get("lane").get(0);
                int    busType = lane.has("type") ? lane.get("type").asInt() : 1;
                String color   = BUS_COLORS.getOrDefault(busType, "#3A7BD5");
                String busNo   = lane.get("busNo").asText();
                String fromSt  = stations != null ? stations.get(0).get("stationName").asText() : "";
                String toSt    = stations != null ? stations.get(stations.size()-1).get("stationName").asText() : "";

                TransportStepDto step = new TransportStepDto();
                step.setType("bus");
                step.setLine(busNo);
                step.setColor(color);
                step.setFromStation(fromSt);
                step.setToStation(toSt);
                step.setDuration(sectionTime);
                step.setPath(pathCoords);
                steps.add(step);

            } else if (trafficType == 3 && sectionTime > 0) {
                List<Map<String, Double>> walkPath = new ArrayList<>();
                if (sp.has("startExitX") && !sp.get("startExitX").isNull()) {
                    Map<String, Double> p = new HashMap<>();
                    p.put("lat", sp.get("startExitY").asDouble());
                    p.put("lng", sp.get("startExitX").asDouble());
                    walkPath.add(p);
                }
                if (sp.has("endExitX") && !sp.get("endExitX").isNull()) {
                    Map<String, Double> p = new HashMap<>();
                    p.put("lat", sp.get("endExitY").asDouble());
                    p.put("lng", sp.get("endExitX").asDouble());
                    walkPath.add(p);
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

    // -----------------------------------------------
    // routeId로 상세 조회
    // -----------------------------------------------
    public TransportRouteDto getTransportRouteDetail(String routeId) {
        return transportRouteStore.get(routeId);
    }
}