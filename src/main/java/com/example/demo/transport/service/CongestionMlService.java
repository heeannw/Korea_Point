package com.example.demo.transport.service;

import ai.onnxruntime.*;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.Map;

@Service
public class CongestionMlService {

    private OrtEnvironment env;
    private OrtSession modelSession;

    // 모델 로드 (scaler 없음)
    @PostConstruct
    public void init() throws Exception {
        env = OrtEnvironment.getEnvironment();

        modelSession = env.createSession(
                loadBytes("model/transport/congestion_model.onnx"),
                new OrtSession.SessionOptions()
        );

        System.out.println("혼잡도 모델 로드 완료");
    }

    // 혼잡도 예측 (0=여유, 1=보통, 2=혼잡)
    public int predict(float station, float hour, float day) throws OrtException {
        float[] inputArr = new float[]{station, hour, day};

        long[] shape = {1, inputArr.length};
        OnnxTensor input = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(inputArr),
                shape
        );

        try (OrtSession.Result result = modelSession.run(
                Collections.singletonMap("input", input))) {

            Object value = result.get(0).getValue();

            if (value instanceof long[]) {
                return (int) ((long[]) value)[0];
            }

            if (value instanceof float[][] probs) {
                int bestIndex = 0;
                float max = probs[0][0];

                for (int i = 1; i < probs[0].length; i++) {
                    if (probs[0][i] > max) {
                        max = probs[0][i];
                        bestIndex = i;
                    }
                }
                return bestIndex;
            }

            return 1; // 기본값
        }
    }

    // 리소스 파일 읽기
    private byte[] loadBytes(String path) throws IOException {
        try (var is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) throw new RuntimeException("파일 없음: " + path);
            return is.readAllBytes();
        }
    }
}