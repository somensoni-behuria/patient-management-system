package com.pm.analytics.service;

import com.pm.analytics.dto.AnalyticsSummaryDTO;
import com.pm.analytics.model.AnalyticsEvent;
import com.pm.analytics.repository.AnalyticsEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import patient.events.PatientEvent;

import java.time.Instant;

@Service
public class AnalyticsService {

    private final AnalyticsEventRepository repository;

    public AnalyticsService(AnalyticsEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(PatientEvent event) {
        AnalyticsEvent entity = new AnalyticsEvent();
        entity.setPatientId(event.getPatientId());
        entity.setName(event.getName());
        entity.setEmail(event.getEmail());
        entity.setEventType(event.getEventType());
        entity.setReceivedAt(Instant.now());
        repository.save(entity);
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryDTO summary() {
        long total = repository.count();
        long created = repository.countByEventType("PATIENT_CREATED");
        return new AnalyticsSummaryDTO(total, created);
    }

    @Transactional(readOnly = true)
    public long patientsCreatedCount() {
        return repository.countByEventType("PATIENT_CREATED");
    }
}
