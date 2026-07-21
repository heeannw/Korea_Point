package com.example.demo.transport.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BusanBusArrivalService {

    private static final String BASE_URL = "http://apis.data.go.kr/6260000/BusanBIMS";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${BUSAN_BIMS_SERVICE_KEY:}")
    private String serviceKey;

    public String getArrivalJson(String stopName, String busNo, Double lat, Double lng) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("source", "busan-bims");
        response.put("stopName", stopName);
        response.put("busNo", busNo);

        try {
            if (serviceKey == null || serviceKey.isBlank()) {
                response.put("arrivals", List.of());
                response.put("error", "missing-service-key");
                return objectMapper.writeValueAsString(response);
            }

            StopCandidate stop = findStop(stopName, lat, lng);
            if (stop == null) {
                response.put("arrivals", List.of());
                response.put("error", "stop-not-found");
                return objectMapper.writeValueAsString(response);
            }

            List<Map<String, String>> arrivals = fetchArrivals(stop, busNo);
            response.put("stopId", stop.bstopid());
            response.put("arsNo", stop.arsno());
            response.put("matchedStopName", stop.bstopnm());
            response.put("arrivals", arrivals);
        } catch (Exception e) {
            response.put("arrivals", List.of());
            response.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            return "{\"source\":\"busan-bims\",\"arrivals\":[],\"error\":\"json-error\"}";
        }
    }

    private StopCandidate findStop(String stopName, Double lat, Double lng) throws Exception {
        if (stopName == null || stopName.isBlank()) {
            return null;
        }

        String url = BASE_URL + "/busStopList"
                + "?serviceKey=" + encode(serviceKey)
                + "&pageNo=1"
                + "&numOfRows=20"
                + "&bstopnm=" + encode(cleanStopName(stopName));

        Document doc = callXml(url);
        List<StopCandidate> stops = readItems(doc).stream()
                .map(this::toStopCandidate)
                .filter(stop -> stop.bstopid() != null && !stop.bstopid().isBlank())
                .toList();

        if (stops.isEmpty()) {
            return null;
        }

        if (lat != null && lng != null) {
            return stops.stream()
                    .min(Comparator.comparingDouble(stop -> distanceScore(stop, lat, lng)))
                    .orElse(stops.get(0));
        }

        return stops.get(0);
    }

    private List<Map<String, String>> fetchArrivals(StopCandidate stop, String busNo) throws Exception {
        List<String> urls = new ArrayList<>();
        if (stop.bstopid() != null && !stop.bstopid().isBlank()) {
            urls.add(BASE_URL + "/busStopArrByBstopid?serviceKey=" + encode(serviceKey)
                    + "&bstopid=" + encode(stop.bstopid()));
        }
        if (stop.arsno() != null && !stop.arsno().isBlank()) {
            urls.add(BASE_URL + "/busStopArrByArsno?serviceKey=" + encode(serviceKey)
                    + "&arsno=" + encode(stop.arsno()));
        }

        Exception lastError = null;
        for (String url : urls) {
            try {
                List<Map<String, String>> arrivals = readArrivalItems(callXml(url), busNo);
                if (!arrivals.isEmpty()) {
                    return arrivals;
                }
            } catch (Exception e) {
                lastError = e;
            }
        }

        if (lastError != null) {
            throw lastError;
        }
        return List.of();
    }

    private List<Map<String, String>> readArrivalItems(Document doc, String busNo) {
        String normalizedBusNo = normalizeBusNo(busNo);
        List<Map<String, String>> arrivals = new ArrayList<>();

        for (Element item : readItems(doc)) {
            String lineNo = firstText(item, "lineno", "buslinenum", "routeNo", "busNo");
            if (!normalizedBusNo.isBlank() && !normalizeBusNo(lineNo).equals(normalizedBusNo)) {
                continue;
            }

            Map<String, String> arrival = new LinkedHashMap<>();
            arrival.put("line", lineNo != null && !lineNo.isBlank() ? lineNo : busNo);
            arrival.put("message", buildMessage(item));
            arrival.put("remainingStops", firstText(item, "bstopcnt", "reststopcnt", "stationCnt", "stopCnt"));
            arrival.put("vehicleNo", firstText(item, "carno", "vehicleno", "plateNo"));
            arrivals.add(arrival);
        }

        return arrivals.stream().limit(3).toList();
    }

    private String buildMessage(Element item) {
        String minute = firstText(item, "min1", "arrtime", "arrivalTime", "resttime", "time");
        String stopCount = firstText(item, "bstopcnt", "reststopcnt", "stationCnt", "stopCnt");

        if (minute != null && !minute.isBlank()) {
            String numberOnly = minute.replaceAll("[^0-9]", "");
            if (!numberOnly.isBlank()) {
                return numberOnly + "분 후 도착";
            }
            return minute;
        }
        if (stopCount != null && !stopCount.isBlank()) {
            return stopCount + "개 정류장 전";
        }
        return "도착정보 확인 중";
    }

    private StopCandidate toStopCandidate(Element item) {
        return new StopCandidate(
                firstText(item, "bstopid"),
                firstText(item, "arsno"),
                firstText(item, "bstopnm"),
                parseDouble(firstText(item, "gpsy")),
                parseDouble(firstText(item, "gpsx"))
        );
    }

    private Document callXml(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("BIMS HTTP " + response.statusCode());
        }
        return DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new ByteArrayInputStream(response.body()));
    }

    private List<Element> readItems(Document doc) {
        NodeList nodes = doc.getElementsByTagName("item");
        List<Element> items = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element element) {
                items.add(element);
            }
        }
        return items;
    }

    private String firstText(Element item, String... names) {
        for (String name : names) {
            NodeList nodes = item.getElementsByTagName(name);
            if (nodes.getLength() > 0 && nodes.item(0) != null) {
                String value = nodes.item(0).getTextContent();
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
        }
        return "";
    }

    private String cleanStopName(String stopName) {
        return stopName.replaceAll("\\(.*?\\)", "")
                .replaceAll("\\s+", "")
                .trim();
    }

    private String normalizeBusNo(String busNo) {
        if (busNo == null) {
            return "";
        }
        return busNo.replaceAll("\\s+", "").trim();
    }

    private Double parseDouble(String value) {
        try {
            return value == null || value.isBlank() ? null : Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private double distanceScore(StopCandidate stop, double lat, double lng) {
        if (stop.lat() == null || stop.lng() == null) {
            return Double.MAX_VALUE;
        }
        double dLat = stop.lat() - lat;
        double dLng = stop.lng() - lng;
        return dLat * dLat + dLng * dLng;
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private record StopCandidate(String bstopid, String arsno, String bstopnm, Double lat, Double lng) {}
}
