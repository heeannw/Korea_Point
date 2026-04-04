package com.example.demo.transport.service;

import ai.onnxruntime.*;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.Collections;

@Service
public class CongestionMlService {

    private OrtEnvironment env;
    private OrtSession scalerSession;
    private OrtSession modelSession;

    @PostConstruct
    public void init() throws Exception{
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

    public float predict(float stationEnc, float hour, float day) throws OrtException {
        float [] inputArr = {stationEnc, hour, day};

        float [] scaled = runScaler(inputArr);

        return runModel(scaled);
    }

    private float runModel(float[] scaled) throws OrtException {
        long[] shape = {1, scaled.length};

        OnnxTensor input = OnnxTensor.createTensor(env, FloatBuffer.wrap(scaled), shape);

        try(OrtSession.Result result = modelSession.run(
                Collections.singletonMap("input",input))){

            Object raw = result.get(1).getValue();
            if(raw == null) raw = result.get(0).getValue();

            if(raw instanceof java.util.List<?> list){
                Object first = list.get(0);

                if(first instanceof OnnxMap map){
                    Object val = map.getValue().get(2L);
                    if(val == null) val = map.getValue().get(2);
                    if(val == null) val = map.getValue().values().iterator().next();
                    return((Number) val).floatValue();
                }
            }

            return 0.5f;
        }
    }

    private float[] runScaler(float[] inputArr) throws OrtException {
        long[] shape = {1, inputArr.length};

        OnnxTensor input = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputArr), shape);

        try(OrtSession.Result result = scalerSession.run(
                Collections.singletonMap("input",input))){
            return ((float[][]) result.get(0).getValue())[0];
        }
    }


    private byte[] loadBytes(String path) throws IOException {
        try(var is = getClass().getClassLoader().getResourceAsStream(path)){
            if(is == null) throw new RuntimeException("파일 없음: "+ path);
            return is.readAllBytes();
        }
    }
}
