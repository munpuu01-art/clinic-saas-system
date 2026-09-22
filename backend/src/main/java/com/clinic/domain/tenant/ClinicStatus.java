package com.clinic.domain.tenant;

/** สถานะบัญชีคลินิกในระบบ SaaS */
public enum ClinicStatus {
    TRIALING("ทดลองใช้งาน"),
    ACTIVE("ใช้งานปกติ"),
    PAST_DUE("ค้างชำระเงิน"),
    SUSPENDED("ถูกระงับการใช้งาน"),
    CANCELED("ยกเลิกแล้ว");

    private final String label;
    ClinicStatus(String label) { this.label = label; }
    public String getLabel() { return label; }

    /** คลินิกยังเข้าใช้ระบบได้ตามปกติหรือไม่ */
    public boolean isUsable() { return this == TRIALING || this == ACTIVE || this == PAST_DUE; }
}
