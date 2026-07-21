package com.example.demo.transport.service;

import ai.onnxruntime.*;
import com.opencsv.CSVReader;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.util.*;

@Service
public class TransportMlService {

    private OrtEnvironment env;
    private OrtSession scalerSession;
    private OrtSession modelSession;

    private final Map<String, Float> transferTimeMap = new HashMap<>();

    @PostConstruct
    public void init() {
        try {
            env = OrtEnvironment.getEnvironment();

            scalerSession = env.createSession(
                    loadBytes("model/transport/scaler.onnx"),
                    new OrtSession.SessionOptions()
            );
            modelSession = env.createSession(
                    loadBytes("model/transport/route_model.onnx"),
                    new OrtSession.SessionOptions()
            );

            loadDfMeta();
            System.out.println("✅ TransportMlService 초기화 완료");

        } catch (Throwable e) {
            System.out.println("⚠️ TransportMlService 로드 실패 (무시됨)");
            e.printStackTrace();
        }
    }

    private void loadDfMeta() throws Exception {
        try (CSVReader reader = new CSVReader(new InputStreamReader(
                Objects.requireNonNull(
                        getClass().getClassLoader()
                                .getResourceAsStream("model/transport/df_meta.csv")),
                "UTF-8"))) {

            reader.readNext();
            String[] line;
            while ((line = reader.readNext()) != null) {
                if (line.length >= 2) {
                    transferTimeMap.put(line[0].trim(), Float.parseFloat(line[1].trim()));
                }
            }
        }
        System.out.println("df_meta.csv 로드 완료: " + transferTimeMap.size() + "개 역");
    }

    public float getTransferTime(String stationName) {
        return transferTimeMap.getOrDefault(stationName, 5.0f);
    }

    // 중복 제거 - try-catch 버전 하나만 유지
    public float getMlProb(float time, float transfer, float walk,
                           float transferTime, float subwayRatio,
                           float busRatio, float nSegments,
                           float avgStopCount) {
        try {
            float[] features = {
                    time, transfer, walk, transferTime,
                    subwayRatio, busRatio, nSegments, avgStopCount
            };
            float[] scaled = applyScaler(features);
            return applyModel(scaled);

        } catch (Exception e) {
            System.out.println("⚠️ Transport ML 예측 실패: " + e.getMessage());
            return 0.5f;
        }
    }

    private float[] applyScaler(float[] features) throws Exception {
        long[] shape = {1, features.length};
        OnnxTensor input = OnnxTensor.createTensor(
                env, FloatBuffer.wrap(features), shape
        );
        try (OrtSession.Result result = scalerSession.run(
                Collections.singletonMap("input", input))) {

            Object raw = result.get(0).getValue();

            if (raw instanceof float[][]) {
                return ((float[][]) raw)[0];
            } else if (raw instanceof List) {
                List<?> list = (List<?>) raw;
                float[] out = new float[list.size()];
                for (int i = 0; i < list.size(); i++) {
                    out[i] = ((Number) list.get(i)).floatValue();
                }
                return out;
            } else {
                OnnxTensor tensor = (OnnxTensor) result.get(0);
                return ((float[][]) tensor.getValue())[0];
            }
        }
    }

    private float applyModel(float[] scaledFeatures) throws Exception {
        long[] shape = {1, scaledFeatures.length};
        OnnxTensor input = OnnxTensor.createTensor(
                env, FloatBuffer.wrap(scaledFeatures), shape
        );
        try (OrtSession.Result result = modelSession.run(
                Collections.singletonMap("input", input))) {

            Object raw = result.get(1).getValue();

            if (raw instanceof List) {
                List<?> list = (List<?>) raw;
                Object first = list.get(0);

                if (first instanceof OnnxMap) {
                    OnnxMap onnxMap = (OnnxMap) first;
                    Map<?, ?> map = (Map<?, ?>) onnxMap.getValue();

                    Object prob = map.get(1L);
                    if (prob == null) prob = map.get(1);
                    if (prob == null) {
                        List<Object> vals = new ArrayList<>(map.values());
                        if (vals.size() >= 2) prob = vals.get(1);
                    }
                    return prob != null ? ((Number) prob).floatValue() : 0.5f;

                } else if (first instanceof List) {
                    List<?> inner = (List<?>) first;
                    return ((Number) inner.get(1)).floatValue();

                } else if (first instanceof float[]) {
                    return ((float[]) first)[1];
                }
            }

            if (raw instanceof float[][]) {
                return ((float[][]) raw)[0][1];
            }

            return 0.5f;
        }
    }

    public float calcScore(float time, float transfer, float walk,
                           float subwayRatio, float mlProb,
                           float minTime, String routeType) {
        float wMl, wTime, wTransfer, wWalk, wSubway;

        switch (routeType) {
            case "fast":
                wMl = 0.3f; wTime = 0.65f; wTransfer = 0.20f;
                wWalk = 0.10f; wSubway = 0.05f;
                break;
            case "comfort":
                wMl = 0.3f; wTime = 0.20f; wTransfer = 0.60f;
                wWalk = 0.15f; wSubway = 0.05f;
                break;
            default:
                wMl = 0.5f; wTime = 0.50f; wTransfer = 0.35f;
                wWalk = 0.10f; wSubway = 0.05f;
                break;
        }

        float heuristic = (minTime / time)       * wTime
                + (1f / (transfer + 1f)) * wTransfer
                + (1f / (walk + 1f))     * wWalk
                + subwayRatio             * wSubway;

        return mlProb * wMl + heuristic * (1f - wMl);
    }

    private byte[] loadBytes(String path) throws Exception {
        try (var is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) throw new RuntimeException("파일 없음: " + path);
            return is.readAllBytes();
        }
    }
}