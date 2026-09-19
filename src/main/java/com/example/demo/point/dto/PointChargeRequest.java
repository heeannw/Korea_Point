package com.example.demo.point.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PointChargeRequest {

    @NotNull
    private Integer amount;
}
