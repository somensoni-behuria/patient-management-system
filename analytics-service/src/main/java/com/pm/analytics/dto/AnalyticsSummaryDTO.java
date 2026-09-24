package com.pm.analytics.dto;

public class AnalyticsSummaryDTO {

    private long totalEvents;
    private long patientsCreated;

    public AnalyticsSummaryDTO(long totalEvents, long patientsCreated) {
        this.totalEvents = totalEvents;
        this.patientsCreated = patientsCreated;
    }

    public long getTotalEvents() {
        return totalEvents;
    }

    public void setTotalEvents(long totalEvents) {
        this.totalEvents = totalEvents;
    }

    public long getPatientsCreated() {
        return patientsCreated;
    }

    public void setPatientsCreated(long patientsCreated) {
        this.patientsCreated = patientsCreated;
    }
}
