package com.example.demo.reservation.dto;

import com.example.demo.reservation.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReservationPaymentRequest {

    @NotNull(message = "결제수단을 선택해 주세요.")
    private PaymentMethod paymentMethod;
}
