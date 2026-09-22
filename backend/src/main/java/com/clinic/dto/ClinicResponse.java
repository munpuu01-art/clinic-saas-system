package com.clinic.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ClinicResponse(
        Long id,
        String name,
        String slug,
        String contactEmail,
        String contactPhone,
        String status,
        String statusLabel,
        LocalDateTime trialEndsAt,
        LocalDateTime createdAt,
        String planCode,
        String planName,
        BigDecimal planPriceMonthlyThb,
        String subscriptionStatus,
        LocalDateTime currentPeriodEnd,
        long doctorCount,
        long patientCount
) { }
