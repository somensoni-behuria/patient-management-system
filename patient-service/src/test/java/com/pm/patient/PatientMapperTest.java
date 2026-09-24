package com.pm.patient;

import com.pm.patient.dto.PatientRequestDTO;
import com.pm.patient.dto.PatientResponseDTO;
import com.pm.patient.mapper.PatientMapper;
import com.pm.patient.model.Patient;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PatientMapperTest {

    @Test
    void toModel_mapsAllFields() {
        PatientRequestDTO request = new PatientRequestDTO();
        request.setName("Alice");
        request.setEmail("alice@example.com");
        request.setAddress("1 Road");
        request.setDateOfBirth("1980-05-20");
        request.setRegisteredDate("2024-01-01");

        Patient patient = PatientMapper.toModel(request);

        assertThat(patient.getName()).isEqualTo("Alice");
        assertThat(patient.getEmail()).isEqualTo("alice@example.com");
        assertThat(patient.getAddress()).isEqualTo("1 Road");
        assertThat(patient.getDateOfBirth()).isEqualTo(LocalDate.of(1980, 5, 20));
        assertThat(patient.getRegisteredDate()).isEqualTo(LocalDate.of(2024, 1, 1));
    }

    @Test
    void toDTO_mapsAllFields() {
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setName("Bob");
        patient.setEmail("bob@example.com");
        patient.setAddress("2 Street");
        patient.setDateOfBirth(LocalDate.of(1975, 3, 10));
        patient.setRegisteredDate(LocalDate.now());

        PatientResponseDTO dto = PatientMapper.toDTO(patient);

        assertThat(dto.getId()).isEqualTo(patient.getId().toString());
        assertThat(dto.getName()).isEqualTo("Bob");
        assertThat(dto.getEmail()).isEqualTo("bob@example.com");
        assertThat(dto.getAddress()).isEqualTo("2 Street");
        assertThat(dto.getDateOfBirth()).isEqualTo("1975-03-10");
    }
}
