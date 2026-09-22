package com.clinic.repository;

import com.clinic.domain.person.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** Repository Pattern ผ่าน Spring Data JPA — ทุก query กรองด้วย clinicId เสมอ (Multi-tenancy) */
public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findByIdAndClinicId(Long id, Long clinicId);
    Optional<Patient> findByHnAndClinicId(String hn, Long clinicId);
    Optional<Patient> findByNationalIdAndClinicId(String nationalId, Long clinicId);
    boolean existsByHnAndClinicId(String hn, Long clinicId);

    @Query("""
           SELECT p FROM Patient p
           WHERE p.clinicId = :clinicId
             AND (LOWER(p.firstName) LIKE LOWER(CONCAT('%', :q, '%'))
              OR LOWER(p.lastName)  LIKE LOWER(CONCAT('%', :q, '%'))
              OR p.hn LIKE CONCAT('%', :q, '%')
              OR p.contact.phone LIKE CONCAT('%', :q, '%'))
           """)
    Page<Patient> search(@Param("clinicId") Long clinicId, @Param("q") String keyword, Pageable pageable);

    @Query("SELECT COUNT(p) FROM Patient p WHERE p.clinicId = :clinicId AND p.hn LIKE CONCAT('HN-', :year, '-%')")
    long countByHnYear(@Param("clinicId") Long clinicId, @Param("year") String year);

    long countByClinicId(Long clinicId);
}
