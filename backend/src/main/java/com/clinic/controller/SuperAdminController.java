package com.clinic.controller;

import com.clinic.dto.ClinicResponse;
import com.clinic.dto.CreateClinicRequest;
import com.clinic.dto.PlanResponse;
import com.clinic.service.ClinicService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** แผงควบคุมของเจ้าของแพลตฟอร์ม (SUPER_ADMIN) — มองเห็นและจัดการได้ทุกคลินิก */
@RestController
@RequestMapping("/api/super-admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminController {

    private final ClinicService clinicService;

    public SuperAdminController(ClinicService clinicService) {
        this.clinicService = clinicService;
    }

    @GetMapping("/clinics")
    public List<ClinicResponse> clinics() { return clinicService.listAllClinics(); }

    /** เพิ่มคลินิกให้เองโดยตรง (ไม่ผ่านการชำระเงิน) เช่น ลูกค้าที่ตกลงราคากันนอกระบบ */
    @PostMapping("/clinics")
    public ResponseEntity<ClinicResponse> createClinic(@Valid @RequestBody CreateClinicRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clinicService.createClinicBySuperAdmin(request));
    }

    @PatchMapping("/clinics/{id}/status")
    public ClinicResponse setStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return clinicService.setClinicStatus(id, body.get("status"));
    }

    @GetMapping("/plans")
    public List<PlanResponse> plans() { return clinicService.listAllPlansForAdmin(); }
}
