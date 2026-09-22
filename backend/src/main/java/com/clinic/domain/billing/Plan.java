package com.clinic.domain.billing;

import com.clinic.domain.common.BaseEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * แพ็กเกจที่คลินิกเลือกซื้อ (สินค้าในแคตตาล็อกของเรา ไม่ใช่ TenantEntity เพราะเป็นของกลาง
 * ที่ทุกคลินิกมองเห็นร่วมกันตอนเลือกสมัคร)
 */
@Entity
@Table(name = "plan")
public class Plan extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String code;               // FREE / BASIC / PRO

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "price_monthly_thb", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceMonthlyThb;

    @Column(name = "max_doctors")
    private Integer maxDoctors;        // null = ไม่จำกัด

    @Column(name = "max_active_patients")
    private Integer maxActivePatients; // null = ไม่จำกัด

    @Column(length = 500)
    private String description;

    /** รหัส Price ฝั่ง Stripe (price_xxx) ใช้ตอนสร้าง Checkout Session — ว่างได้สำหรับแพ็กเกจฟรี */
    @Column(name = "stripe_price_id", length = 80)
    private String stripePriceId;

    @Column(nullable = false)
    private boolean active = true;

    protected Plan() { }

    public Plan(String code, String name, BigDecimal priceMonthlyThb, Integer maxDoctors,
               Integer maxActivePatients, String description, String stripePriceId) {
        this.code = code;
        this.name = name;
        this.priceMonthlyThb = priceMonthlyThb;
        this.maxDoctors = maxDoctors;
        this.maxActivePatients = maxActivePatients;
        this.description = description;
        this.stripePriceId = stripePriceId;
    }

    public boolean isFree() { return priceMonthlyThb.signum() == 0; }

    /** ---------- getters ---------- */
    public String getCode() { return code; }
    public String getName() { return name; }
    public BigDecimal getPriceMonthlyThb() { return priceMonthlyThb; }
    public Integer getMaxDoctors() { return maxDoctors; }
    public Integer getMaxActivePatients() { return maxActivePatients; }
    public String getDescription() { return description; }
    public String getStripePriceId() { return stripePriceId; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
