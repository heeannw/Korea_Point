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

    // df_meta.csv → 역명:환승시간 맵
    private final Map<String, Float> transferTimeMap = new HashMap<>();

    // features 순서 (변환 결과 확인됨)
    // ['time', 'transfer', 'walk', 'transfer_time',
    //  'subway_ratio', 'bus_ratio', 'n_segments', 'avg_stop_count']

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

    // df_meta.csv 로드
    private void loadDfMeta() throws Exception {
        try (CSVReader reader = new CSVReader(new InputStreamReader(
                Objects.requireNonNull(
                        getClass().getClassLoader()
                                .getResourceAsStream("model/transport/df_meta.csv")),
                "UTF-8"))) {

            reader.readNext(); // 헤더 스킵
            String[] line;
            while ((line = reader.readNext()) != null) {
                if (line.length >= 2) {
                    String stationName  = line[0].trim();
                    float  transferTime = Float.parseFloat(line[1].trim());
                    transferTimeMap.put(stationName, transferTime);
                }
            }
        }
        System.out.println("df_meta.csv 로드 완료: " + transferTimeMap.size() + "개 역");
    }

    // 환승시간 조회
    public float getTransferTime(String stationName) {
        return transferTimeMap.getOrDefault(stationName, 5.0f);
    }

    // ML 확률값 계산 (features 8개)
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
            float[][] output = (float[][]) result.get(0).getValue();
            return output[0];
        }
    }

    private float applyModel(float[] scaledFeatures) throws Exception {
        long[] shape = {1, scaledFeatures.length};
        OnnxTensor input = OnnxTensor.createTensor(
                env, FloatBuffer.wrap(scaledFeatures), shape
        );
        try (OrtSession.Result result = modelSession.run(
                Collections.singletonMap("input", input))) {
            float[][] proba = (float[][]) result.get(1).getValue();
            return proba[0][1];
        }
    }

    // Python calc_score 완전 동일 로직
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
            default: // recommend
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

    // 파일 로드 헬퍼
    private byte[] loadBytes(String path) throws Exception {
        try (var is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) throw new RuntimeException("파일 없음: " + path);
            return is.readAllBytes();
        }
    }
}