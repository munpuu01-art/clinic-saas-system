package com.clinic.repository;

import com.clinic.domain.doctor.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    Optional<Doctor> findByIdAndClinicId(Long id, Long clinicId);
    Optional<Doctor> findByLicenseNoAndClinicId(String licenseNo, Long clinicId);
    List<Doctor> findBySpecialtyIdAndActiveTrueAndClinicId(Long specialtyId, Long clinicId);
    List<Doctor> findByActiveTrueAndClinicId(Long clinicId);

    @Query("""
           SELECT DISTINCT d FROM Doctor d
           LEFT JOIN FETCH d.schedules s
           WHERE d.clinicId = :clinicId AND d.active = true
             AND (:specialtyId IS NULL OR d.specialty.id = :specialtyId)
           """)
    List<Doctor> findAvailableDoctors(@Param("clinicId") Long clinicId, @Param("specialtyId") Long specialtyId);

    long countByClinicId(Long clinicId);
}
