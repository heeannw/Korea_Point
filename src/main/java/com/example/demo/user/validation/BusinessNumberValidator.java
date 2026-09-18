package com.example.demo.user.validation;

import com.example.demo.user.exception.UserInputException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * 사업자등록번호 검증.
 *
 * <p>부를 수 있는 길이 둘이다. 받아둔 정보가 얼마나 되느냐로 갈린다.
 * <ul>
 *   <li>{@link #validate(String)} - 번호만 있을 때. 실재하는 번호인지, 폐업·휴업은 아닌지까지.
 *       <b>번호만 맞으면 아무 이름으로나 통과한다.</b></li>
 *   <li>{@link #validateOwner(String, String, String)} - 대표자명과 개업일자까지 있을 때.
 *       셋이 국세청 기록과 전부 맞아야 통과한다. 화면에서 두 값을 받게 되면 이쪽으로 바꾼다.</li>
 * </ul>
 *
 * <p>어느 쪽이든 두 단계를 거친다.
 * <ol>
 *   <li>체크섬 - 번호 열 자리에 들어 있는 검증 규칙. 오타나 아무 숫자는 여기서 걸린다.
 *       외부 호출이 없어 항상 동작한다.</li>
 *   <li>국세청 조회 - 키({@code nts.business.service-key})가 없으면 이 단계는 건너뛴다.</li>
 * </ol>
 *
 * <p>여기까지가 자동으로 되는 최대치다. 대표자명까지 맞춰도
 * "지금 신청하는 사람이 그 대표자다"는 끝내 밝히지 못한다(그건 휴대폰 실명확인의 몫이다).
 * 그래서 서류를 받아 관리자가 최종 승인하는 단계가 뒤에 그대로 남아 있다.
 *
 * <p>막을 때는 {@link IllegalArgumentException}을 던진다. 회원가입 컨트롤러가
 * 이 예외를 잡아 {@code signupError}로 화면에 그대로 띄운다.
 */
@Component
public class BusinessNumberValidator {

    private static final Logger log = LoggerFactory.getLogger(BusinessNumberValidator.class);

    /** 국세청이 정한 자릿수별 가중치. 열 번째 자리는 검증용이라 빠진다. */
    private static final int[] WEIGHTS = {1, 3, 7, 1, 3, 7, 1, 3, 5};

    private static final String BASE_URL = "https://api.odcloud.kr/api/nts-businessman/v1";

    /** 상태조회 - 번호만 보내고 계속사업자·휴업·폐업만 돌려받는다. */
    private static final String STATUS_URL = BASE_URL + "/status";

    /** 진위확인 - 번호·개업일자·대표자명이 국세청 기록과 맞는지까지 본다. */
    private static final String VALIDATE_URL = BASE_URL + "/validate";

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    private final RestClient client = RestClient.create();

    @Value("${nts.business.service-key:}")
    private String serviceKey;

    /** 국세청 기록과 맞다고 확인된 값들. 신청서에 그대로 저장하면 된다. */
    public record VerifiedBusiness(String businessNumber, String startDate, String ownerName) {
    }

    /**
     * 번호만으로 검증한다.
     *
     * @return 하이픈과 공백을 뺀 숫자 열 자리
     * @throws IllegalArgumentException 형식이 틀렸거나 국세청에 없는 번호일 때
     */
    public String validate(String raw) {
        String normalized = normalize(raw);
        checkExistsAtNts(normalized);
        return normalized;
    }

    /**
     * 대표자명·개업일자까지 국세청 기록과 대조한다.
     *
     * <p>국세청은 <b>보낸 항목이 전부 일치할 때만</b> 통과시킨다. 그래서 상호·업태·주소 같은
     * 선택 항목은 일부러 보내지 않는다. 보내면 검증이 세지는 게 아니라, 표기가 조금 다른
     * 멀쩡한 사장님이 떨어진다. 막아야 할 사람은 아래 셋에서 이미 걸린다.
     *
     * @param raw       사업자등록번호. 하이픈이 있어도 된다
     * @param startDate 개업일자. 사업자등록증의 개업연월일, YYYYMMDD
     * @param ownerName 대표자명. 사업자등록증에 적힌 그대로
     * @throws IllegalArgumentException 형식이 틀렸거나 국세청 기록과 맞지 않을 때
     */
    public VerifiedBusiness validateOwner(String raw, String startDate, String ownerName) {
        String normalizedNumber = normalize(raw);
        String normalizedStartDate = normalizeStartDate(startDate);
        String normalizedOwnerName = normalizeOwnerName(ownerName);

        checkMatchesAtNts(normalizedNumber, normalizedStartDate, normalizedOwnerName);

        return new VerifiedBusiness(normalizedNumber, normalizedStartDate, normalizedOwnerName);
    }

    /** 하이픈·공백을 걷어내고 열 자리 숫자와 체크섬까지 확인한다. 외부 호출 없음. */
    public String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new UserInputException("business.error.numberRequired", "사업자등록번호를 입력해 주세요.");
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() != 10) {
            throw new UserInputException("business.error.numberDigits", "사업자등록번호는 숫자 10자리입니다. 다시 확인해 주세요.");
        }
        if (!hasValidChecksum(digits)) {
            throw new UserInputException("business.error.numberInvalid", "올바르지 않은 사업자등록번호입니다. 다시 확인해 주세요.");
        }
        return digits;
    }

    /**
     * 개업일자를 숫자 여덟 자리로 정리한다. 외부 호출 없음.
     *
     * <p>2월 30일 같은 없는 날짜와 미래 날짜는 여기서 걸러낸다. 국세청에 물어봐야
     * "맞지 않다"고만 답하지 무엇이 틀렸는지는 알려주지 않기 때문에, 알 수 있는 건 미리 알려준다.
     */
    public String normalizeStartDate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new UserInputException("business.error.startDateRequired", "개업일자를 입력해 주세요. 사업자등록증의 개업연월일입니다.");
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() != 8) {
            throw new UserInputException("business.error.startDateDigits", "개업일자는 숫자 8자리입니다. 2020년 3월 5일이면 20200305 처럼 입력해 주세요.");
        }
        LocalDate parsed;
        try {
            parsed = LocalDate.parse(digits, YYYYMMDD);
        } catch (DateTimeParseException e) {
            throw new UserInputException("business.error.startDateInvalid", "올바르지 않은 개업일자입니다. 다시 확인해 주세요.");
        }
        if (parsed.isAfter(LocalDate.now())) {
            throw new UserInputException("business.error.startDateFuture", "개업일자가 미래로 되어 있습니다. 다시 확인해 주세요.");
        }
        return parsed.format(YYYYMMDD);
    }

    /**
     * 대표자명을 다듬는다. 외부 호출 없음.
     *
     * <p>앞뒤 공백만 걷어낸다. 가운데 공백은 그대로 둔다 - 외국인 사업자는 영문명으로
     * 등록돼 있어 이름 사이 공백이 기록의 일부다.
     */
    public String normalizeOwnerName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new UserInputException("business.error.ownerNameRequired", "대표자명을 입력해 주세요. 사업자등록증에 적힌 대로 입력해 주세요.");
        }
        return raw.trim();
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
     * 국세청에 실재하는 번호인지 확인한다(상태조회).
     *
     * <p>키가 없으면 아무것도 하지 않는다. 조회 자체가 실패하면(연결 끊김, 국세청 점검 등)
     * 통과시킨다. 우리 쪽 사정으로 멀쩡한 사장님의 가입을 막을 수는 없고,
     * 뒤에 서류 심사가 남아 있기 때문이다. 반대로 "폐업"처럼 국세청이 분명하게
     * 답한 경우에만 막는다.
     */
    private void checkExistsAtNts(String businessNumber) {
        if (missingServiceKey("사업자번호를 체크섬까지만 검사한다")) {
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

        // 상태조회는 상태 필드가 data 첫 건에 그대로 들어 있다.
        Map<?, ?> entry = firstEntry(body);
        if (entry != null) {
            checkOperating(entry);
        }
    }

    /**
     * 번호·개업일자·대표자명이 국세청 기록과 맞는지 확인한다(진위확인).
     *
     * <p>실패했을 때의 태도는 상태조회와 같다. 우리 쪽 사정이면 통과시키고,
     * 국세청이 "맞지 않다"고 분명하게 답했을 때만 막는다.
     *
     * <p>국세청은 셋이 전부 맞으면 상태조회 결과까지 함께 돌려준다. 그래서 이 길로
     * 들어오면 폐업·휴업을 보려고 따로 한 번 더 부를 필요가 없다.
     */
    private void checkMatchesAtNts(String businessNumber, String startDate, String ownerName) {
        if (missingServiceKey("대표자명·개업일자 대조를 건너뛰고 체크섬까지만 검사한다")) {
            return;
        }

        Map<?, ?> body;
        try {
            body = client.post()
                    .uri(VALIDATE_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Infuser " + serviceKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("businesses", List.of(Map.of(
                            "b_no", businessNumber,
                            "start_dt", startDate,
                            "p_nm", ownerName))))
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            log.warn("국세청 사업자 진위확인 실패 - 형식 검증만 거쳐 통과시킨다: {}", e.getMessage());
            return;
        }

        Map<?, ?> entry = firstEntry(body);
        if (entry == null) {
            return;
        }

        // 01=일치, 02=불일치. 셋 중 무엇이 틀렸는지는 알려주지 않으니 안내 문구로 대신한다.
        if (!"01".equals(str(entry.get("valid")))) {
            log.info("국세청 진위확인 불일치 - 번호 {}, 응답 {}", businessNumber, str(entry.get("valid_msg")));
            throw new UserInputException("business.error.mismatch", "사업자등록증의 대표자명 또는 개업일자와 맞지 않습니다. 등록증에 적힌 대로 입력해 주세요.");
        }

        if (entry.get("status") instanceof Map<?, ?> status) {
            checkOperating(status);
        }
    }

    /** 계속사업자가 아니면 막는다. 상태 필드({@code b_stt_cd})가 담긴 덩어리를 받는다. */
    private void checkOperating(Map<?, ?> status) {
        switch (str(status.get("b_stt_cd"))) {
            case "01" -> { /* 계속사업자 */ }
            case "02" -> throw new UserInputException("business.error.suspended", "휴업 중인 사업자등록번호입니다.");
            case "03" -> throw new UserInputException("business.error.closed", "폐업한 사업자등록번호입니다.");
            default -> throw new UserInputException("business.error.notRegistered", "국세청에 등록되지 않은 사업자등록번호입니다.");
        }
    }

    /**
     * 키가 없으면 그 사실을 로그로 남기고 {@code true}를 준다.
     *
     * <p>조용히 넘어가면 "체크섬은 맞지만 실재하지 않는 번호"가 그대로 통과해
     * 검증이 고장 난 것처럼 보인다. 어느 쪽인지 로그로 남긴다.
     */
    private boolean missingServiceKey(String whatWeDoInstead) {
        if (serviceKey != null && !serviceKey.isBlank()) {
            return false;
        }
        log.info("국세청 키가 없어 {} "
                        + "(TOUR_API_SERVICE_KEY 또는 NTS_BUSINESS_SERVICE_KEY 를 설정하면 조회까지 한다)",
                whatWeDoInstead);
        return true;
    }

    /** 응답의 {@code data} 첫 건을 꺼낸다. 읽지 못하면 통과시킨다는 뜻으로 {@code null}. */
    private Map<?, ?> firstEntry(Map<?, ?> body) {
        Object data = body == null ? null : body.get("data");
        if (data instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> entry) {
            return entry;
        }
        log.warn("국세청 응답을 읽지 못했다 - 통과시킨다: {}", body);
        return null;
    }

    private String str(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
