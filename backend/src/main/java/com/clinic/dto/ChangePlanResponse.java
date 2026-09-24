package com.clinic.dto;

/**
 * ผลลัพธ์การเปลี่ยนแพ็กเกจ — ถ้าเป็นการอัปเกรดจากแพ็กเกจฟรีไปแพ็กเกจเสียเงินครั้งแรก
 * ต้องพาไปชำระเงินที่ Stripe ก่อน (requiresPayment=true); กรณีอื่นเปลี่ยนได้ทันที
 */
public record ChangePlanResponse(
        boolean requiresPayment,
        String checkoutUrl,
        SubscriptionResponse subscription
) { }
