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
    public void init() throws Exception {
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
        System.out.println("TransportMlService 초기화 완료");
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

    public float getMlProb(float time, float transfer, float walk,
                           float transferTime, float subwayRatio,
                           float busRatio, float nSegments,
                           float avgStopCount) throws Exception {

        float[] features = {
                time, transfer, walk, transferTime,
                subwayRatio, busRatio, nSegments, avgStopCount
        };

        float[] scaled = applyScaler(features);
        return applyModel(scaled);
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
                // OnnxTensor로 직접 읽기
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

            // result.get(1) = List 안에 OnnxMap이 들어있는 구조
            Object raw = result.get(1).getValue();
            System.out.println("raw 타입: " + raw.getClass().getName());

            // List 안에 OnnxMap
            if (raw instanceof List) {
                List<?> list = (List<?>) raw;
                Object first = list.get(0);
                System.out.println("first 타입: " + first.getClass().getName());

                if (first instanceof OnnxMap) {
                    // 핵심: List 안의 OnnxMap 처리
                    OnnxMap onnxMap = (OnnxMap) first;
                    Map<?, ?> map = (Map<?, ?>) onnxMap.getValue();
                    System.out.println("map 내용: " + map);

                    // 클래스 1의 확률값
                    Object prob = map.get(1L);
                    if (prob == null) prob = map.get(1);
                    if (prob == null) {
                        // 값이 뭔지 모르면 두 번째 값 가져오기
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

            System.out.println("처리 못한 타입: " + raw.getClass().getName());
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