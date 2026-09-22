package com.clinic.service;

import com.clinic.domain.tenant.TenantContext;
import com.clinic.domain.tenant.TenantEntity;
import com.clinic.exception.ResourceNotFoundException;
import org.springframework.stereotype.Component;

/**
 * ด่านตรวจความเป็นเจ้าของข้อมูลข้าม tenant — เรียกใช้ "ทุกครั้ง" หลัง findById ของ
 * Entity ที่เป็น TenantEntity ก่อนคืนค่าออกไปให้ชั้นบน เพื่อป้องกันการเดา id ข้ามคลินิก
 *
 * ตั้งใจคืน 404 (ไม่พบ) แทน 403 (ไม่มีสิทธิ์) เพื่อไม่ให้รู้ว่า id นั้นมีอยู่จริงในคลินิกอื่น
 * (Security by obscurity ระดับพื้นฐาน — ไม่บอกใบ้การมีอยู่ของข้อมูลข้ามผู้เช่า)
 */
@Component
public class TenantGuard {

    public <T extends TenantEntity> T assertOwned(T entity, String notFoundMessage) {
        if (entity == null) {
            throw new ResourceNotFoundException(notFoundMessage);
        }
        Long currentClinic = TenantContext.currentOrNull();
        // ไม่มี tenant ปัจจุบัน (เช่น SUPER_ADMIN) = มองเห็นได้ทุกคลินิกโดยตั้งใจ
        if (currentClinic == null) return entity;
        if (!currentClinic.equals(entity.getClinicId())) {
            throw new ResourceNotFoundException(notFoundMessage);
        }
        return entity;
    }

    /** clinicId ของ tenant ปัจจุบัน (โยน error ถ้าไม่มี — ใช้ตอนสร้าง record ใหม่) */
    public Long requireCurrentClinicId() {
        Long id = TenantContext.currentOrNull();
        if (id == null) {
            throw new com.clinic.exception.BusinessRuleException("NO_TENANT_CONTEXT",
                    "ไม่พบคลินิกปัจจุบันของผู้ใช้งาน");
        }
        return id;
    }
}
