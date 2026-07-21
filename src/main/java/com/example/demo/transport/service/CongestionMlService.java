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
import java.util.Locale;
import java.util.Map;

@Service
public class CongestionMlService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OrtEnvironment env;
    private ModelBundle seoulModel;
    private ModelBundle busanModel;

    @PostConstruct
    public void init() {
        try {
            env = OrtEnvironment.getEnvironment();

            seoulModel = loadModelBundle(
                    "seoul",
                    "model/transport/transport_congestion.onnx",
                    "model/transport/station_meta.json",
                    "model/transport/line_hour_fallback.json"
            );

            busanModel = loadModelBundle(
                    "busan",
                    "model/transport/busan_transport_congestion.onnx",
                    "model/transport/busan_station_meta.json",
                    "model/transport/busan_line_hour_fallback.json"
            );

            System.out.println(
                    "CongestionMlService ONNX ready: seoulStations=" + seoulModel.stationMeta().size()
                            + ", busanStations=" + busanModel.stationMeta().size()
            );

        } catch (Throwable e) {
            System.out.println("CongestionMlService ONNX load failed");
            e.printStackTrace();
        }
    }

    public int predict(int hour, int day, String station, int line) {
        return predict("seoul", hour, day, station, line);
    }

    public int predict(String city, int hour, int day, String station, int line) {
        return predict(city, hour, day, station, null, line);
    }

    public int predict(String city, int hour, int day, String station, Integer odsayStationId, int line) {
        ModelBundle model = resolveModel(city);
        String normalizedStation = normalizeStation(station);

        try {
            if (model == null || model.session() == null || model.stationMeta() == null) {
                return fallback(model, line, day, hour, "onnx-not-ready", station, normalizedStation);
            }

            StationMeta meta = resolveStationMeta(model, normalizedStation, odsayStationId, line);
            int lineNum = line > 0 ? line : (meta != null ? meta.lineNum() : 0);

            if (meta == null) {
                return fallback(model, lineNum, day, hour, "station-not-found", station, normalizedStation);
            }

            int stationNum = meta.stationNum();
            float[][] input = new float[][]{
                    {hour, day, lineNum, stationNum}
            };

            try (OnnxTensor tensor = OnnxTensor.createTensor(env, input);
                 OrtSession.Result result = model.session().run(Map.of("input", tensor))) {

                long[] labels = (long[]) result.get(0).getValue();
                int prediction = (int) labels[0];

                System.out.println(
                        "[Congestion ONNX] city=" + model.city()
                                + ", station=" + station
                                + ", normalizedStation=" + normalizedStation
                                + ", odsayStationId=" + odsayStationId
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
            return fallback(model, line, day, hour, "predict-error", station, normalizedStation);
        }
    }

    @PreDestroy
    public void close() throws Exception {
        closeModel(seoulModel);
        closeModel(busanModel);
    }

    private ModelBundle loadModelBundle(String city, String onnxPath, String stationMetaPath, String fallbackPath)
            throws Exception {
        OrtSession session = env.createSession(loadBytes(onnxPath), new OrtSession.SessionOptions());
        Map<String, StationMeta> stationMeta = readJson(
                stationMetaPath,
                new TypeReference<Map<String, StationMeta>>() {}
        );
        FallbackData fallbackData = readJson(
                fallbackPath,
                new TypeReference<FallbackData>() {}
        );
        return new ModelBundle(city, session, stationMeta, fallbackData);
    }

    private ModelBundle resolveModel(String city) {
        String normalizedCity = city == null ? "" : city.trim().toLowerCase(Locale.ROOT);
        if (normalizedCity.equals("busan")) {
            return busanModel;
        }
        return seoulModel;
    }

    private void closeModel(ModelBundle model) throws Exception {
        if (model != null && model.session() != null) {
            model.session().close();
        }
    }

    private int fallback(
            ModelBundle model,
            int line,
            int day,
            int hour,
            String reason,
            String station,
            String normalizedStation
    ) {
        FallbackEntry entry = findFallback(model, line, day, hour);
        int prediction = entry != null ? entry.label() : -1;

        System.out.println(
                "[Congestion fallback] reason=" + reason
                        + ", city=" + (model != null ? model.city() : "unknown")
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

    private FallbackEntry findFallback(ModelBundle model, int line, int day, int hour) {
        if (model == null || model.fallbackData() == null) {
            return null;
        }

        FallbackEntry byLine = findFallbackEntry(model.fallbackData().byLine(), line, day, hour);
        if (byLine != null) {
            return byLine;
        }

        return findGlobalFallbackEntry(model.fallbackData(), day, hour);
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

    private FallbackEntry findGlobalFallbackEntry(FallbackData fallbackData, int day, int hour) {
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

    private StationMeta resolveStationMeta(ModelBundle model, String station, Integer odsayStationId, int line) {
        if (station == null || station.isBlank() || model == null || model.stationMeta() == null) {
            return null;
        }

        if (odsayStationId != null && odsayStationId > 0) {
            StationMeta byOdsayId = resolveStationMetaByOdsayId(model, odsayStationId, line);
            if (byOdsayId != null) {
                return byOdsayId;
            }
        }

        StationMeta exact = model.stationMeta().get(station);
        if (exact != null) {
            return exact;
        }

        if (line > 0) {
            StationMeta lineStation = model.stationMeta().get(line + ":" + station);
            if (lineStation != null) {
                return lineStation;
            }
        }

        for (Map.Entry<String, StationMeta> entry : model.stationMeta().entrySet()) {
            String key = entry.getKey();
            if (key.equalsIgnoreCase(station)
                    || key.contains(station)
                    || station.contains(key)
                    || key.endsWith(":" + station)) {
                return entry.getValue();
            }
        }

        return null;
    }

    private StationMeta resolveStationMetaByOdsayId(ModelBundle model, int odsayStationId, int line) {
        StationMeta fallback = null;

        for (StationMeta meta : model.stationMeta().values()) {
            if (meta.stationId() == null || meta.stationId() != odsayStationId) {
                continue;
            }

            if (line <= 0 || meta.lineNum() == line) {
                return meta;
            }

            if (fallback == null) {
                fallback = meta;
            }
        }

        return fallback;
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

    private record ModelBundle(
            String city,
            OrtSession session,
            Map<String, StationMeta> stationMeta,
            FallbackData fallbackData
    ) {}

    private record StationMeta(
            @JsonProperty("station_num") int stationNum,
            @JsonProperty("line_num") int lineNum,
            @JsonProperty("station_id") Integer stationId
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
