package com.example.demo.transport.dto;

import java.util.List;

public class TransportRouteDto {

    private String routeId;
    private String type;
    private String start;
    private String end;
    private int totalTime;
    private int transferCount;
    private double walkTime;
    private String departTime;
    private List<TransportStepDto> steps;
    private Double startLat;
    private Double startLng;
    private Double endLat;
    private Double endLng;
    /** 택시 탭 전용 — TMAP 자동차 경로안내가 계산해주는 예상 택시 요금(원). 다른 수단은 null. */
    private Integer fareWon;

    public String getRouteId() { return routeId; }
    public void setRouteId(String routeId) { this.routeId = routeId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getStart() { return start; }
    public void setStart(String start) { this.start = start; }
    public String getEnd() { return end; }
    public void setEnd(String end) { this.end = end; }
    public int getTotalTime() { return totalTime; }
    public void setTotalTime(int totalTime) { this.totalTime = totalTime; }
    public int getTransferCount() { return transferCount; }
    public void setTransferCount(int transferCount) { this.transferCount = transferCount; }
    public double getWalkTime() { return walkTime; }
    public void setWalkTime(double walkTime) { this.walkTime = walkTime; }
    public String getDepartTime() { return departTime; }
    public void setDepartTime(String departTime) { this.departTime = departTime; }
    public List<TransportStepDto> getSteps() { return steps; }
    public void setSteps(List<TransportStepDto> steps) { this.steps = steps; }
    public Double getStartLat() { return startLat; }
    public void setStartLat(Double startLat) { this.startLat = startLat; }
    public Double getStartLng() { return startLng; }
    public void setStartLng(Double startLng) { this.startLng = startLng; }
    public Double getEndLat() { return endLat; }
    public void setEndLat(Double endLat) { this.endLat = endLat; }
    public Double getEndLng() { return endLng; }
    public void setEndLng(Double endLng) { this.endLng = endLng; }
    public Integer getFareWon() { return fareWon; }
    public void setFareWon(Integer fareWon) { this.fareWon = fareWon; }
}