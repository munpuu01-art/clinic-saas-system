package com.clinic.service;

import com.clinic.domain.common.ContactInfo;
import com.clinic.domain.common.Gender;
import com.clinic.domain.person.Patient;
import com.clinic.domain.tenant.TenantContext;
import com.clinic.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** TC-31..TC-33 : ทดสอบด่านตรวจ Multi-tenancy (TenantGuard) */
class TenantGuardTest {

    private final TenantGuard guard = new TenantGuard();

    private Patient patientOfClinic(Long clinicId) {
        Patient p = new Patient("HN-0001", "คนไข้", "ทดสอบ", Gender.FEMALE,
                LocalDate.of(1990, 1, 1), null, new ContactInfo("0812345678", null, null));
        p.setClinicId(clinicId);
        return p;
    }

    @AfterEach
    void cleanup() { TenantContext.clear(); }

    @Test
    @DisplayName("TC-31 เข้าถึงข้อมูลของคลินิกตัวเองได้ตามปกติ")
    void allowsAccessWithinSameClinic() {
        TenantContext.set(1L);
        Patient patient = patientOfClinic(1L);
        assertSame(patient, guard.assertOwned(patient, "ไม่พบผู้ป่วย"));
    }

    @Test
    @DisplayName("TC-32 ห้ามเข้าถึงข้อมูลของคลินิกอื่น แม้จะรู้ id ก็ตาม")
    void blocksAccessAcrossClinics() {
        TenantContext.set(1L);
        Patient patientOfOtherClinic = patientOfClinic(2L);
        assertThrows(ResourceNotFoundException.class,
                () -> guard.assertOwned(patientOfOtherClinic, "ไม่พบผู้ป่วย"));
    }

    @Test
    @DisplayName("TC-33 ไม่มี tenant ปัจจุบัน (เช่น SUPER_ADMIN) มองเห็นได้ทุกคลินิก")
    void superAdminBypassesTenantCheck() {
        // ไม่เรียก TenantContext.set(...) เลย = จำลองบัญชี SUPER_ADMIN ที่ไม่สังกัดคลินิกใด
        Patient patient = patientOfClinic(99L);
        assertSame(patient, guard.assertOwned(patient, "ไม่พบผู้ป่วย"));
    }
}
