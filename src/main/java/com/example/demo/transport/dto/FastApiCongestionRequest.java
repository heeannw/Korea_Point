package com.example.demo.transport.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FastApiCongestionRequest {
    private int hour;
    private int day;
    private String station;
    private int line;
}
