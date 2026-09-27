package com.clinic.service;

import com.clinic.dto.RecordPlatformPaymentRequest;
import com.clinic.dto.RevenueSummaryResponse;

/** รายรับของเจ้าของแพลตฟอร์ม — เงินที่คลินิกจ่ายค่าบริการเข้ามา */
public interface RevenueService {

    RevenueSummaryResponse summary();

    RevenueSummaryResponse.PaymentRow recordManualPayment(Long clinicId, RecordPlatformPaymentRequest request);

    void deleteManualPayment(Long paymentId);

    /**
     * เรียกจาก Webhook เมื่อ Stripe เก็บเงินสำเร็จ (invoice.paid) — ทั้งรอบแรกและรอบต่ออายุ
     * @param amountMinorUnit จำนวนเงินในหน่วยย่อย (สตางค์) ตามที่ Stripe ส่งมา
     */
    void recordStripeInvoicePaid(String invoiceId, String stripeCustomerId, long amountMinorUnit, String currency);
}
