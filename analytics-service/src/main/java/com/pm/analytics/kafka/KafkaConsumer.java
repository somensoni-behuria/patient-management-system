package com.pm.analytics.kafka;

import com.google.protobuf.InvalidProtocolBufferException;
import com.pm.analytics.service.AnalyticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import patient.events.PatientEvent;

/**
 * Consumes patient events and records them into the analytics store. Uses its own consumer group
 * so it receives every event independently of notification-service.
 */
@Service
public class KafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumer.class);

    private final AnalyticsService analyticsService;

    public KafkaConsumer(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @KafkaListener(topics = "patient", groupId = "analytics-service")
    public void consumeEvent(byte[] event) {
        try {
            PatientEvent patientEvent = PatientEvent.parseFrom(event);
            log.info("Recording analytics for patientId={} eventType={}",
                    patientEvent.getPatientId(), patientEvent.getEventType());
            analyticsService.record(patientEvent);
        } catch (InvalidProtocolBufferException e) {
            log.error("Failed to deserialize PatientEvent: {}", e.getMessage());
        }
    }
}
