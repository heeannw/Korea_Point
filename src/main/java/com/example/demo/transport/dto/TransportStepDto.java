package com.example.demo.transport.dto;

import java.util.List;
import java.util.Map;

public class TransportStepDto {

    private String type;
    private String line;
    private String color;
    private String fromStation;
    private String toStation;
    private int duration;
    private List<Map<String, Double>> path;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getLine() { return line; }
    public void setLine(String line) { this.line = line; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getFromStation() { return fromStation; }
    public void setFromStation(String fromStation) { this.fromStation = fromStation; }
    public String getToStation() { return toStation; }
    public void setToStation(String toStation) { this.toStation = toStation; }
    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }
    public List<Map<String, Double>> getPath() { return path; }
    public void setPath(List<Map<String, Double>> path) { this.path = path; }
}