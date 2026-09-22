package com.clinic.repository;

import com.clinic.domain.auth.Role;
import com.clinic.domain.auth.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    // ชื่อผู้ใช้ไม่ซ้ำกันทั้งระบบ (global) โดยตั้งใจ — ผู้ใช้ล็อกอินด้วย username เดียว
    // โดยยังไม่รู้ว่าตัวเองสังกัดคลินิกไหน (เหมือนระบบ SaaS ทั่วไป เช่น Slack)
    Optional<UserAccount> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);

    List<UserAccount> findByClinicIdAndRole(Long clinicId, Role role);
    List<UserAccount> findByClinicId(Long clinicId);

    Optional<UserAccount> findByClinicIdAndPersonId(Long clinicId, Long personId);
    boolean existsByClinicIdAndPersonId(Long clinicId, Long personId);
}
