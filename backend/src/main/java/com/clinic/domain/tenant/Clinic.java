package com.clinic.domain.tenant;

import com.clinic.domain.common.BaseEntity;
import com.clinic.exception.BusinessRuleException;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Aggregate Root ของผู้เช่าระบบ (Tenant) — คือ "คลินิก" ที่สมัครใช้บริการของเรา
 * Clinic เองไม่ใช่ TenantEntity (มันคือตัวเจ้าของ tenant) แต่ทุก record อื่นในระบบ
 * (ผู้ป่วย แพทย์ นัดหมาย ฯลฯ) จะสืบทอด TenantEntity และผูกกับ clinicId ของ Clinic นี้
 */
@Entity
@Table(name = "clinic")
public class Clinic extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    /** ใช้เป็นตัวระบุที่อ่านง่าย (เช่น ใช้ในอนาคตเป็น subdomain) ไม่ซ้ำกันทั้งระบบ */
    @Column(nullable = false, unique = true, length = 60)
    private String slug;

    @Column(name = "contact_email", nullable = false, length = 120)
    private String contactEmail;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClinicStatus status = ClinicStatus.TRIALING;

    @Column(name = "trial_ends_at")
    private LocalDateTime trialEndsAt;

    protected Clinic() { }

    public Clinic(String name, String slug, String contactEmail, String contactPhone) {
        if (name == null || name.isBlank()) {
            throw new BusinessRuleException("CLINIC_NAME_REQUIRED", "กรุณาระบุชื่อคลินิก");
        }
        this.name = name.trim();
        this.slug = slug.trim().toLowerCase();
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.trialEndsAt = LocalDateTime.now().plusDays(14);   // ทดลองใช้ฟรี 14 วัน
    }

    /** ---------- พฤติกรรมทางธุรกิจ ---------- */

    public void activate() { this.status = ClinicStatus.ACTIVE; }
    public void markPastDue() { this.status = ClinicStatus.PAST_DUE; }
    public void suspend() { this.status = ClinicStatus.SUSPENDED; }
    public void cancel() { this.status = ClinicStatus.CANCELED; }

    public boolean canAcceptTraffic() { return status.isUsable(); }

    public boolean trialExpired() {
        return status == ClinicStatus.TRIALING
                && trialEndsAt != null && trialEndsAt.isBefore(LocalDateTime.now());
    }

    /** ---------- getters ---------- */
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String v) { this.contactEmail = v; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String v) { this.contactPhone = v; }
    public ClinicStatus getStatus() { return status; }
    public LocalDateTime getTrialEndsAt() { return trialEndsAt; }
}
