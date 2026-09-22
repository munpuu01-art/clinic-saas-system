package com.clinic.repository;

import com.clinic.domain.doctor.Specialty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpecialtyRepository extends JpaRepository<Specialty, Long> {
    Optional<Specialty> findByIdAndClinicId(Long id, Long clinicId);
    Optional<Specialty> findByCodeAndClinicId(String code, Long clinicId);
    List<Specialty> findByClinicId(Long clinicId);
}
