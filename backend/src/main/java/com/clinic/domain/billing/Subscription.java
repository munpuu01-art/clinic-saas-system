package com.clinic.domain.billing;

import com.clinic.domain.common.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * การสมัครสมาชิกของคลินิกหนึ่งแห่งกับแพ็กเกจหนึ่งแพ็กเกจ — Aggregate Root ของฝั่งบิลลิ่ง
 * ผูกกับ Clinic ด้วย clinicId ตรง ๆ (ไม่ใช้ TenantEntity เพราะ Super Admin ต้องมองเห็น
 * Subscription ของทุกคลินิกพร้อมกันได้ ซึ่งขัดกับหลักการกรองอัตโนมัติของ tenant ปกติ)
 */
@Entity
@Table(name = "subscription")
public class Subscription extends BaseEntity {

    @Column(name = "clinic_id", nullable = false, unique = true)
    private Long clinicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status;

    @Column(name = "current_period_start")
    private LocalDateTime currentPeriodStart;

    @Column(name = "current_period_end")
    private LocalDateTime currentPeriodEnd;

    @Column(name = "stripe_customer_id", length = 80)
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id", length = 80)
    private String stripeSubscriptionId;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd = false;

    protected Subscription() { }

    public Subscription(Long clinicId, Plan plan, SubscriptionStatus status) {
        this.clinicId = clinicId;
        this.plan = plan;
        this.status = status;
    }

    /** ---------- พฤติกรรมทางธุรกิจ ---------- */

    public void changePlan(Plan newPlan) { this.plan = newPlan; }

    public void applyStripeCheckout(String stripeCustomerId, String stripeSubscriptionId,
                                    LocalDateTime periodStart, LocalDateTime periodEnd) {
        this.stripeCustomerId = stripeCustomerId;
        this.stripeSubscriptionId = stripeSubscriptionId;
        this.currentPeriodStart = periodStart;
        this.currentPeriodEnd = periodEnd;
        this.status = SubscriptionStatus.ACTIVE;
    }

    public void markPastDue() { this.status = SubscriptionStatus.PAST_DUE; }
    public void markCanceled() { this.status = SubscriptionStatus.CANCELED; }
    public void renewPeriod(LocalDateTime start, LocalDateTime end) {
        this.currentPeriodStart = start;
        this.currentPeriodEnd = end;
        this.status = SubscriptionStatus.ACTIVE;
    }
    public void requestCancelAtPeriodEnd() { this.cancelAtPeriodEnd = true; }
    public void cancelTheCancellation() { this.cancelAtPeriodEnd = false; }
    public void cancelImmediately() {
        this.status = SubscriptionStatus.CANCELED;
        this.stripeSubscriptionId = null;
        this.cancelAtPeriodEnd = false;
    }

    public boolean isUsable() {
        return status == SubscriptionStatus.TRIALING || status == SubscriptionStatus.ACTIVE
                || status == SubscriptionStatus.PAST_DUE;
    }

    /** ---------- getters ---------- */
    public Long getClinicId() { return clinicId; }
    public Plan getPlan() { return plan; }
    public SubscriptionStatus getStatus() { return status; }
    public LocalDateTime getCurrentPeriodStart() { return currentPeriodStart; }
    public LocalDateTime getCurrentPeriodEnd() { return currentPeriodEnd; }
    public String getStripeCustomerId() { return stripeCustomerId; }
    public String getStripeSubscriptionId() { return stripeSubscriptionId; }
    public boolean isCancelAtPeriodEnd() { return cancelAtPeriodEnd; }
}
