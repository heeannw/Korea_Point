package com.example.demo.user.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 국세청 키 없이 도는 부분(체크섬)만 확인한다.
 * 키를 넣지 않으면 조회 단계는 건너뛰므로 네트워크 없이 그대로 돌아간다.
 */
class BusinessNumberValidatorTest {

    private final BusinessNumberValidator validator = new BusinessNumberValidator();

    @Test
    @DisplayName("실재하는 번호는 하이픈이 있어도 숫자 10자리로 정리된다")
    void 정상_번호() {
        assertThat(validator.validate("124-81-00998")).isEqualTo("1248100998");
        assertThat(validator.validate("220-81-62517")).isEqualTo("2208162517");
        assertThat(validator.validate(" 124 81 00998 ")).isEqualTo("1248100998");
    }

    @Test
    @DisplayName("체크섬이 맞지 않는 번호는 막는다")
    void 체크섬_불일치() {
        assertThatThrownBy(() -> validator.validate("123-45-67890"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("올바르지 않은");
    }

    @Test
    @DisplayName("자릿수가 열 자리가 아니면 막는다")
    void 자릿수_오류() {
        assertThatThrownBy(() -> validator.validate("124-81-0099"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10자리");
    }

    @Test
    @DisplayName("비어 있으면 입력을 요청한다")
    void 빈_값() {
        assertThatThrownBy(() -> validator.validate("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("입력해");
    }
}
