package com.clinic.dto;

/**
 * ผลลัพธ์การสมัครคลินิกใหม่ — สองแบบ:
 * 1) แพ็กเกจฟรี: requiresPayment=false และ login มาพร้อมทันที (เข้าใช้งานได้เลย)
 * 2) แพ็กเกจเสียเงิน: requiresPayment=true และ checkoutUrl ให้ redirect ไปชำระเงินที่ Stripe ก่อน
 */
public record ClinicRegisterResponse(
        boolean requiresPayment,
        String checkoutUrl,
        LoginResponse login
) { }
