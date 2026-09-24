package com.pm.patient.config;

import com.pm.patient.model.Patient;
import com.pm.patient.repository.PatientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Seeds a couple of demo patients on first startup only (when the table is empty). Writes
 * directly through the repository so seeding does not depend on billing/Kafka being up.
 */
@Component
public class DataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    private final PatientRepository patientRepository;

    public DataLoader(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    @Override
    public void run(String... args) {
        if (patientRepository.count() > 0) {
            return;
        }

        Patient john = new Patient();
        john.setName("John Doe");
        john.setEmail("john.doe@example.com");
        john.setAddress("123 Main St, Springfield");
        john.setDateOfBirth(LocalDate.of(1985, 6, 15));
        john.setRegisteredDate(LocalDate.now());
        patientRepository.save(john);

        Patient jane = new Patient();
        jane.setName("Jane Smith");
        jane.setEmail("jane.smith@example.com");
        jane.setAddress("456 Oak Ave, Shelbyville");
        jane.setDateOfBirth(LocalDate.of(1990, 11, 2));
        jane.setRegisteredDate(LocalDate.now());
        patientRepository.save(jane);

        log.info("Seeded {} demo patients", patientRepository.count());
    }
}
