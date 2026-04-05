package com.example.demo.transport.service;

import ai.onnxruntime.*;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class CongestionMlService {

    private OrtEnvironment env;
    private OrtSession scalerSession;
    private OrtSession modelSession;

    @PostConstruct
    public void init() throws Exception {
        env = OrtEnvironment.getEnvironment();

        scalerSession = env.createSession(
                loadBytes("model/transport/congestion_scaler.onnx"),
                new OrtSession.SessionOptions()
        );
        modelSession = env.createSession(
                loadBytes("model/transport/congestion_model.onnx"),
                new OrtSession.SessionOptions()
        );

        System.out.println("혼잡도 모델 로드 완료");
    }

    // -----------------------------------------------
    // 혼잡도 예측
    // 반환값: 0=여유, 1=보통, 2=혼잡
    // -----------------------------------------------
    public int predict(float stationEnc, float hour, float day) throws OrtException {
        float[] scaled = runScaler(new float[]{stationEnc, hour, day});
        return runModel(scaled);
    }

    private int runModel(float[] scaled) throws OrtException {
        long[] shape = {1, scaled.length};
        OnnxTensor input = OnnxTensor.createTensor(env, FloatBuffer.wrap(scaled), shape);

        try (OrtSession.Result result = modelSession.run(
                Collections.singletonMap("input", input))) {

            // output_label (index 0) → 0=여유, 1=보통, 2=혼잡
            Object labelRaw = result.get(0).getValue();
            if (labelRaw instanceof long[]) {
                return (int) ((long[]) labelRaw)[0];
            }

            // 혹시 확률로 나오면 가장 높은 클래스 선택
            Object probRaw = result.get(1).getValue();
            if (probRaw instanceof List<?> list && !list.isEmpty()) {
                Object first = list.get(0);
                if (first instanceof OnnxMap onnxMap) {
                    Map<?, ?> map = (Map<?, ?>) onnxMap.getValue();
                    int bestLabel = 0;
                    float bestProb = -1f;
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        float prob = ((Number) entry.getValue()).floatValue();
                        int label = ((Number) entry.getKey()).intValue();
                        if (prob > bestProb) {
                            bestProb = prob;
                            bestLabel = label;
                        }
                    }
                    return bestLabel;
                }
            }

            return 1; // 기본값: 보통
        }
    }

    private float[] runScaler(float[] inputArr) throws OrtException {
        long[] shape = {1, inputArr.length};
        OnnxTensor input = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputArr), shape);

        try (OrtSession.Result result = scalerSession.run(
                Collections.singletonMap("input", input))) {
            return ((float[][]) result.get(0).getValue())[0];
        }
    }

    private byte[] loadBytes(String path) throws IOException {
        try (var is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) throw new RuntimeException("파일 없음: " + path);
            return is.readAllBytes();
        }
    }
}