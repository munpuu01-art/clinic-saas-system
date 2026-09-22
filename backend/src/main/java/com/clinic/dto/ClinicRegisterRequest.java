package com.clinic.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** ฟอร์มสมัครคลินิกใหม่ด้วยตนเอง (self-service) — สร้างคลินิก + บัญชีผู้ดูแลคลินิกในคราวเดียว */
public record ClinicRegisterRequest(
        @NotBlank(message = "กรุณากรอกชื่อคลินิก") String clinicName,
        @NotBlank(message = "กรุณากรอกรหัสคลินิก (ใช้ในลิงก์)")
        @Pattern(regexp = "^[a-z0-9-]{3,60}$", message = "ใช้ได้เฉพาะตัวอักษรอังกฤษพิมพ์เล็ก ตัวเลข และขีดกลาง") String slug,
        @NotBlank @Email(message = "อีเมลไม่ถูกต้อง") String contactEmail,
        String contactPhone,
        @NotBlank(message = "กรุณากรอกชื่อผู้ใช้ผู้ดูแลคลินิก")
        @Size(min = 4, message = "ชื่อผู้ใช้ต้องยาวอย่างน้อย 4 ตัวอักษร") String adminUsername,
        @NotBlank(message = "กรุณากรอกรหัสผ่าน")
        @Size(min = 8, message = "รหัสผ่านต้องยาวอย่างน้อย 8 ตัวอักษร") String adminPassword,
        @NotBlank(message = "กรุณาเลือกแพ็กเกจ") String planCode
) { }
