package com.clinic.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangePlanRequest(
        @NotBlank(message = "กรุณาเลือกแพ็กเกจ") String planCode
) { }
