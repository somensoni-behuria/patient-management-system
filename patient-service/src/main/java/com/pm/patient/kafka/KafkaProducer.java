package com.pm.patient.kafka;

import com.pm.patient.model.Patient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import patient.events.PatientEvent;

/**
 * Publishes a Protobuf {@link PatientEvent} to the {@code patient} topic when a patient is
 * created. Consumed asynchronously by notification-service and analytics-service.
 */
@Service
public class KafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducer.class);
    private static final String TOPIC = "patient";

    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    public KafkaProducer(KafkaTemplate<String, byte[]> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendPatientCreatedEvent(Patient patient) {
        PatientEvent event = PatientEvent.newBuilder()
                .setPatientId(patient.getId().toString())
                .setName(patient.getName())
                .setEmail(patient.getEmail())
                .setEventType("PATIENT_CREATED")
                .build();

        try {
            kafkaTemplate.send(TOPIC, event.toByteArray());
            log.info("Published PATIENT_CREATED event for patientId={}", patient.getId());
        } catch (Exception e) {
            log.error("Failed to publish PATIENT_CREATED event for patientId={}: {}",
                    patient.getId(), e.getMessage());
        }
    }
}
