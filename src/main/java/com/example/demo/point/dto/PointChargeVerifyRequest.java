package com.example.demo.point.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PointChargeVerifyRequest {

    @NotBlank
    private String impUid;

    @NotBlank
    private String merchantUid;
}
