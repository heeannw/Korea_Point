package com.example.demo.transport.service;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Map;

@Service
public class CongestionMlService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OrtEnvironment env;
    private OrtSession session;
    private Map<String, StationMeta> stationMeta;
    private FallbackData fallbackData;

    @PostConstruct
    public void init() {
        try {
            env = OrtEnvironment.getEnvironment();

            session = env.createSession(
                    loadBytes("model/transport/transport_congestion.onnx"),
                    new OrtSession.SessionOptions()
            );

            stationMeta = readJson(
                    "model/transport/station_meta.json",
                    new TypeReference<Map<String, StationMeta>>() {}
            );
            fallbackData = readJson(
                    "model/transport/line_hour_fallback.json",
                    new TypeReference<FallbackData>() {}
            );

            System.out.println("CongestionMlService ONNX ready: stations=" + stationMeta.size());

        } catch (Throwable e) {
            System.out.println("CongestionMlService ONNX load failed");
            e.printStackTrace();
        }
    }

    public int predict(int hour, int day, String station, int line) {
        String normalizedStation = normalizeStation(station);

        try {
            if (session == null || stationMeta == null) {
                return fallback(line, day, hour, "onnx-not-ready", station, normalizedStation);
            }

            StationMeta meta = resolveStationMeta(normalizedStation);
            int lineNum = line > 0 ? line : (meta != null ? meta.lineNum() : 0);

            if (meta == null) {
                return fallback(lineNum, day, hour, "station-not-found", station, normalizedStation);
            }

            int stationNum = meta.stationNum();
            float[][] input = new float[][]{
                    {hour, day, lineNum, stationNum}
            };

            try (OnnxTensor tensor = OnnxTensor.createTensor(env, input);
                 OrtSession.Result result = session.run(Map.of("input", tensor))) {

                long[] labels = (long[]) result.get(0).getValue();
                int prediction = (int) labels[0];

                System.out.println(
                        "[Congestion ONNX] station=" + station
                                + ", normalizedStation=" + normalizedStation
                                + ", stationMatched=true"
                                + ", hour=" + hour
                                + ", day=" + day
                                + ", line=" + lineNum
                                + ", stationNum=" + stationNum
                                + ", prediction=" + prediction
                );

                return prediction;
            }

        } catch (Exception e) {
            System.out.println("Congestion ONNX predict failed: " + e.getMessage());
            return fallback(line, day, hour, "predict-error", station, normalizedStation);
        }
    }

    @PreDestroy
    public void close() throws Exception {
        if (session != null) {
            session.close();
        }
    }

    private int fallback(int line, int day, int hour, String reason, String station, String normalizedStation) {
        FallbackEntry entry = findFallback(line, day, hour);
        int prediction = entry != null ? entry.label() : -1;

        System.out.println(
                "[Congestion fallback] reason=" + reason
                        + ", station=" + station
                        + ", normalizedStation=" + normalizedStation
                        + ", hour=" + hour
                        + ", day=" + day
                        + ", line=" + line
                        + ", prediction=" + prediction
                        + (entry != null ? ", avg=" + entry.avgCongestion() + ", samples=" + entry.sampleCount() : "")
        );

        return prediction;
    }

    private FallbackEntry findFallback(int line, int day, int hour) {
        if (fallbackData == null) {
            return null;
        }

        FallbackEntry byLine = findFallbackEntry(fallbackData.byLine(), line, day, hour);
        if (byLine != null) {
            return byLine;
        }

        return findGlobalFallbackEntry(day, hour);
    }

    private FallbackEntry findFallbackEntry(
            Map<String, Map<String, Map<String, FallbackEntry>>> source,
            int line,
            int day,
            int hour
    ) {
        if (source == null) {
            return null;
        }

        Map<String, Map<String, FallbackEntry>> dayMap = source.get(String.valueOf(line));
        if (dayMap == null) {
            return null;
        }

        Map<String, FallbackEntry> hourMap = dayMap.get(String.valueOf(day));
        if (hourMap == null) {
            return null;
        }
        return hourMap.get(String.valueOf(hour));
    }

    private FallbackEntry findGlobalFallbackEntry(int day, int hour) {
        if (fallbackData.global() == null) {
            return null;
        }

        Map<String, FallbackEntry> hourMap = fallbackData.global().get(String.valueOf(day));
        if (hourMap == null) {
            return null;
        }

        return hourMap.get(String.valueOf(hour));
    }

    private String normalizeStation(String station) {
        if (station == null) {
            return "";
        }

        return station
                .replaceAll("\\s+", "")
                .replaceAll("\\(.*?\\)", "")
                .trim();
    }

    private StationMeta resolveStationMeta(String station) {
        if (station == null || station.isBlank() || stationMeta == null) {
            return null;
        }

        StationMeta exact = stationMeta.get(station);
        if (exact != null) {
            return exact;
        }

        if (station.endsWith("역")) {
            StationMeta withoutSuffix = stationMeta.get(station.substring(0, station.length() - 1));
            if (withoutSuffix != null) {
                return withoutSuffix;
            }
        }

        StationMeta withSuffix = stationMeta.get(station + "역");
        if (withSuffix != null) {
            return withSuffix;
        }

        for (Map.Entry<String, StationMeta> entry : stationMeta.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(station)
                    || entry.getKey().contains(station)
                    || station.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }

    private <T> T readJson(String path, TypeReference<T> typeReference) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new RuntimeException("file not found: " + path);
            }
            return objectMapper.readValue(is, typeReference);
        }
    }

    private byte[] loadBytes(String path) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new RuntimeException("file not found: " + path);
            }
            return is.readAllBytes();
        }
    }

    private record StationMeta(
            @JsonProperty("station_num") int stationNum,
            @JsonProperty("line_num") int lineNum
    ) {}

    private record FallbackData(
            @JsonProperty("by_line") Map<String, Map<String, Map<String, FallbackEntry>>> byLine,
            Map<String, Map<String, FallbackEntry>> global
    ) {}

    private record FallbackEntry(
            int label,
            @JsonProperty("avg_congestion") double avgCongestion,
            @JsonProperty("sample_count") int sampleCount
    ) {}
}
