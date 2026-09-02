package com.example.demo.user.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 사업자등록번호 검증.
 *
 * <p>두 단계로 거른다.
 * <ol>
 *   <li>체크섬 - 번호 열 자리에 들어 있는 검증 규칙. 오타나 아무 숫자는 여기서 걸린다.
 *       외부 호출이 없어 항상 동작한다.</li>
 *   <li>국세청 조회 - 실재하는 번호인지, 폐업·휴업은 아닌지 확인한다.
 *       키({@code nts.business.service-key})가 없으면 이 단계는 건너뛴다.</li>
 * </ol>
 *
 * <p>여기까지가 자동으로 되는 최대치다. "이 번호가 진짜다"는 밝혀도
 * "지금 신청하는 사람이 그 대표자다"는 밝히지 못한다. 그래서 서류를 받아
 * 관리자가 최종 승인하는 단계가 뒤에 그대로 남아 있다.
 *
 * <p>막을 때는 {@link IllegalArgumentException}을 던진다. 회원가입 컨트롤러가
 * 이 예외를 잡아 {@code signupError}로 화면에 그대로 띄운다.
 */
@Component
public class BusinessNumberValidator {

    private static final Logger log = LoggerFactory.getLogger(BusinessNumberValidator.class);

    /** 국세청이 정한 자릿수별 가중치. 열 번째 자리는 검증용이라 빠진다. */
    private static final int[] WEIGHTS = {1, 3, 7, 1, 3, 7, 1, 3, 5};

    private static final String STATUS_URL =
            "https://api.odcloud.kr/api/nts-businessman/v1/status";

    private final RestClient client = RestClient.create();

    @Value("${nts.business.service-key:}")
    private String serviceKey;

    /**
     * 번호를 숫자 열 자리로 정리하고 검증한다.
     *
     * @return 하이픈과 공백을 뺀 숫자 열 자리
     * @throws IllegalArgumentException 형식이 틀렸거나 국세청에 없는 번호일 때
     */
    public String validate(String raw) {
        String normalized = normalize(raw);
        checkExistsAtNts(normalized);
        return normalized;
    }

    /** 하이픈·공백을 걷어내고 열 자리 숫자와 체크섬까지 확인한다. 외부 호출 없음. */
    public String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("사업자등록번호를 입력해 주세요.");
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() != 10) {
            throw new IllegalArgumentException("사업자등록번호는 숫자 10자리입니다. 다시 확인해 주세요.");
        }
        if (!hasValidChecksum(digits)) {
            throw new IllegalArgumentException("올바르지 않은 사업자등록번호입니다. 다시 확인해 주세요.");
        }
        return digits;
    }

    /**
     * 사업자등록번호 열 자리에 들어 있는 검증 규칙.
     *
     * <p>앞 아홉 자리에 가중치를 곱해 더하고, 아홉 번째 자리에 5를 곱한 값의
     * 십의 자리를 보탠다. 10에서 그 합의 일의 자리를 뺀 값이 마지막 자리와 같아야 한다.
     */
    private boolean hasValidChecksum(String digits) {
        int sum = 0;
        for (int i = 0; i < WEIGHTS.length; i++) {
            sum += (digits.charAt(i) - '0') * WEIGHTS[i];
        }
        sum += ((digits.charAt(8) - '0') * 5) / 10;
        int expected = (10 - (sum % 10)) % 10;
        return expected == (digits.charAt(9) - '0');
    }

    /**
     * 국세청에 실재하는 번호인지 확인한다.
     *
     * <p>키가 없으면 아무것도 하지 않는다. 조회 자체가 실패하면(연결 끊김, 국세청 점검 등)
     * 통과시킨다. 우리 쪽 사정으로 멀쩡한 사장님의 가입을 막을 수는 없고,
     * 뒤에 서류 심사가 남아 있기 때문이다. 반대로 "폐업"처럼 국세청이 분명하게
     * 답한 경우에만 막는다.
     */
    private void checkExistsAtNts(String businessNumber) {
        if (serviceKey == null || serviceKey.isBlank()) {
            return;
        }

        Map<?, ?> body;
        try {
            body = client.post()
                    .uri(STATUS_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Infuser " + serviceKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("b_no", List.of(businessNumber)))
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            log.warn("국세청 사업자 조회 실패 - 형식 검증만 거쳐 통과시킨다: {}", e.getMessage());
            return;
        }

        Object data = body == null ? null : body.get("data");
        if (!(data instanceof List<?> list) || list.isEmpty()
                || !(list.get(0) instanceof Map<?, ?> entry)) {
            log.warn("국세청 사업자 조회 응답을 읽지 못했다 - 통과시킨다: {}", body);
            return;
        }

        String statusCode = str(entry.get("b_stt_cd"));
        switch (statusCode) {
            case "01" -> { /* 계속사업자 */ }
            case "02" -> throw new IllegalArgumentException("휴업 중인 사업자등록번호입니다.");
            case "03" -> throw new IllegalArgumentException("폐업한 사업자등록번호입니다.");
            default -> throw new IllegalArgumentException("국세청에 등록되지 않은 사업자등록번호입니다.");
        }
    }

    private String str(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
