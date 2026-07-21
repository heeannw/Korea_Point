package com.example.demo.transport.dto;

import java.util.List;
import java.util.Map;

public class TransportStepDto {

    private String type;
    private String line;
    private Integer subwayCode;
    private String color;
    private String fromStation;
    private String toStation;
    private Integer fromStationId;
    private Integer toStationId;
    private int duration;
    private List<Map<String, Double>> path;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getLine() { return line; }
    public void setLine(String line) { this.line = line; }
    public Integer getSubwayCode() { return subwayCode; }
    public void setSubwayCode(Integer subwayCode) { this.subwayCode = subwayCode; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getFromStation() { return fromStation; }
    public void setFromStation(String fromStation) { this.fromStation = fromStation; }
    public String getToStation() { return toStation; }
    public void setToStation(String toStation) { this.toStation = toStation; }
    public Integer getFromStationId() { return fromStationId; }
    public void setFromStationId(Integer fromStationId) { this.fromStationId = fromStationId; }
    public Integer getToStationId() { return toStationId; }
    public void setToStationId(Integer toStationId) { this.toStationId = toStationId; }
    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }
    public List<Map<String, Double>> getPath() { return path; }
    public void setPath(List<Map<String, Double>> path) { this.path = path; }
}
