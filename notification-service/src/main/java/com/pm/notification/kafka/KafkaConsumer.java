package com.pm.notification.kafka;

import com.google.protobuf.InvalidProtocolBufferException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import patient.events.PatientEvent;

/**
 * Consumes patient events from Kafka and "sends" a welcome notification (here, logged).
 *
 * <p>Uses a dedicated consumer group so it scales independently of analytics-service and so
 * rolling restarts rebalance partitions without losing events.
 */
@Service
public class KafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumer.class);

    @KafkaListener(topics = "patient", groupId = "notification-service")
    public void consumeEvent(byte[] event) {
        try {
            PatientEvent patientEvent = PatientEvent.parseFrom(event);
            log.info("Received patient event [type={}] - sending welcome notification to "
                            + "patientId={} name={} email={}",
                    patientEvent.getEventType(),
                    patientEvent.getPatientId(),
                    patientEvent.getName(),
                    patientEvent.getEmail());

            // Real implementation would call an email/SMS provider here.
            sendWelcomeNotification(patientEvent);
        } catch (InvalidProtocolBufferException e) {
            log.error("Failed to deserialize PatientEvent: {}", e.getMessage());
        }
    }

    private void sendWelcomeNotification(PatientEvent event) {
        log.info("Notification sent: Welcome to our clinic, {}!", event.getName());
    }
}
