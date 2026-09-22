package com.clinic.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** SUPER_ADMIN เพิ่มคลินิกให้เองโดยตรง (ไม่ผ่านการชำระเงิน เช่น ลูกค้าที่คุยกันนอกระบบ) */
public record CreateClinicRequest(
        @NotBlank String clinicName,
        @NotBlank @Pattern(regexp = "^[a-z0-9-]{3,60}$") String slug,
        @NotBlank @Email String contactEmail,
        String contactPhone,
        @NotBlank @Size(min = 4) String adminUsername,
        @NotBlank @Size(min = 8) String adminPassword,
        @NotBlank String planCode
) { }
