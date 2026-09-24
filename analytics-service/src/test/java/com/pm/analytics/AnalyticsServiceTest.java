package com.pm.analytics;

import com.pm.analytics.dto.AnalyticsSummaryDTO;
import com.pm.analytics.model.AnalyticsEvent;
import com.pm.analytics.repository.AnalyticsEventRepository;
import com.pm.analytics.service.AnalyticsService;
import org.junit.jupiter.api.Test;
import patient.events.PatientEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsServiceTest {

    private final AnalyticsEventRepository repository = mock(AnalyticsEventRepository.class);
    private final AnalyticsService service = new AnalyticsService(repository);

    @Test
    void record_persistsMappedEvent() {
        PatientEvent event = PatientEvent.newBuilder()
                .setPatientId("p1")
                .setName("Carol")
                .setEmail("carol@example.com")
                .setEventType("PATIENT_CREATED")
                .build();

        service.record(event);

        verify(repository).save(any(AnalyticsEvent.class));
    }

    @Test
    void summary_returnsCounts() {
        when(repository.count()).thenReturn(5L);
        when(repository.countByEventType("PATIENT_CREATED")).thenReturn(3L);

        AnalyticsSummaryDTO summary = service.summary();

        assertThat(summary.getTotalEvents()).isEqualTo(5L);
        assertThat(summary.getPatientsCreated()).isEqualTo(3L);
    }
}
