package com.clinic.repository;

import com.clinic.domain.tenant.Clinic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClinicRepository extends JpaRepository<Clinic, Long> {
    boolean existsBySlugIgnoreCase(String slug);
    Optional<Clinic> findBySlugIgnoreCase(String slug);
}
