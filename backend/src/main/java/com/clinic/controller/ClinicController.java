package com.clinic.controller;

import com.clinic.dto.ClinicRegisterRequest;
import com.clinic.dto.ClinicRegisterResponse;
import com.clinic.dto.PlanResponse;
import com.clinic.service.ClinicService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * จุดเข้าใช้งานสาธารณะสำหรับ "คลินิกที่จะซื้อ/สมัครใช้ระบบของเรา"
 * ไม่ต้องล็อกอินก่อนเรียก (ดู SecurityConfig: permitAll เฉพาะ 2 endpoint นี้)
 */
@RestController
@RequestMapping("/api")
public class ClinicController {

    private final ClinicService clinicService;

    public ClinicController(ClinicService clinicService) {
        this.clinicService = clinicService;
    }

    /** รายการแพ็กเกจที่เปิดขาย — แสดงบนหน้าสมัครก่อนล็อกอิน */
    @GetMapping("/plans")
    public List<PlanResponse> plans() { return clinicService.listPlans(); }

    /** สมัครคลินิกใหม่ด้วยตนเอง — แพ็กเกจฟรีได้ใช้ทันที แพ็กเกจเสียเงินจะได้ลิงก์ไปชำระผ่าน Stripe */
    @PostMapping("/clinics/register")
    public ResponseEntity<ClinicRegisterResponse> register(@Valid @RequestBody ClinicRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clinicService.registerClinic(request));
    }
}
