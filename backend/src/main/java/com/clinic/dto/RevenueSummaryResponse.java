package com.clinic.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** ภาพรวมรายรับของแพลตฟอร์ม สำหรับหน้า Super Admin */
public record RevenueSummaryResponse(
        /** เงินที่ได้รับจริงทั้งหมดตั้งแต่เปิดระบบ */
        BigDecimal totalReceivedThb,
        /** เงินที่ได้รับจริงในเดือนปฏิทินปัจจุบัน */
        BigDecimal receivedThisMonthThb,
        /** รายได้ประจำต่อเดือน (MRR) = ผลรวมราคาแพ็กเกจของคลินิกที่สถานะ ACTIVE และไม่ใช่แพ็กเกจฟรี */
        BigDecimal monthlyRecurringThb,
        long payingClinicCount,
        long paymentCount,
        List<ClinicRevenue> byClinic,
        List<PaymentRow> recentPayments
) {
    public record ClinicRevenue(Long clinicId, BigDecimal totalPaidThb, int paymentCount, LocalDateTime lastPaidAt) { }

    public record PaymentRow(Long id, Long clinicId, String clinicName, String planCode, BigDecimal amountThb,
                             String method, String methodLabel, String reference, String note,
                             LocalDateTime paidAt, boolean manual) { }
}
