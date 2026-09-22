package com.clinic.domain.tenant;

import com.clinic.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;

/**
 * Superclass ของทุก Entity ที่ "เป็นของคลินิกใดคลินิกหนึ่ง" (Multi-tenancy)
 * ทุก Entity ที่สืบทอดจากคลาสนี้จะมีคอลัมน์ clinic_id กำกับอยู่เสมอ
 *
 * หลักการ: การตัดสินใจว่า record นี้เป็นของคลินิกไหนต้อง "ชัดเจนที่สุด" — service layer
 * เป็นผู้ตั้งค่า clinicId ตรง ๆ ตอนสร้าง record เสมอ (ไม่พึ่งพา thread-local ล้วน ๆ)
 * ส่วน @PrePersist ด้านล่างเป็นเพียงตาข่ายกันพลาดชั้นสุดท้าย เผื่อ service ลืมตั้งค่า
 */
@MappedSuperclass
public abstract class TenantEntity extends BaseEntity {

    // nullable ที่ระดับฐานข้อมูล เพราะมีข้อยกเว้นเดียวคือบัญชี SUPER_ADMIN ที่ไม่สังกัดคลินิกใด
    // (ตรวจบังคับไม่ให้ null ที่ชั้น Service สำหรับ Entity อื่นทุกตัวแทน)
    @Column(name = "clinic_id", updatable = false)
    private Long clinicId;

    public Long getClinicId() { return clinicId; }
    public void setClinicId(Long clinicId) { this.clinicId = clinicId; }

    @PrePersist
    protected void ensureTenantAssigned() {
        if (this.clinicId == null) {
            this.clinicId = TenantContext.currentOrNull();
        }
    }
}
