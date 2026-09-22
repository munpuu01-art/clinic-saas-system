package com.clinic.repository;

import com.clinic.domain.medical.MedicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Long> {
    Optional<MedicalRecord> findByIdAndClinicId(Long id, Long clinicId);
    Optional<MedicalRecord> findByClinicIdAndAppointmentId(Long clinicId, Long appointmentId);
    List<MedicalRecord> findByClinicIdAndPatientIdOrderByCreatedAtDesc(Long clinicId, Long patientId);
}
