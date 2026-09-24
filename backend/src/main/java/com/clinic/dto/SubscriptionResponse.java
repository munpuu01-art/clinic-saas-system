package com.clinic.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SubscriptionResponse(
        Long clinicId,
        String clinicName,
        String clinicStatus,
        String planCode,
        String planName,
        BigDecimal planPriceMonthlyThb,
        Integer maxDoctors,
        Integer maxActivePatients,
        String subscriptionStatus,
        String subscriptionStatusLabel,
        LocalDateTime currentPeriodStart,
        LocalDateTime currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        boolean hasStripeSubscription
) { }
