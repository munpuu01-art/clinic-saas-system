package com.clinic.domain.billing;

import com.clinic.domain.common.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * เงินที่คลินิก (ลูกค้า) จ่ายค่าบริการให้แพลตฟอร์ม — 1 แถวต่อ 1 ครั้งที่ได้รับเงินจริง
 *
 * ต่างจาก {@link Payment} ซึ่งเป็นเงินที่ "ผู้ป่วยจ่ายให้คลินิก" (อยู่ในโลกของแต่ละ tenant)
 * ส่วนคลาสนี้เป็นรายรับของเจ้าของแพลตฟอร์ม จึงผูกด้วย clinicId ตรง ๆ แบบเดียวกับ Subscription
 * เพื่อให้ Super Admin มองเห็นของทุกคลินิกพร้อมกันได้
 *
 * แหล่งที่มา: Webhook ของ Stripe (invoice.paid) หรือ Super Admin บันทึกเองเมื่อรับโอน/เงินสด
 */
@Entity
@Table(name = "platform_payment", indexes = @Index(name = "idx_platform_payment_clinic", columnList = "clinic_id"))
public class PlatformPayment extends BaseEntity {

    @Column(name = "clinic_id", nullable = false)
    private Long clinicId;

    /** แพ็กเกจ ณ วันที่จ่าย — เก็บเป็นข้อความเพื่อไม่ให้ประวัติเปลี่ยนตามเมื่อคลินิกเปลี่ยนแพ็กเกจภายหลัง */
    @Column(name = "plan_code", length = 20)
    private String planCode;

    @Column(name = "amount_thb", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountThb;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlatformPaymentMethod method;

    /** เลข invoice ของ Stripe (in_...) — unique กันบันทึกซ้ำเมื่อ Stripe ส่ง webhook มาซ้ำ */
    @Column(unique = true, length = 80)
    private String reference;

    @Column(length = 255)
    private String note;

    protected PlatformPayment() { }

    public PlatformPayment(Long clinicId, String planCode, BigDecimal amountThb, LocalDateTime paidAt,
                           PlatformPaymentMethod method, String reference, String note) {
        if (amountThb == null || amountThb.signum() <= 0) {
            throw new IllegalArgumentException("จำนวนเงินต้องมากกว่า 0");
        }
        this.clinicId = clinicId;
        this.planCode = planCode;
        this.amountThb = amountThb;
        this.paidAt = paidAt != null ? paidAt : LocalDateTime.now();
        this.method = method;
        this.reference = reference;
        this.note = note;
    }

    /** รายการที่มาจาก Stripe ลบไม่ได้ (ต้องคืนเงินผ่าน Stripe) — ลบได้เฉพาะที่บันทึกมือ */
    public boolean isManual() { return method != PlatformPaymentMethod.STRIPE; }

    public Long getClinicId() { return clinicId; }
    public String getPlanCode() { return planCode; }
    public BigDecimal getAmountThb() { return amountThb; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public PlatformPaymentMethod getMethod() { return method; }
    public String getReference() { return reference; }
    public String getNote() { return note; }
}
