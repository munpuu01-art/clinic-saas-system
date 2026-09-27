package com.clinic.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Super Admin บันทึกรับเงินจากคลินิกเอง (เช่น ลูกค้าโอนเงินหรือจ่ายเงินสด นอก Stripe) */
public record RecordPlatformPaymentRequest(
        @NotNull @DecimalMin(value = "0.01", message = "จำนวนเงินต้องมากกว่า 0") BigDecimal amountThb,
        @NotNull @Pattern(regexp = "BANK_TRANSFER|CASH", message = "ช่องทางต้องเป็น BANK_TRANSFER หรือ CASH") String method,
        LocalDate paidDate,
        @Size(max = 255) String note
) { }
