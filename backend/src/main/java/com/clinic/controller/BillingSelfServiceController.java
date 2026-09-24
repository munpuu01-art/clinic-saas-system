package com.clinic.controller;

import com.clinic.dto.ChangePlanRequest;
import com.clinic.dto.ChangePlanResponse;
import com.clinic.dto.SubscriptionResponse;
import com.clinic.service.ClinicService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * "แพ็กเกจของฉัน" — ผู้ดูแลคลินิก (ADMIN) ดู/เปลี่ยน/ยกเลิกแพ็กเกจของคลินิกตัวเองได้เอง
 * ไม่ต้องรอ Super Admin ช่วย ทุก endpoint อ่านคลินิกจาก token ของผู้ใช้ ไม่รับ clinicId จากภายนอก
 */
@RestController
@RequestMapping("/api/billing")
@PreAuthorize("hasRole('ADMIN')")
public class BillingSelfServiceController {

    private final ClinicService clinicService;

    public BillingSelfServiceController(ClinicService clinicService) {
        this.clinicService = clinicService;
    }

    @GetMapping("/subscription")
    public SubscriptionResponse mySubscription() {
        return clinicService.getMySubscription();
    }

    @PostMapping("/subscription/change-plan")
    public ChangePlanResponse changePlan(@Valid @RequestBody ChangePlanRequest request) {
        return clinicService.changeMyPlan(request);
    }

    @PostMapping("/subscription/cancel")
    public SubscriptionResponse cancel() {
        return clinicService.cancelMySubscription();
    }

    @PostMapping("/subscription/reactivate")
    public SubscriptionResponse reactivate() {
        return clinicService.reactivateMySubscription();
    }
}
