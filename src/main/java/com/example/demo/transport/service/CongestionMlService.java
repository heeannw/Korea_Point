package com.example.demo.transport.service;

import ai.onnxruntime.*;
import org.springframework.stereotype.Service;

import java.nio.FloatBuffer;
import java.util.Collections;
import java.io.InputStream;


@Service
public class CongestionMlService {

    private OrtEnvironment env;
    private OrtSession session;

    public CongestionMlService() throws Exception {
        env = OrtEnvironment.getEnvironment();

        System.out.println("🔥 ONNX 로딩 시작");

        InputStream is = getClass()
                .getClassLoader()
                .getResourceAsStream("model/transport/transport_congestion_model.onnx");

        if (is == null) {
            throw new RuntimeException("❌ ONNX 파일 못찾음 (경로 확인)");
        }

        System.out.println("🔥 파일 찾음");

        byte[] modelBytes = is.readAllBytes();

        System.out.println("🔥 바이트 로드 완료");

        session = env.createSession(modelBytes, new OrtSession.SessionOptions());

        System.out.println("🔥 ONNX 세션 생성 완료");
    }

    public int predict(int hour, int day, int line, int stationNum) {
        try {
            float[] inputData = new float[]{ hour, day, line, stationNum };

            OnnxTensor inputTensor = OnnxTensor.createTensor(
                    env,
                    FloatBuffer.wrap(inputData),
                    new long[]{1, 4}
            );

            OrtSession.Result result = session.run(
                    Collections.singletonMap("float_input", inputTensor)
            );

            Object value = result.get(0).getValue();

            // long[] 타입 대응
            if (value instanceof long[]) {
                return (int) ((long[]) value)[0];
            }
            // float[][] 타입 대응
            if (value instanceof float[][]) {
                return (int) ((float[][]) value)[0][0];
            }
            // long[][] 타입 대응
            if (value instanceof long[][]) {
                return (int) ((long[][]) value)[0][0];
            }

            return 1; // fallback

        } catch (Exception e) {
            e.printStackTrace();
            return 1;
        }
    }

}