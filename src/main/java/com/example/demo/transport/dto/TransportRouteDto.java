package com.example.demo.transport.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class TransportRouteDto {

    @JsonProperty("route_id")
    private String routeId;

    private String type;
    private String start;
    private String end;

    @JsonProperty("total_time")
    private int totalTime;

    @JsonProperty("transfer_count")
    private int transferCount;

    @JsonProperty("walk_time")
    private double walkTime;

    @JsonProperty("depart_time")
    private String departTime;

    private List<TransportStepDto> steps;

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
}