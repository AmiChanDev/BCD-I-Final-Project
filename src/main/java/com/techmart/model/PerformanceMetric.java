package com.techmart.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class PerformanceMetric implements Serializable {

    private static final long serialVersionUID = 1L;

    private String metricName;
    private double value;
    private String unit;
    private LocalDateTime recordedAt;
    private String component;

    public PerformanceMetric() {
        this.recordedAt = LocalDateTime.now();
    }

    public PerformanceMetric(String component, String metricName, double value, String unit) {
        this();
        this.component  = component;
        this.metricName = metricName;
        this.value      = value;
        this.unit       = unit;
    }

    // ---- Getters & Setters ----

    public String getMetricName()               { return metricName; }
    public void setMetricName(String name)      { this.metricName = name; }

    public double getValue()                    { return value; }
    public void setValue(double value)          { this.value = value; }

    public String getUnit()                     { return unit; }
    public void setUnit(String unit)            { this.unit = unit; }

    public LocalDateTime getRecordedAt()        { return recordedAt; }
    public void setRecordedAt(LocalDateTime dt) { this.recordedAt = dt; }

    public String getComponent()                { return component; }
    public void setComponent(String component)  { this.component = component; }

    @Override
    public String toString() {
        return "PerformanceMetric{component='" + component + "', metric='" + metricName +
               "', value=" + value + " " + unit + ", at=" + recordedAt + "}";
    }
}
