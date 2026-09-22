package com.clinic.dto;

import java.math.BigDecimal;

public record PlanResponse(
        Long id,
        String code,
        String name,
        BigDecimal priceMonthlyThb,
        Integer maxDoctors,
        Integer maxActivePatients,
        String description
) { }
