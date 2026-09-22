package com.clinic.repository;

import com.clinic.domain.person.Staff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StaffRepository extends JpaRepository<Staff, Long> {
    Optional<Staff> findByIdAndClinicId(Long id, Long clinicId);
    Optional<Staff> findByStaffCodeAndClinicId(String staffCode, Long clinicId);
}
