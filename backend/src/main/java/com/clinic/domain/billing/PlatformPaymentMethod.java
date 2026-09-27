package com.clinic.domain.billing;

/** ช่องทางที่คลินิกชำระค่าบริการให้แพลตฟอร์ม */
public enum PlatformPaymentMethod {
    STRIPE("บัตร (Stripe)"),
    BANK_TRANSFER("โอนเงิน"),
    CASH("เงินสด");

    private final String label;

    PlatformPaymentMethod(String label) { this.label = label; }

    public String getLabel() { return label; }
}
