package com.pm.patient.mapper;

import com.pm.patient.dto.PatientRequestDTO;
import com.pm.patient.dto.PatientResponseDTO;
import com.pm.patient.model.Patient;

import java.time.LocalDate;

/**
 * Maps between the Patient entity and its request/response DTOs.
 */
public class PatientMapper {

    private PatientMapper() {
    }

    public static PatientResponseDTO toDTO(Patient patient) {
        PatientResponseDTO dto = new PatientResponseDTO();
        dto.setId(patient.getId().toString());
        dto.setName(patient.getName());
        dto.setEmail(patient.getEmail());
        dto.setAddress(patient.getAddress());
        dto.setDateOfBirth(patient.getDateOfBirth().toString());
        return dto;
    }

    public static Patient toModel(PatientRequestDTO request) {
        Patient patient = new Patient();
        patient.setName(request.getName());
        patient.setEmail(request.getEmail());
        patient.setAddress(request.getAddress());
        patient.setDateOfBirth(LocalDate.parse(request.getDateOfBirth()));
        if (request.getRegisteredDate() != null && !request.getRegisteredDate().isBlank()) {
            patient.setRegisteredDate(LocalDate.parse(request.getRegisteredDate()));
        } else {
            patient.setRegisteredDate(LocalDate.now());
        }
        return patient;
    }
}
