package com.clinic.domain.billing;

/** สถานะการสมัครสมาชิกของคลินิกหนึ่ง ๆ (mirror จากสถานะฝั่ง Stripe) */
public enum SubscriptionStatus {
    TRIALING("ทดลองใช้งาน"),
    ACTIVE("ใช้งานปกติ"),
    PAST_DUE("ค้างชำระเงิน"),
    CANCELED("ยกเลิกแล้ว"),
    INCOMPLETE("รอดำเนินการชำระเงิน");

    private final String label;
    SubscriptionStatus(String label) { this.label = label; }
    public String getLabel() { return label; }
}
