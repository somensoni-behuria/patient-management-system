package com.pm.patient.service;

import com.pm.patient.dto.PatientRequestDTO;
import com.pm.patient.dto.PatientResponseDTO;
import com.pm.patient.exception.EmailAlreadyExistsException;
import com.pm.patient.exception.PatientNotFoundException;
import com.pm.patient.grpc.BillingServiceGrpcClient;
import com.pm.patient.kafka.KafkaProducer;
import com.pm.patient.mapper.PatientMapper;
import com.pm.patient.model.Patient;
import com.pm.patient.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PatientService {

    private final PatientRepository patientRepository;
    private final BillingServiceGrpcClient billingServiceGrpcClient;
    private final KafkaProducer kafkaProducer;

    public PatientService(PatientRepository patientRepository,
                          BillingServiceGrpcClient billingServiceGrpcClient,
                          KafkaProducer kafkaProducer) {
        this.patientRepository = patientRepository;
        this.billingServiceGrpcClient = billingServiceGrpcClient;
        this.kafkaProducer = kafkaProducer;
    }

    @Transactional(readOnly = true)
    public List<PatientResponseDTO> getPatients() {
        return patientRepository.findAll().stream()
                .map(PatientMapper::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public PatientResponseDTO getPatient(UUID id) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new PatientNotFoundException(
                        "Patient not found with id: " + id));
        return PatientMapper.toDTO(patient);
    }

    @Transactional
    public PatientResponseDTO createPatient(PatientRequestDTO request) {
        if (patientRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException(
                    "A patient already exists with email: " + request.getEmail());
        }

        Patient patient = patientRepository.save(PatientMapper.toModel(request));

        // Synchronous side effect: create a billing account over gRPC.
        billingServiceGrpcClient.createBillingAccount(
                patient.getId().toString(), patient.getName(), patient.getEmail());

        // Asynchronous side effect: publish a domain event to Kafka.
        kafkaProducer.sendPatientCreatedEvent(patient);

        return PatientMapper.toDTO(patient);
    }

    @Transactional
    public PatientResponseDTO updatePatient(UUID id, PatientRequestDTO request) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new PatientNotFoundException(
                        "Patient not found with id: " + id));

        if (patientRepository.existsByEmailAndIdNot(request.getEmail(), id)) {
            throw new EmailAlreadyExistsException(
                    "A patient already exists with email: " + request.getEmail());
        }

        patient.setName(request.getName());
        patient.setEmail(request.getEmail());
        patient.setAddress(request.getAddress());
        patient.setDateOfBirth(java.time.LocalDate.parse(request.getDateOfBirth()));

        return PatientMapper.toDTO(patientRepository.save(patient));
    }

    @Transactional
    public void deletePatient(UUID id) {
        if (!patientRepository.existsById(id)) {
            throw new PatientNotFoundException("Patient not found with id: " + id);
        }
        patientRepository.deleteById(id);
    }
}
